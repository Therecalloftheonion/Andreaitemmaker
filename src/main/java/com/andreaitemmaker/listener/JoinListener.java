package com.andreaitemmaker.listener;

import com.andreaitemmaker.AndreaitemmakerPlugin;
import com.andreaitemmaker.util.Chat;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/** Sends the generated resource pack to joining players and reacts to their response. */
public final class JoinListener implements Listener {

    private final AndreaitemmakerPlugin plugin;

    public JoinListener(AndreaitemmakerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfigValues().pack.sendOnJoin) {
            return;
        }
        // Small delay so the join packet burst has settled. Skip players who already got
        // this pack (e.g. from a generation that finished while they were joining).
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            var manager = plugin.getPackManager();
            if (!manager.wasSentTo(event.getPlayer())) {
                manager.sendTo(event.getPlayer());
            }
        }, 40L);
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        // Record the outcome so re-joining after a failed/declined attempt gets re-sent and
        // players who already accepted are not prompted again on every join.
        plugin.getPackManager().recordPackStatus(event.getPlayer(), event.getStatus().name());
        switch (event.getStatus()) {
            case DECLINED -> {
                if (plugin.getConfigValues().pack.required) {
                    event.getPlayer().sendMessage(Chat.color(
                            "&cThis server requires the custom content pack. Run &f/aitem pack&c to try again."));
                }
            }
            case FAILED_DOWNLOAD -> event.getPlayer().sendMessage(Chat.color(
                    "&cThe resource pack failed to download. Run &f/aitem pack&c to retry."));
            case INVALID_URL -> plugin.getLogger().warning(
                    "Player " + event.getPlayer().getName() + " reported an invalid pack URL: "
                            + plugin.getPackManager().getUrl());
            default -> {
                // ACCEPTED / SUCCESSFULLY_LOADED / newer statuses need no action
            }
        }
    }
}
