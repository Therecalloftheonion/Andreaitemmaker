package com.andreaitemmaker.listener;

import com.andreaitemmaker.AndreaitemmakerPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which online players currently have a custom item in an armor slot, so the periodic
 * armor-mechanics task only scans those players instead of every player on the server every
 * tick interval. The set is kept fresh by {@link ArmorListener} on inventory-affecting events
 * and reconciled periodically by the plugin's armor task.
 */
public final class ArmorTracker {

    private final AndreaitemmakerPlugin plugin;
    private final Set<UUID> tracked = ConcurrentHashMap.newKeySet();

    public ArmorTracker(AndreaitemmakerPlugin plugin) {
        this.plugin = plugin;
    }

    /** Re-scan a player's armor slots and add/remove them from the tracked set. */
    public void recompute(Player player) {
        boolean wearingCustom = false;
        for (ItemStack stack : player.getInventory().getArmorContents()) {
            if (plugin.getContentRegistry().getItemByStack(stack) != null) {
                wearingCustom = true;
                break;
            }
        }
        if (wearingCustom) {
            tracked.add(player.getUniqueId());
        } else {
            tracked.remove(player.getUniqueId());
        }
    }

    public void remove(UUID uuid) {
        tracked.remove(uuid);
    }

    /** Snapshot of tracked player ids (stale entries for offline players are fine; the tick loop drops them). */
    public Set<UUID> snapshot() {
        return Set.copyOf(tracked);
    }
}
