package com.andreaitemmaker.editor;

import com.andreaitemmaker.editor.menu.EditorMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Routes inventory and chat events to the editor.
 *
 * <p>Every handler returns immediately unless the event belongs to an editor inventory, and the
 * menu is found through the inventory's holder, so this listener never inspects content, touches
 * the filesystem, or affects normal gameplay.
 */
public final class EditorListener implements Listener {

    private final EditorManager manager;

    public EditorListener(EditorManager manager) {
        this.manager = manager;
    }

    /** Captures the next chat message while the editor is waiting for a value. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onChat(AsyncPlayerChatEvent event) {
        if (manager.handleChat(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof EditorMenu menu)) {
            return;
        }
        // Editors are read-only views: never let items be moved in or out of them.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != menu.getInventory()) {
            return;
        }
        manager.handleClick(player, menu, event.getSlot(), event.getClick());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof EditorMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof EditorMenu menu
                && event.getPlayer() instanceof Player player) {
            manager.handleClose(player, menu);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.handleQuit(event.getPlayer());
    }
}
