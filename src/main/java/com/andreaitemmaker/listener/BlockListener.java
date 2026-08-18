package com.andreaitemmaker.listener;

import com.andreaitemmaker.AndreaitemmakerPlugin;
import com.andreaitemmaker.api.CustomBlock;
import com.andreaitemmaker.api.event.CustomBlockBreakEvent;
import com.andreaitemmaker.util.BlockData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles breaking, placement protection, pistons, fire and explosions for custom blocks.
 *
 * <p>Custom blocks are identified by their persistent chunk-PDC tag ({@link BlockData}),
 * never by their {@link Material}: a normal vanilla STONE or WOOL block without the tag is
 * completely unaffected and behaves exactly like vanilla.
 */
public final class BlockListener implements Listener {

    private final AndreaitemmakerPlugin plugin;

    public BlockListener(AndreaitemmakerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String id = BlockData.get(block);
        if (id == null) {
            return; // normal block, nothing to do
        }
        CustomBlock custom = plugin.getContentRegistry().getBlock(id);
        if (custom == null || block.getType() != custom.getBaseBlock()) {
            // The content was removed from config, or the block was replaced externally
            // (e.g. by a world editor) leaving a stale tag. Either way the block is no
            // longer a real custom block: clear the tag and let vanilla breaking proceed.
            BlockData.remove(block);
            return;
        }
        event.setCancelled(true);
        CustomBlockBreakEvent breakEvent = new CustomBlockBreakEvent(event.getPlayer(), custom, block);
        Bukkit.getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) {
            return;
        }
        block.setType(Material.AIR, false);
        BlockData.remove(block);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_STONE_BREAK, 1f, 1f);
        if (custom.dropsItem() && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5),
                    plugin.getItemFactory().build(custom, 1));
        }
    }

    /** A vanilla block cannot be placed over a custom block. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        String id = BlockData.get(placed);
        if (id == null) {
            return;
        }
        CustomBlock custom = plugin.getContentRegistry().getBlock(id);
        if (custom != null && placed.getType() == custom.getBaseBlock()) {
            event.setCancelled(true); // replacing an existing custom block
        } else {
            // Stale tag on a coordinate whose custom block was replaced externally: allow
            // the placement and drop the dead tag.
            BlockData.remove(placed);
        }
    }

    /** Custom blocks cannot be destroyed by fire (e.g. wool bases). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (BlockData.get(event.getBlock()) != null) {
            event.setCancelled(true);
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
            if (BlockData.get(b) != null) {
                toRemove.add(b);
            }
        }
        event.blockList().removeAll(toRemove);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        List<Block> toRemove = new ArrayList<>();
        for (Block b : event.blockList()) {
            if (BlockData.get(b) != null) {
                toRemove.add(b);
            }
        }
        event.blockList().removeAll(toRemove);
    }

    private boolean containsCustom(List<Block> blocks) {
        for (Block b : blocks) {
            if (BlockData.get(b) != null) {
                return true;
            }
        }
        return false;
    }
}
