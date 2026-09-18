package com.andreaitemmaker.editor.menu;

import com.andreaitemmaker.config.PluginConfig;
import com.andreaitemmaker.editor.EditorGui;
import com.andreaitemmaker.editor.EditorManager;
import com.andreaitemmaker.editor.EditorSession;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Read-only view of the effective settings plus the pack controls.
 *
 * <p>{@code config.yml} is deliberately not edited from the GUI: the file is comment-heavy and
 * hand-tuned, and the plugin already migrates it automatically when a new version adds options.
 * The screen instead shows the live values, so there is nothing to guess about.
 */
public final class SettingsMenu extends EditorMenu {

    public SettingsMenu(EditorManager manager, EditorSession session, EditorMenu parent) {
        super(manager, session, parent);
    }

    @Override
    protected String title() {
        return "&8| &bSettings & diagnostics";
    }

    @Override
    protected void render(Inventory inventory) {
        background(inventory);
        PluginConfig config = manager.plugin().getConfigValues();
        var pack = manager.plugin().getPackManager();

        set(inventory, 4, EditorGui.icon(Material.HOPPER, "&bEffective settings",
                List.of("&7Config file: &fplugins/Andreaitemmaker/config.yml",
                        "&7Namespace: &f" + config.namespace,
                        "&7Texture size: &f" + config.pack.textureSize,
                        "&7Explosion protected: &f" + config.explosionProtected,
                        "&7Custom model data start: &f" + config.customModelDataStart,
                        "",
                        "&7These are read from config.yml; the plugin",
                        "&7migrates the file automatically on update,",
                        "&7so they are edited by hand only.")));

        set(inventory, 19, EditorGui.icon(config.pack.sendOnJoin ? Material.LIME_DYE : Material.GRAY_DYE,
                "&bSend on join: &f" + config.pack.sendOnJoin,
                "&7pack.send-on-join"));
        set(inventory, 20, EditorGui.icon(config.pack.required ? Material.LIME_DYE : Material.GRAY_DYE,
                "&bRequired: &f" + config.pack.required,
                "&7pack.required"));
        set(inventory, 21, EditorGui.icon(config.pack.resendOnReload ? Material.LIME_DYE : Material.GRAY_DYE,
                "&bResend on reload: &f" + config.pack.resendOnReload,
                "&7pack.resend-on-reload"));
        set(inventory, 22, EditorGui.icon(config.pack.serveEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
                "&bBuilt-in HTTP server: &f" + config.pack.serveEnabled,
                List.of("&7pack.serve.enabled", "&7Port: &f" + config.pack.servePort)));

        set(inventory, 30, EditorGui.icon(pack.isGenerated() ? Material.LIME_DYE : Material.RED_DYE,
                "&bResource pack",
                List.of("&7Generated: &f" + pack.isGenerated(),
                        "&7Format: &f" + pack.getFormat(),
                        "&7SHA-1: &f" + (pack.getSha1().isEmpty() ? "-" : pack.getSha1()),
                        "&7Size: &f" + pack.getLastGenerationBytes() + " bytes",
                        "&7Generation: &f" + pack.getLastGenerationMillis() + "ms",
                        "&7HTTP server: &f" + (pack.isServing() ? "active" : "inactive"),
                        "&7URL: &f" + (pack.getUrl().isEmpty() ? "(none)" : pack.getUrl()),
                        "&7Deliveries tracked: &f" + pack.trackedDeliveries())));
        set(inventory, 32, EditorGui.icon(Material.COMPASS, "&bServer",
                List.of("&7Plugin: &f" + manager.plugin().getDescription().getVersion(),
                        "&7Server: &f" + Bukkit.getBukkitVersion(),
                        "&7Java: &f" + System.getProperty("java.version"),
                        "&7WorldGuard: &f" + (manager.plugin().getProtectionService().isWorldGuardActive()
                                ? "active" : "not detected"),
                        "&7Content entries: &f" + manager.plugin().getContentRegistry().getAll().size(),
                        "&7Reload running: &f" + manager.plugin().isReloading())));

        set(inventory, 38, EditorGui.icon(Material.PISTON, "&eRegenerate the pack",
                "&7Rebuilds the ZIP from the current content."));
        set(inventory, 40, EditorGui.icon(Material.ENDER_PEARL, "&eSend the pack to everyone",
                "&7Prompts every online player."));
        set(inventory, 42, EditorGui.icon(Material.HOPPER, "&eReload content",
                "&7Reads config + content again and",
                        "&7regenerates the pack (background)."));

        navBar(inventory);
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        switch (slot) {
            case 38 -> {
                boolean ok = manager.plugin().getPackManager().generate();
                manager.message(player, ok ? "&aPack regeneration started."
                        : "&cCould not start pack generation, check the console.");
            }
            case 40 -> {
                manager.plugin().getPackManager().sendToAll();
                manager.message(player, "&aPack sent to all online players.");
            }
            case 42 -> {
                manager.plugin().reloadAll();
                manager.message(player, "&eReload requested; it runs in the background.");
            }
            case EditorGui.SLOT_BACK -> back(player);
            case EditorGui.SLOT_CLOSE -> player.closeInventory();
            default -> {
                // ignore
            }
        }
    }
}
