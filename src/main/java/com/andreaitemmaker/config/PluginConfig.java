package com.andreaitemmaker.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable, typed view of the plugin's config.yml. */
public final class PluginConfig {

    public final String namespace;
    public final Pack pack;
    public final int customModelDataStart;
    public final int armorTickSeconds;

    private PluginConfig(String namespace, Pack pack, int customModelDataStart, int armorTickSeconds) {
        this.namespace = namespace;
        this.pack = pack;
        this.customModelDataStart = customModelDataStart;
        this.armorTickSeconds = armorTickSeconds;
    }

    public static PluginConfig from(YamlConfiguration yaml) {
        String namespace = yaml.getString("namespace", "itemmaker");
        Pack pack = new Pack(yaml.getConfigurationSection("pack"));
        int cmdStart = yaml.getInt("content.custom-model-data-start", 1000);
        int armorTick = Math.max(1, yaml.getInt("content.armor-tick-seconds", 2));
        return new PluginConfig(namespace, pack, cmdStart, armorTick);
    }

    /** Resource pack related settings. */
    public static final class Pack {
        public final int textureSize;
        public final String description;
        public final boolean sendOnJoin;
        public final boolean required;
        public final String prompt;
        public final boolean resendOnReload;
        public final Integer formatOverride;
        public final boolean serveEnabled;
        public final int servePort;
        public final String publicUrl;
        public final String publicIp;
        public final boolean uploadEnabled;
        public final String uploadMethod;
        public final String uploadUrl;
        public final String uploadPublicUrl;
        public final Map<String, String> uploadHeaders;

        Pack(ConfigurationSection s) {
            ConfigurationSection root = s == null ? new YamlConfiguration() : s;
            this.textureSize = clampTextureSize(root.getInt("texture-size", 16));
            this.description = root.getString("description", "Andreaitemmaker custom content");
            this.sendOnJoin = root.getBoolean("send-on-join", true);
            this.required = root.getBoolean("required", false);
            this.prompt = root.getString("prompt", "Install the custom content pack?");
            this.resendOnReload = root.getBoolean("resend-on-reload", true);
            Object fmt = root.get("format", "AUTO");
            this.formatOverride = (fmt instanceof Number n) ? n.intValue() : null;
            ConfigurationSection serve = root.getConfigurationSection("serve");
            this.serveEnabled = serve == null || serve.getBoolean("enabled", true);
            this.servePort = serve == null ? 8163 : serve.getInt("port", 8163);
            this.publicUrl = root.getString("public-url", "");
            this.publicIp = root.getString("public-ip", "");
            ConfigurationSection upload = root.getConfigurationSection("upload");
            this.uploadEnabled = upload != null && upload.getBoolean("enabled", false);
            this.uploadMethod = upload == null ? "PUT" : upload.getString("method", "PUT").toUpperCase();
            this.uploadUrl = upload == null ? "" : upload.getString("url", "");
            this.uploadPublicUrl = upload == null ? "" : upload.getString("public-url", "");
            Map<String, String> headers = new LinkedHashMap<>();
            if (upload != null && upload.isConfigurationSection("headers")) {
                for (String key : upload.getConfigurationSection("headers").getKeys(false)) {
                    headers.put(key, upload.getString("headers." + key, ""));
                }
            }
            this.uploadHeaders = Collections.unmodifiableMap(headers);
        }

        private static int clampTextureSize(int size) {
            if (size == 32 || size == 64) {
                return size;
            }
            return 16;
        }
    }
}
