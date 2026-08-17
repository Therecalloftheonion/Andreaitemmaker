package com.andreaitemmaker.listener;

import com.andreaitemmaker.AndreaitemmakerPlugin;
import com.andreaitemmaker.api.CustomBlock;
import com.andreaitemmaker.api.event.CustomBlockBreakEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.ArrayList;
import java.util.List;

/** Handles breaking, piston movement and explosions for custom blocks. */
public final class BlockListener implements Listener {

    private final AndreaitemmakerPlugin plugin;

    public BlockListener(AndreaitemmakerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        CustomBlock block = plugin.getContentRegistry().getBlockByBase(event.getBlock().getType());
        if (block == null) {
            return;
        }
        event.setCancelled(true);
        CustomBlockBreakEvent breakEvent = new CustomBlockBreakEvent(event.getPlayer(), block, event.getBlock());
        Bukkit.getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) {
            return;
        }
        Block b = event.getBlock();
        b.setType(Material.AIR, false);
        b.getWorld().playSound(b.getLocation(), Sound.BLOCK_STONE_BREAK, 1f, 1f);
        if (block.dropsItem() && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5),
                    plugin.getItemFactory().build(block, 1));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (containsCustom(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (containsCustom(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        List<Block> toRemove = new ArrayList<>();
        for (Block b : event.blockList()) {
            if (plugin.getContentRegistry().getBlockByBase(b.getType()) != null) {
                toRemove.add(b);
            }
        }
        event.blockList().removeAll(toRemove);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        List<Block> toRemove = new ArrayList<>();
        for (Block b : event.blockList()) {
            if (plugin.getContentRegistry().getBlockByBase(b.getType()) != null) {
                toRemove.add(b);
            }
        }
        event.blockList().removeAll(toRemove);
    }

    private boolean containsCustom(List<Block> blocks) {
        for (Block b : blocks) {
            if (plugin.getContentRegistry().getBlockByBase(b.getType()) != null) {
                return true;
            }
        }
        return false;
    }
}
