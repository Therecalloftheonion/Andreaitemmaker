package com.andreaitemmaker.api;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A config-defined custom item (also covers weapons, armor and food via {@link CustomItemType}).
 * Instances are immutable data holders; create in-game stacks with
 * {@link AndreaitemmakerAPI#createItemStack(String, int)}.
 */
public class CustomItem {

    protected final String id;
    protected final CustomItemType type;
    protected final Material material;
    protected final String displayName;
    protected final List<String> lore;
    protected final int customModelData;
    protected final int maxStackSize;
    protected final Map<String, Double> attributes;
    protected final Map<String, Integer> enchantments;
    protected final boolean unbreakable;
    protected final boolean glow;
    protected final String textureSpec;
    protected final String armorTextureSpec;
    protected final String modelFile;
    protected final Map<String, Map<String, Object>> mechanics;

    public CustomItem(
            String id,
            CustomItemType type,
            Material material,
            String displayName,
            List<String> lore,
            int customModelData,
            int maxStackSize,
            Map<String, Double> attributes,
            Map<String, Integer> enchantments,
            boolean unbreakable,
            boolean glow,
            String textureSpec,
            String armorTextureSpec,
            String modelFile,
            Map<String, Map<String, Object>> mechanics) {
        this.id = id;
        this.type = type;
        this.material = material;
        this.displayName = displayName;
        this.lore = lore == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(lore));
        this.customModelData = customModelData;
        this.maxStackSize = maxStackSize;
        this.attributes = attributes == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        this.enchantments = enchantments == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(enchantments));
        this.unbreakable = unbreakable;
        this.glow = glow;
        this.textureSpec = textureSpec;
        this.armorTextureSpec = armorTextureSpec;
        this.modelFile = modelFile;
        // Deep-freeze: the constructor must never retain a reference to a caller-supplied
        // mutable nested structure, or the whole class would silently stop being immutable.
        this.mechanics = mechanics == null || mechanics.isEmpty()
                ? Collections.emptyMap()
                : freezeMechanics(mechanics);
    }

    /** Recursively copy and wrap a nested mechanic config map into an immutable deep structure. */
    private static Map<String, Map<String, Object>> freezeMechanics(
            Map<String, Map<String, Object>> source) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : source.entrySet()) {
            out.put(e.getKey(), deepImmutableMap(e.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    private static Map<String, Object> deepImmutableMap(Map<String, Object> source) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : source.entrySet()) {
            out.put(e.getKey(), deepImmutable(e.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    private static Object deepImmutable(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                nested.put(String.valueOf(e.getKey()), deepImmutable(e.getValue()));
            }
            return Collections.unmodifiableMap(nested);
        }
        if (value instanceof List<?> list) {
            List<Object> immutable = new ArrayList<>(list.size());
            for (Object entry : list) {
                immutable.add(deepImmutable(entry));
            }
            return Collections.unmodifiableList(immutable);
        }
        return value;
    }

    /** Unique id of this content entry, e.g. "my_sword". */
    public String getId() {
        return id;
    }

    public CustomItemType getType() {
        return type;
    }

    /** The vanilla base material the item is built on. */
    public Material getMaterial() {
        return material;
    }

    /** Display name, with '&' color codes already translated. */
    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLore() {
        return lore;
    }

    /** Custom model data used on legacy (&lt; 1.21.2) servers. */
    public int getCustomModelData() {
        return customModelData;
    }

    public int getMaxStackSize() {
        return maxStackSize;
    }

    /** Attribute name (e.g. "attack_damage") to value. */
    public Map<String, Double> getAttributes() {
        return attributes;
    }

    /**
     * Optional dedicated texture for the <em>worn</em> armor layer ({@code armor-texture:} in
     * the config). Only used for ARMOR items; a proper 64x32 humanoid armor texture is ideal.
     * Returns null when the worn layer should be derived from {@link #getTextureSpec()} or
     * the model's own texture.
     */
    public String getArmorTextureSpec() {
        return armorTextureSpec;
    }

    /** Enchantment key (e.g. "sharpness") to level. */
    public Map<String, Integer> getEnchantments() {
        return enchantments;
    }

    public boolean isUnbreakable() {
        return unbreakable;
    }

    public boolean hasGlow() {
        return glow;
    }

    /**
     * Raw texture spec from config: a hex color ("#4f7cff"), a map-style pattern spec
     * ("gradient:#4f7cff:#1b2a6b") or a path to a PNG inside the plugin's assets folder.
     */
    public String getTextureSpec() {
        return textureSpec;
    }

    /** Optional path to a model JSON inside the plugin's assets folder (null = generated). */
    public String getModelFile() {
        return modelFile;
    }

    /** Mechanic id to its config map, as declared in the item's YAML. */
    public Map<String, Map<String, Object>> getMechanics() {
        return mechanics;
    }

    /** Whether a mechanic with the given id is configured on this item. */
    public boolean hasMechanic(String id) {
        return mechanics.containsKey(id);
    }

    @Override
    public String toString() {
        return "CustomItem{" + type + ":" + id + "}";
    }
}
