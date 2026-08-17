package com.andreaitemmaker.pack;

import com.andreaitemmaker.AndreaitemmakerPlugin;
import com.andreaitemmaker.api.ResourcePackManager;
import com.andreaitemmaker.config.PluginConfig;
import com.andreaitemmaker.util.ServerVersion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

/** Default {@link ResourcePackManager}: builds the zip, hosts/uploads it, sends it to players. */
public final class ResourcePackManagerImpl implements ResourcePackManager {

    private final AndreaitemmakerPlugin plugin;
    private final PackHttpServer httpServer;
    private final PackUploader uploader = new PackUploader();

    private volatile byte[] packBytes;
    private volatile byte[] sha1Bytes;
    private volatile String sha1;
    private volatile String url;
    private volatile int format;
    private volatile boolean serving;

    public ResourcePackManagerImpl(AndreaitemmakerPlugin plugin) {
        this.plugin = plugin;
        this.httpServer = new PackHttpServer(plugin.getLogger());
    }

    @Override
    public boolean isGenerated() {
        return packBytes != null;
    }

    @Override
    public synchronized boolean generate() {
        try {
            ServerVersion.PackTarget target = plugin.getPackTarget();
            PackGenerator.Context ctx = new PackGenerator.Context(
                    plugin.getConfigValues().namespace,
                    target,
                    plugin.getServerVersion(),
                    plugin.getConfigValues().pack.textureSize,
                    plugin.getConfigValues().pack.description,
                    plugin.getDataFolder(),
                    plugin.getContentRegistry().getAll(),
                    plugin.getLogger());
            Map<String, byte[]> entries = PackGenerator.buildEntries(ctx);
            byte[] bytes = PackGenerator.zip(entries);
            this.packBytes = bytes;
            this.sha1Bytes = MessageDigest.getInstance("SHA-1").digest(bytes);
            this.sha1 = hex(sha1Bytes);
            this.format = target.format();

            File packFile = new File(plugin.getDataFolder(), "pack.zip");
            Files.write(packFile.toPath(), bytes);
            // Always write the unzipped pack folder too, so admins can host it manually
            // (file host, web server) when the built-in server is unreachable.
            File packFolder = new File(plugin.getDataFolder(), "pack");
            PackGenerator.writeFolder(entries, packFolder);
            plugin.getLogger().info("Unzipped pack written to " + packFolder.getPath()
                    + " (host it anywhere and set pack.public-url)");

            PluginConfig.Pack pack = plugin.getConfigValues().pack;
            this.serving = false;
            this.url = "";
            if (pack.uploadEnabled && !pack.uploadUrl.isEmpty()) {
                try {
                    this.url = uploader.upload(pack, bytes);
                    plugin.getLogger().info("Uploaded resource pack (" + bytes.length + " bytes) to " + url);
                } catch (Exception e) {
                    plugin.getLogger().warning("Pack upload failed: " + e.getMessage());
                    if (pack.serveEnabled) {
                        startServer(pack, packFile);
                    }
                }
            } else if (pack.serveEnabled) {
                startServer(pack, packFile);
            } else if (!pack.publicUrl.isEmpty()) {
                this.url = pack.publicUrl;
            } else {
                plugin.getLogger().warning("No automatic hosting configured (upload disabled, pack server disabled, "
                        + "no public-url). Players will not receive the pack automatically.\n"
                        + "Host the unzipped pack in " + new File(plugin.getDataFolder(), "pack").getPath()
                        + " (or pack.zip) anywhere and set pack.public-url.");
            }
            plugin.getLogger().info("Resource pack generated: " + bytes.length + " bytes, SHA-1 " + sha1
                    + ", format " + format);
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to generate resource pack: " + e);
            this.packBytes = null;
            return false;
        }
    }

    private void startServer(PluginConfig.Pack pack, File packFile) {
        if (httpServer.start(pack.servePort, packFile)) {
            this.serving = true;
            this.url = buildUrl(pack);
        } else if (!pack.publicUrl.isEmpty()) {
            this.url = pack.publicUrl;
        }
    }

    private String buildUrl(PluginConfig.Pack pack) {
        if (!pack.publicUrl.isEmpty()) {
            return pack.publicUrl;
        }
        String ip = pack.publicIp;
        if (ip == null || ip.isEmpty()) {
            ip = Bukkit.getServer().getIp();
        }
        if (ip == null || ip.isEmpty()) {
            try {
                ip = InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                ip = "127.0.0.1";
            }
        }
        return "http://" + ip + ":" + pack.servePort + "/pack.zip";
    }

    @Override
    public File getPackFile() {
        return packBytes == null ? null : new File(plugin.getDataFolder(), "pack.zip");
    }

    @Override
    public File getPackFolder() {
        return new File(plugin.getDataFolder(), "pack");
    }

    @Override
    public byte[] getPackBytes() {
        return packBytes;
    }

    @Override
    public String getSha1() {
        return sha1;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public int getFormat() {
        return format;
    }

    @Override
    public String getNamespace() {
        return plugin.getConfigValues().namespace;
    }

    @Override
    public boolean isServing() {
        return serving && httpServer.isRunning();
    }

    @Override
    public void sendTo(Player player) {
        if (!isGenerated() || player == null || !player.isOnline()) {
            return;
        }
        if (player.hasPermission("andreaitemmaker.bypass")) {
            return;
        }
        String packUrl = getUrl();
        if (packUrl == null || packUrl.isEmpty()) {
            return;
        }
        Runnable task = () -> send0(player, packUrl);
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private void send0(Player player, String packUrl) {
        PluginConfig.Pack pack = plugin.getConfigValues().pack;
        UUID packId = UUID.nameUUIDFromBytes(sha1Bytes);
        try {
            Method m = Player.class.getMethod("setResourcePack",
                    UUID.class, String.class, byte[].class, String.class, boolean.class);
            m.invoke(player, packId, packUrl, sha1Bytes, pack.prompt, pack.required);
            return;
        } catch (NoSuchMethodException ignored) {
            // older server
        } catch (Exception e) {
            plugin.getLogger().fine("setResourcePack(UUID,...) failed: " + e.getMessage());
        }
        try {
            Method m = Player.class.getMethod("setResourcePack",
                    String.class, byte[].class, String.class, boolean.class);
            m.invoke(player, packUrl, sha1Bytes, pack.prompt, pack.required);
        } catch (NoSuchMethodException ignored) {
            player.setResourcePack(packUrl, sha1Bytes);
        } catch (Exception e) {
            plugin.getLogger().fine("setResourcePack(String,...) failed: " + e.getMessage());
        }
    }

    @Override
    public void sendToAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendTo(player);
        }
    }

    @Override
    public void shutdown() {
        httpServer.stop();
        serving = false;
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
