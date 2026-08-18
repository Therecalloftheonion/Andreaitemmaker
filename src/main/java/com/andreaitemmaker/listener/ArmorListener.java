package com.andreaitemmaker.listener;

import com.andreaitemmaker.AndreaitemmakerPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Keeps {@link ArmorTracker} accurate by recomputing a player's tracked state whenever an
 * event can change what is in their armor slots. Recomputes are cheap (a few PDC reads) and
 * only happen on events, not every tick. The plugin's armor task additionally runs a slow
 * full reconciliation as a safety net for direct inventory edits that fire no events.
 *
 * <p>{@code PlayerArmorChangeEvent} is Paper-only and not available on the compile classpath,
 * so armor equips are caught via inventory clicks, right-click equip interactions (armor
 * material in hand) and the reconciliation task instead.
 */
public final class ArmorListener implements Listener {

    private final AndreaitemmakerPlugin plugin;

    public ArmorListener(AndreaitemmakerPlugin plugin) {
        this.plugin = plugin;
    }

    private ArmorTracker tracker() {
        return plugin.getArmorTracker();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        tracker().recompute(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tracker().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            tracker().recompute(player);
        }
    }

    /** Right-click equipping an armor item in hand never fires an inventory event. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack stack = event.getItem();
        if (stack != null && !stack.getType().isAir()
                && stack.getType().getEquipmentSlot() != EquipmentSlot.HAND) {
            tracker().recompute(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        tracker().recompute(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            tracker().recompute(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        tracker().recompute(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        tracker().recompute(event.getEntity());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        tracker().recompute(event.getPlayer());
    }
}
