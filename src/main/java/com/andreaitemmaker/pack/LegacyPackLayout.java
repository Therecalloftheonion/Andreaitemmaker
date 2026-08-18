package com.andreaitemmaker.pack;

import com.andreaitemmaker.api.CustomItem;
import com.andreaitemmaker.util.Json;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Legacy layout (1.20.5 - 1.21.1): items carry a CustomModelData number and the pack
 * patches {@code assets/minecraft/models/item/<base>.json} with predicates that point at
 * the custom models.
 */
final class LegacyPackLayout implements PackLayout {

    private static final java.util.Set<String> HANDHELD_BASES = java.util.Set.of(
            "netherite_sword", "diamond_sword", "iron_sword", "golden_sword", "stone_sword", "wooden_sword",
            "netherite_axe", "diamond_axe", "iron_axe", "golden_axe", "stone_axe", "wooden_axe",
            "netherite_pickaxe", "diamond_pickaxe", "iron_pickaxe", "golden_pickaxe", "stone_pickaxe", "wooden_pickaxe",
            "netherite_shovel", "diamond_shovel", "iron_shovel", "golden_shovel", "stone_shovel", "wooden_shovel",
            "netherite_hoe", "diamond_hoe", "iron_hoe", "golden_hoe", "stone_hoe", "wooden_hoe");

    private static final java.util.Set<String> GENERATED_BASES = java.util.Set.of(
            "stick", "paper", "diamond", "emerald", "iron_ingot", "gold_ingot", "netherite_ingot", "copper_ingot",
            "iron_nugget", "gold_nugget", "coal", "charcoal", "flint", "feather", "string", "leather", "bone",
            "blaze_rod", "ender_pearl", "snowball", "egg", "arrow", "apple", "golden_apple", "bread",
            "cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_cod", "cooked_salmon", "carrot", "potato",
            "cookie", "melon_slice", "sweet_berries", "glow_berries", "pumpkin_pie", "cake",
            "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots",
            "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots",
            "golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots",
            "netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots",
            "leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots",
            "chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots", "turtle_helmet",
            "quartz", "redstone", "lapis_lazuli", "amethyst_shard", "echo_shard", "nether_star",
            "prismarine_shard", "prismarine_crystals", "phantom_membrane", "rabbit_foot", "ghast_tear",
            "blaze_powder", "gunpowder", "sugar", "slime_ball", "clay_ball", "brick", "nether_brick");

    private static final Map<String, String> BASE_PARENT = new HashMap<>();

    static {
        for (String name : HANDHELD_BASES) {
            BASE_PARENT.put(name, "minecraft:item/handheld");
        }
        for (String name : GENERATED_BASES) {
            BASE_PARENT.put(name, "minecraft:item/generated");
        }
    }

    LegacyPackLayout() {
    }

    @Override
    public boolean isModern() {
        return false;
    }

    @Override
    public void writeItemDefinition(Map<String, byte[]> entries, PackGenerator.Context ctx,
                                    CustomItem item, Map<Material, List<CustomItem>> legacyGroups) {
        legacyGroups.computeIfAbsent(item.getMaterial(), k -> new ArrayList<>()).add(item);
    }

    @Override
    public void writeArmorAssets(Map<String, byte[]> entries, PackGenerator.Context ctx,
                                 CustomItem item, byte[] layer1, byte[] layer2) {
        // Legacy servers use the vanilla armor layer system; the layer PNGs are already
        // written by the generator and need no extra definition.
    }

    @Override
    public void writeLegacyOverrides(Map<String, byte[]> entries, PackGenerator.Context ctx,
                                     Map<Material, List<CustomItem>> groups) {
        for (Map.Entry<Material, List<CustomItem>> e : groups.entrySet()) {
            String baseKey = e.getKey().name().toLowerCase(Locale.ROOT);
            String parent = BASE_PARENT.get(baseKey);
            if (parent == null) {
                ctx.logger().warning("Cannot wire custom items on material '" + baseKey
                        + "' for this server version (no safe base model). Use a material from the documented list.");
                continue;
            }
            List<Object> overrides = new ArrayList<>();
            for (CustomItem item : e.getValue()) {
                overrides.add(Map.of(
                        "predicate", Map.of("custom_model_data", item.getCustomModelData()),
                        "model", ctx.namespace() + ":item/" + item.getId()));
            }
            String json = Json.obj(
                    "parent", parent,
                    "textures", Map.of("layer0", "minecraft:item/" + baseKey),
                    "overrides", overrides);
            PackGenerator.putString(entries, "assets/minecraft/models/item/" + baseKey + ".json", json);
        }
    }
}
