package com.andreaitemmaker.api.event;

import com.andreaitemmaker.api.CustomBlock;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.Material;

/** Fired when a player places a custom block (before the block is set). */
public class CustomBlockPlaceEvent extends CustomItemEvent {

    private final Block block;
    private final Material baseBlock;

    public CustomBlockPlaceEvent(Player player, CustomBlock item, Block block, Material baseBlock) {
        super(player, item);
        this.block = block;
        this.baseBlock = baseBlock;
    }

    /** The block that will be placed. */
    public Block getBlock() {
        return block;
    }

    /** The vanilla material used as the placed hitbox. */
    public Material getBaseBlock() {
        return baseBlock;
    }

    @Override
    public CustomBlock getItem() {
        return (CustomBlock) super.getItem();
    }
}
