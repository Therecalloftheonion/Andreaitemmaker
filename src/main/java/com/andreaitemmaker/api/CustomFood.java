package com.andreaitemmaker.api;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/** A config-defined custom food. Right-clicking eats it (consume + hunger/saturation restore). */
public class CustomFood extends CustomItem {

    private final int hunger;
    private final float saturation;
    private final int cooldown;

    public CustomFood(
            String id,
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
            String modelFile,
            Map<String, Map<String, Object>> mechanics,
            int hunger,
            float saturation,
            int cooldown) {
        super(id, CustomItemType.FOOD, material, displayName, lore, customModelData, maxStackSize,
                attributes, enchantments, unbreakable, glow, textureSpec, modelFile, mechanics);
        this.hunger = hunger;
        this.saturation = saturation;
        this.cooldown = cooldown;
    }

    /** Hunger points restored per bite. */
    public int getHunger() {
        return hunger;
    }

    /** Saturation restored per bite. */
    public float getSaturation() {
        return saturation;
    }

    /** Seconds before the food can be eaten again. */
    public int getCooldown() {
        return cooldown;
    }
}
