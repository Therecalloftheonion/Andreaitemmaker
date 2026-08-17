package com.andreaitemmaker.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keeps config.yml in sync with the version shipped inside the jar. When the config version
 * stored in the file is older than {@link #CURRENT_VERSION}, the shipped defaults are merged
 * underneath the admin's existing values: new keys are added, user settings are preserved,
 * and the version is bumped. One extra line in a changelog-style comment keeps admins informed.
 */
public final class ConfigMigrator {

    /** Bump when config.yml gains or changes keys, and add matching migration logic. */
    public static final int CURRENT_VERSION = 1;

    private final JavaPlugin plugin;

    public ConfigMigrator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Load the main config, writing the shipped default when missing and migrating older
     * versions. Returns the loaded configuration (never null).
     */
    public YamlConfiguration loadMainConfig() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            plugin.saveResource("config.yml", false);
            return YamlConfiguration.loadConfiguration(file);
        }
        YamlConfiguration user = YamlConfiguration.loadConfiguration(file);
        int userVersion = user.getInt("config-version", 0);
        if (userVersion >= CURRENT_VERSION) {
            return user;
        }
        plugin.getLogger().info("Config version " + userVersion + " -> " + CURRENT_VERSION + ", migrating config.yml");
        YamlConfiguration defaults = loadDefaults();
        YamlConfiguration merged = new YamlConfiguration();
        for (Map.Entry<String, Object> e : defaults.getValues(true).entrySet()) {
            merged.set(e.getKey(), e.getValue());
        }
        // Overlay user values (deep merge for sections).
        overlay(user, merged, "");
        merged.set("config-version", CURRENT_VERSION);
        try {
            merged.save(file);
            plugin.getLogger().info("Migrated config.yml to version " + CURRENT_VERSION + " (your settings were preserved)");
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save migrated config.yml: " + e.getMessage());
        }
        return merged;
    }

    private YamlConfiguration loadDefaults() {
        try (InputStreamReader reader = new InputStreamReader(
                plugin.getResource("config.yml"), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception e) {
            throw new IllegalStateException("Bundled config.yml is missing or unreadable", e);
        }
    }

    private static void overlay(YamlConfiguration source, YamlConfiguration target, String prefix) {
        for (String key : source.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = source.get(key);
            if (value instanceof ConfigurationSection) {
                overlay(source, target, path);
            } else if (value != null) {
                target.set(path, value);
            }
        }
    }

    /** Deep-merge helper kept for future migrations that need to transform specific keys. */
    @SuppressWarnings("unchecked")
    static Object merge(Object defaults, Object user) {
        if (defaults instanceof Map<?, ?> dm && user instanceof Map<?, ?> um) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : dm.entrySet()) {
                Object key = e.getKey();
                Object userValue = ((Map<?, ?>) um).get(key);
                out.put(String.valueOf(key), userValue == null ? e.getValue() : merge(e.getValue(), userValue));
            }
            for (Map.Entry<?, ?> e : um.entrySet()) {
                out.putIfAbsent(String.valueOf(e.getKey()), e.getValue());
            }
            return out;
        }
        return user == null ? defaults : user;
    }
}
