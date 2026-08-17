package com.andreaitemmaker.api;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * A config-defined custom block. The block is placed as a vanilla {@link #getBaseBlock()}
 * whose model is replaced by the generated resource pack, so the base block must be a
 * simple full block (see the safe list in the docs).
 */
public class CustomBlock extends CustomItem {

    private final Material baseBlock;
    private final boolean dropsItem;

    public CustomBlock(
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
            Material baseBlock,
            boolean dropsItem) {
        super(id, CustomItemType.BLOCK, material, displayName, lore, customModelData, maxStackSize,
                attributes, enchantments, unbreakable, glow, textureSpec, modelFile, mechanics);
        this.baseBlock = baseBlock;
        this.dropsItem = dropsItem;
    }

    /** The vanilla block used as a hitbox for this custom block. */
    public Material getBaseBlock() {
        return baseBlock;
    }

    /** Whether breaking the block drops the custom block item. */
    public boolean dropsItem() {
        return dropsItem;
    }
}
