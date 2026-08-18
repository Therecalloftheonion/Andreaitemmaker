package com.andreaitemmaker;

import com.andreaitemmaker.api.AndreaitemmakerAPI;
import com.andreaitemmaker.api.CustomItem;
import com.andreaitemmaker.api.ItemMechanic;
import com.andreaitemmaker.api.MechanicContext;
import com.andreaitemmaker.command.ItemMakerCommand;
import com.andreaitemmaker.config.ConfigMigrator;
import com.andreaitemmaker.config.ContentLoader;
import com.andreaitemmaker.config.PluginConfig;
import com.andreaitemmaker.content.ContentRegistry;
import com.andreaitemmaker.content.ItemFactory;
import com.andreaitemmaker.listener.ArmorListener;
import com.andreaitemmaker.listener.ArmorTracker;
import com.andreaitemmaker.listener.BlockListener;
import com.andreaitemmaker.listener.FurnitureListener;
import com.andreaitemmaker.listener.JoinListener;
import com.andreaitemmaker.listener.UseListener;
import com.andreaitemmaker.mechanics.MechanicRegistryImpl;
import com.andreaitemmaker.pack.ResourcePackManagerImpl;
import com.andreaitemmaker.util.ServerVersion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Andreaitemmaker: config-driven custom content with automatic resource pack generation. */
public final class AndreaitemmakerPlugin extends JavaPlugin {

    private PluginConfig configValues;
    private volatile ContentRegistry contentRegistry;
    private ItemFactory itemFactory;
    private MechanicRegistryImpl mechanicRegistry;
    private ResourcePackManagerImpl packManager;
    private ServerVersion.Version serverVersion;
    private ServerVersion.PackTarget packTarget;
    private ArmorTracker armorTracker;
    private int armorTaskId = -1;
    private int reconcileTaskId = -1;

    @Override
    public void onEnable() {
        serverVersion = ServerVersion.parse(Bukkit.getBukkitVersion());
        if (serverVersion == null) {
            getLogger().severe("Could not parse server version '" + Bukkit.getBukkitVersion()
                    + "'. Andreaitemmaker requires a 1.20.5+ server.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!serverVersion.isAtLeast(1, 20, 5)) {
            getLogger().severe("Andreaitemmaker requires a 1.20.5+ server (running "
                    + Bukkit.getBukkitVersion() + "). Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Detected server version " + serverVersion);

        configValues = PluginConfig.from(new ConfigMigrator(this).loadMainConfig());
        copyExamplesIfMissing();
        createAssetFolders();
        contentRegistry = new ContentRegistry();
        itemFactory = new ItemFactory(this);
        mechanicRegistry = new MechanicRegistryImpl();
        packManager = new ResourcePackManagerImpl(this);
        armorTracker = new ArmorTracker(this);
        packTarget = computePackTarget(configValues);

        ContentRegistry loaded = buildRegistry();
        if (loaded == null) {
            getLogger().severe("Could not load any content; disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        contentRegistry = loaded;

        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new UseListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new FurnitureListener(this), this);
        getServer().getPluginManager().registerEvents(new ArmorListener(this), this);

        ItemMakerCommand command = new ItemMakerCommand(this);
        getCommand("andreaitemmaker").setExecutor(command);
        getCommand("andreaitemmaker").setTabCompleter(command);

        startArmorTask();
        packManager.generate();
        AndreaitemmakerAPI.init(this);

        getLogger().info("Enabled with " + contentRegistry.getAll().size() + " content entries, "
                + "resource pack generation " + (packManager.isGenerated() ? "ready" : "started in the background"));
    }

    @Override
    public void onDisable() {
        AndreaitemmakerAPI.close();
        if (packManager != null) {
            packManager.shutdown();
        }
        cancelTasks();
    }

    /**
     * Full reload: config, content, pack. Transactional: everything is loaded and validated
     * into fresh objects first, and only swapped in once it succeeded. If anything fails, the
     * previous working config/registry stays active and the previous pack keeps being served.
     */
    public void reloadAll() {
        PluginConfig newConfig = PluginConfig.from(new ConfigMigrator(this).loadMainConfig());
        ServerVersion.PackTarget newTarget = computePackTarget(newConfig);
        createAssetFolders();
        ContentRegistry newRegistry = buildRegistry();
        if (newRegistry == null) {
            getLogger().severe("Reload aborted: content could not be loaded; keeping the previous state.");
            return;
        }
        // Everything loaded fine: swap atomically (reads see either the old or the new state).
        this.configValues = newConfig;
        this.packTarget = newTarget;
        this.contentRegistry = newRegistry;

        cancelTasks();
        startArmorTask();
        boolean ok = packManager.generate();
        getLogger().info("Reload finished with " + newRegistry.getAll().size() + " content entries; "
                + "pack generation " + (ok ? "started" : "FAILED to start"));
    }

    /**
     * Load content into a fresh registry without touching the current one.
     *
     * @return the new registry, or null when loading failed catastrophically
     */
    private ContentRegistry buildRegistry() {
        try {
            ContentLoader.LoadResult result = new ContentLoader(this).load();
            ContentRegistry registry = ContentRegistry.build(result.items);
            // Cross-check mechanic references so typos surface at load time.
            for (CustomItem item : registry.getAll()) {
                for (String mechId : item.getMechanics().keySet()) {
                    if (mechanicRegistry.get(mechId) == null) {
                        getLogger().warning("Item '" + item.getId() + "' references unknown mechanic '"
                                + mechId + "' (registered: " + mechanicRegistry.getAll().stream()
                                .map(ItemMechanic::getId).sorted().toList() + ")");
                    }
                }
            }
            getLogger().info("Loaded " + result.loaded + " content entries"
                    + (result.errors.isEmpty() ? "" : ", " + result.errors.size()
                    + " entries skipped (see warnings above)"));
            return registry;
        } catch (Exception e) {
            getLogger().severe("Failed to load content: " + e);
            return null;
        }
    }

    private void startArmorTask() {
        int interval = Math.max(1, configValues.armorTickSeconds) * 20;
        // Only players actually wearing custom armor are scanned (see ArmorTracker).
        armorTaskId = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (UUID uuid : armorTracker.snapshot()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    armorTracker.remove(uuid);
                    continue;
                }
                tickArmor(player);
            }
        }, 40L, interval).getTaskId();
        // Slow belt-and-braces reconciliation in case a plugin changed armor slots outside
        // of events (e.g. setItem direct calls).
        reconcileTaskId = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                armorTracker.recompute(player);
            }
        }, 600L, 600L).getTaskId();
    }

    private void tickArmor(Player player) {
        for (ItemStack stack : player.getInventory().getArmorContents()) {
            CustomItem item = contentRegistry.getItemByStack(stack);
            if (item == null) {
                continue;
            }
            for (String mechId : item.getMechanics().keySet()) {
                ItemMechanic mechanic = mechanicRegistry.get(mechId);
                if (mechanic != null) {
                    try {
                        mechanic.onWornTick(player, stack, item);
                    } catch (Exception e) {
                        getLogger().warning("Mechanic '" + mechId + "' failed on worn tick: " + e);
                    }
                }
            }
        }
    }

    private void cancelTasks() {
        if (armorTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(armorTaskId);
            armorTaskId = -1;
        }
        if (reconcileTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(reconcileTaskId);
            reconcileTaskId = -1;
        }
    }

    /** Run all configured on-use mechanics for an item. */
    public void runUseMechanics(Player player, CustomItem item, ItemStack stack, EquipmentSlot hand, PlayerEvent event) {
        MechanicContext context = new MechanicContext(player, event, item, stack, hand);
        for (Map.Entry<String, Map<String, Object>> mech : item.getMechanics().entrySet()) {
            ItemMechanic mechanic = mechanicRegistry.get(mech.getKey());
            if (mechanic == null) {
                continue;
            }
            try {
                mechanic.onUse(context);
            } catch (Exception e) {
                getLogger().warning("Mechanic '" + mech.getKey() + "' failed on use: " + e);
            }
        }
    }

    /** Run all configured on-hit mechanics for an item. */
    public void runHitMechanics(Player player, CustomItem item, ItemStack stack, Entity target) {
        MechanicContext context = new MechanicContext(player, null, item, stack, EquipmentSlot.HAND);
        for (Map.Entry<String, Map<String, Object>> mech : item.getMechanics().entrySet()) {
            ItemMechanic mechanic = mechanicRegistry.get(mech.getKey());
            if (mechanic == null) {
                continue;
            }
            try {
                mechanic.onHitEntity(context, target);
            } catch (Exception e) {
                getLogger().warning("Mechanic '" + mech.getKey() + "' failed on hit: " + e);
            }
        }
    }

    private ServerVersion.PackTarget computePackTarget(PluginConfig cfg) {
        Integer override = cfg.pack.formatOverride;
        if (override != null) {
            boolean range = ServerVersion.usesRangeFormat(serverVersion);
            ServerVersion.Mode mode = serverVersion.isAtLeast(1, 21, 2)
                    ? ServerVersion.Mode.MODERN : ServerVersion.Mode.LEGACY;
            return new ServerVersion.PackTarget(override, range, mode);
        }
        return ServerVersion.targetFor(serverVersion);
    }

    /** Always keep the assets/models + assets/textures folders around for imported models. */
    private void createAssetFolders() {
        for (String sub : List.of("models", "textures")) {
            File dir = new File(getDataFolder(), "assets/" + sub);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                getLogger().warning("Could not create folder " + dir.getPath());
            }
        }
    }

    private void copyExamplesIfMissing() {
        for (String folder : List.of("items", "blocks", "furniture")) {
            File dir = new File(getDataFolder(), folder);
            File[] existing = dir.listFiles((d, name) -> name.endsWith(".yml"));
            if (dir.isDirectory() && existing != null && existing.length > 0) {
                continue;
            }
            List<String> examples = switch (folder) {
                case "items" -> List.of("example_sword.yml", "example_helmet.yml", "example_food.yml");
                case "blocks" -> List.of("example_block.yml");
                default -> List.of("example_lamp.yml");
            };
            for (String name : examples) {
                saveResource(folder + "/" + name, false);
            }
        }
    }

    // ---- accessors used by internals and the API ----

    public PluginConfig getConfigValues() {
        return configValues;
    }

    public ContentRegistry getContentRegistry() {
        return contentRegistry;
    }

    public ItemFactory getItemFactory() {
        return itemFactory;
    }

    public MechanicRegistryImpl getMechanicRegistry() {
        return mechanicRegistry;
    }

    public ResourcePackManagerImpl getPackManager() {
        return packManager;
    }

    public ArmorTracker getArmorTracker() {
        return armorTracker;
    }

    public ServerVersion.Version getServerVersion() {
        return serverVersion;
    }

    public ServerVersion.PackTarget getPackTarget() {
        return packTarget;
    }
}
