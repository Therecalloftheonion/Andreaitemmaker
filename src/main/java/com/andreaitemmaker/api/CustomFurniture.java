package com.andreaitemmaker.api;

import org.bukkit.Material;
import org.bukkit.Sound;

import java.util.List;
import java.util.Map;

/**
 * A config-defined furniture piece. Placing the item spawns an invisible armor stand
 * that renders the item's model, so furniture works with any flat or 3D model.
 */
public class CustomFurniture extends CustomItem {

    private final boolean small;
    private final boolean consumable;
    private final boolean dropsItem;
    private final double offsetY;
    private final Sound placeSound;
    private final Sound breakSound;

    public CustomFurniture(
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
            boolean small,
            boolean consumable,
            boolean dropsItem,
            double offsetY,
            Sound placeSound,
            Sound breakSound) {
        super(id, CustomItemType.FURNITURE, material, displayName, lore, customModelData, maxStackSize,
                attributes, enchantments, unbreakable, glow, textureSpec, modelFile, mechanics);
        this.small = small;
        this.consumable = consumable;
        this.dropsItem = dropsItem;
        this.offsetY = offsetY;
        this.placeSound = placeSound;
        this.breakSound = breakSound;
    }

    /** Whether the armor stand is rendered small (better for lamps, plates, etc.). */
    public boolean isSmall() {
        return small;
    }

    /** Whether placing consumes one item from the player's hand. */
    public boolean isConsumable() {
        return consumable;
    }

    /** Whether breaking the piece drops the furniture item. */
    public boolean dropsItem() {
        return dropsItem;
    }

    /** Vertical offset applied when placing, in blocks. */
    public double getOffsetY() {
        return offsetY;
    }

    /** Sound played when placing (null = default wood sound). */
    public Sound getPlaceSound() {
        return placeSound;
    }

    /** Sound played when breaking/picking up (null = default wood sound). */
    public Sound getBreakSound() {
        return breakSound;
    }
}
