package com.andreaitemmaker.content;

import com.andreaitemmaker.AndreaitemmakerPlugin;
import com.andreaitemmaker.api.CustomItem;
import com.andreaitemmaker.util.Chat;
import com.andreaitemmaker.util.Keys;
import com.andreaitemmaker.util.ServerVersion;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Turns {@link CustomItem} definitions into real {@link ItemStack}s.
 * Version-specific calls (item_model component, equippable component, max stack size)
 * are done reflectively so the plugin keeps working on every supported server version.
 */
public final class ItemFactory {

    private static final Set<String> KNOWN_ATTRIBUTES = new LinkedHashSet<>(java.util.List.of(
            "attack_damage", "attack_speed", "armor", "armor_toughness", "knockback_resistance",
            "max_health", "movement_speed", "luck", "attack_knockback", "follow_range",
            "flying_speed", "horse_jump_strength", "step_height", "scale", "jump_strength",
            "safe_fall_distance", "gravity", "burning_time", "explosion_knockback_resistance",
            "mined_block_speed", "player_block_break_speed", "player_mining_efficiency",
            "player_sneaking_speed", "player_submerged_mining_speed", "player_sweeping_damage",
            "zephyr_velocity", "fall_damage_multiplier"));

    private final AndreaitemmakerPlugin plugin;

    public ItemFactory(AndreaitemmakerPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isKnownAttribute(String name) {
        return KNOWN_ATTRIBUTES.contains(name.trim().toLowerCase());
    }

    /** Build the ItemStack for a custom item definition. */
    public ItemStack build(CustomItem item, int amount) {
        ItemStack stack = new ItemStack(item.getMaterial(), Math.max(1, Math.min(amount, 64)));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(Chat.color(item.getDisplayName()));
        if (!item.getLore().isEmpty()) {
            meta.setLore(item.getLore().stream().map(Chat::color).toList());
        }
        meta.getPersistentDataContainer().set(Keys.ITEM_ID, PersistentDataType.STRING, item.getId());
        meta.getPersistentDataContainer().set(Keys.ITEM_TYPE, PersistentDataType.STRING, item.getType().name());

        ServerVersion.Version version = plugin.getServerVersion();
        if (version.isAtLeast(1, 21, 2)) {
            setItemModel(meta, new NamespacedKey(plugin.getConfigValues().namespace, item.getId()));
            setMaxStackSize(meta, item.getMaxStackSize());
        } else if (item.getCustomModelData() > 0) {
            meta.setCustomModelData(item.getCustomModelData());
        }

        applyAttributes(meta, item.getAttributes());
        applyEnchantments(meta, item.getEnchantments());

        if (item.isUnbreakable()) {
            meta.setUnbreakable(true);
        }
        if (item.hasGlow()) {
            setGlintOverride(meta);
        }
        if (item.getType().isArmor() && version.isAtLeast(1, 21, 4)) {
            wireEquippable(meta, item, slotFor(item.getMaterial()));
        }

        stack.setItemMeta(meta);
        return stack;
    }

    private void applyAttributes(ItemMeta meta, Map<String, Double> attributes) {
        for (Map.Entry<String, Double> e : attributes.entrySet()) {
            Attribute attribute = parseAttribute(e.getKey());
            if (attribute == null) {
                plugin.getLogger().warning("Unknown attribute '" + e.getKey() + "' on item, ignored");
                continue;
            }
            meta.addAttributeModifier(attribute, modifier(e.getKey(), e.getValue()));
        }
    }

    /**
     * Build an AttributeModifier. Uses the modern (NamespacedKey + EquipmentSlotGroup) form
     * when the server supports it, falling back to the deprecated UUID form on older versions.
     */
    private static AttributeModifier modifier(String attributeName, double value) {
        try {
            Class<?> slotGroup = Class.forName("org.bukkit.inventory.EquipmentSlotGroup");
            Object any = slotGroup.getField("ANY").get(null);
            var constructor = AttributeModifier.class.getConstructor(
                    NamespacedKey.class, double.class, AttributeModifier.Operation.class, slotGroup);
            return constructor.newInstance(
                    new NamespacedKey("andreaitemmaker", "attr_" + attributeName.replace('_', '-')),
                    value, AttributeModifier.Operation.ADD_NUMBER, any);
        } catch (ReflectiveOperationException e) {
            return new AttributeModifier(java.util.UUID.randomUUID(), "andreaitemmaker", value,
                    AttributeModifier.Operation.ADD_NUMBER);
        }
    }

    private void applyEnchantments(ItemMeta meta, Map<String, Integer> enchantments) {
        for (Map.Entry<String, Integer> e : enchantments.entrySet()) {
            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(e.getKey()));
            if (enchantment == null) {
                plugin.getLogger().warning("Unknown enchantment '" + e.getKey() + "' on item, ignored");
                continue;
            }
            meta.addEnchant(enchantment, e.getValue(), true);
        }
    }

    /** Parse an attribute name from config (e.g. "attack_damage", "generic.attack_damage"). */
    public static Attribute parseAttribute(String configName) {
        String n = configName.trim().toLowerCase();
        if (n.startsWith("minecraft:")) {
            n = n.substring("minecraft:".length());
        }
        String snake = n.replace("generic.", "").replace('.', '_').toUpperCase();
        for (String candidate : new String[]{"GENERIC_" + snake, snake}) {
            try {
                return Attribute.valueOf(candidate);
            } catch (IllegalArgumentException ignored) {
                // try next candidate
            }
        }
        return null;
    }

    public static EquipmentSlot slotFor(org.bukkit.Material material) {
        String name = material.name();
        if (name.contains("HELMET")) {
            return EquipmentSlot.HEAD;
        }
        if (name.contains("CHESTPLATE")) {
            return EquipmentSlot.CHEST;
        }
        if (name.contains("LEGGINGS")) {
            return EquipmentSlot.LEGS;
        }
        if (name.contains("BOOTS")) {
            return EquipmentSlot.FEET;
        }
        return EquipmentSlot.HEAD;
    }

    // ---- version-specific wiring (reflection) ----

    private static Method setItemModelMethod;
    private static Method setMaxStackSizeMethod;
    private static Method setGlintMethod;

    public static boolean setItemModel(ItemMeta meta, NamespacedKey key) {
        try {
            if (setItemModelMethod == null) {
                setItemModelMethod = ItemMeta.class.getMethod("setItemModel", NamespacedKey.class);
            }
            setItemModelMethod.invoke(meta, key);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static void setMaxStackSize(ItemMeta meta, int max) {
        if (max <= 0 || max >= 64) {
            return;
        }
        try {
            if (setMaxStackSizeMethod == null) {
                setMaxStackSizeMethod = ItemMeta.class.getMethod("setMaxStackSize", int.class);
            }
            setMaxStackSizeMethod.invoke(meta, max);
        } catch (ReflectiveOperationException ignored) {
            // Feature not present on this server version; the vanilla stack size applies.
        }
    }

    private static void setGlintOverride(ItemMeta meta) {
        try {
            if (setGlintMethod == null) {
                setGlintMethod = ItemMeta.class.getMethod("setEnchantmentGlintOverride", Boolean.class);
            }
            setGlintMethod.invoke(meta, true);
        } catch (ReflectiveOperationException ignored) {
            // Feature not present on this server version; the vanilla glint behavior applies.
        }
    }

    /**
     * Wire the equippable component so custom armor renders from its equipment asset when
     * worn (1.21.2+). Paper's {@code setModel(NamespacedKey)} is the asset reference: on
     * 1.21.4+ it maps to the component's {@code asset_id} field (there is no setAssetId
     * method on current Paper), so it is the one call that must never be skipped. Optional
     * fields are guarded individually so a missing method on an older Paper build cannot
     * silently discard the whole wiring (previously an always-throwing setAssetId() call
     * did exactly that, leaving vanilla armor on the player).
     */
    private void wireEquippable(ItemMeta meta, CustomItem item, EquipmentSlot slot) {
        try {
            Method getEquippable = ItemMeta.class.getMethod("getEquippable");
            Object component = getEquippable.invoke(meta);
            if (component == null) {
                return;
            }
            Class<?> type = component.getClass();
            invoke(type, component, "setModel", NamespacedKey.class,
                    new NamespacedKey(plugin.getConfigValues().namespace, item.getId()));
            invoke(type, component, "setSlot", EquipmentSlot.class, slot);
            tryInvoke(type, component, "setDamageOnHurt", boolean.class, false);
            tryInvoke(type, component, "setDispensable", boolean.class, true);
            tryInvoke(type, component, "setSwappable", boolean.class, true);
            tryInvoke(type, component, "setEquipSound", Sound.class, Sound.ITEM_ARMOR_EQUIP_IRON);
            Method setEquippable = ItemMeta.class.getMethod("setEquippable", getEquippable.getReturnType());
            setEquippable.invoke(meta, component);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.FINE,
                    "Could not wire equippable component for " + item.getId() + " (non-Paper server?)", e);
        }
    }

    /** Invoke an optional equippable setter; a missing method is fine and is ignored. */
    private static void tryInvoke(Class<?> type, Object target, String method, Class<?> param, Object arg) {
        try {
            invoke(type, target, method, param, arg);
        } catch (ReflectiveOperationException ignored) {
            // Optional field not present on this Paper version; the essential model/slot wiring already succeeded.
        }
    }

    private static void invoke(Class<?> type, Object target, String method, Class<?> param, Object arg)
            throws ReflectiveOperationException {
        Method m = type.getMethod(method, param);
        m.setAccessible(true);
        m.invoke(target, arg);
    }
}
