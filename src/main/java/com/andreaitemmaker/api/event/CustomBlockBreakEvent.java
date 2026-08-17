package com.andreaitemmaker.api.event;

import com.andreaitemmaker.api.CustomBlock;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** Fired when a custom block is broken (before it is removed). */
public class CustomBlockBreakEvent extends CustomItemEvent {

    private final Block block;

    public CustomBlockBreakEvent(Player player, CustomBlock item, Block block) {
        super(player, item);
        this.block = block;
    }

    public Block getBlock() {
        return block;
    }

    @Override
    public CustomBlock getItem() {
        return (CustomBlock) super.getItem();
    }
}
