package com.andreaitemmaker.pack;

import com.andreaitemmaker.AndreaitemmakerPlugin;
import com.andreaitemmaker.api.CustomItem;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link ResourcePackManager}: builds the zip, hosts/uploads it, sends it to players.
 *
 * <p>Generation never runs on the Minecraft main thread: the caller snapshots an immutable
 * {@link PackSnapshot} of the current content, the heavy work (PNG/JSON generation, zipping,
 * SHA-1, file writes, optional upload) runs on a background thread, and only the final
 * publish (starting the HTTP server, building the URL, atomically swapping the pack state)
 * happens back on the main thread. Until the new state is published, the previous pack keeps
 * being served, so a failed or in-flight generation never leaves players without a valid pack.
 */
public final class ResourcePackManagerImpl implements ResourcePackManager {

    /** Immutable result of one successful generation, published atomically. */
    private record PackState(byte[] packBytes, byte[] sha1Bytes, String sha1, String url,
                             int format, boolean serving, File packFile, File packFolder) {
    }

    /** Immutable snapshot of everything a generation needs; taken on the main thread. */
    record PackSnapshot(String namespace, ServerVersion.PackTarget target, ServerVersion.Version version,
                        int textureSize, String description, File dataFolder, List<CustomItem> items,
                        PluginConfig.Pack pack, java.util.logging.Logger logger) {
    }

    private record GenerationOutcome(Map<String, byte[]> entries, byte[] bytes, byte[] sha1Bytes,
                                     String sha1, int format, File packFile, File packFolder,
                                     String uploadedUrl) {
    }

    private final AndreaitemmakerPlugin plugin;
    private final PackHttpServer httpServer;
    private final PackUploader uploader = new PackUploader();
    private final GenerationCoordinator<PackSnapshot> coordinator = new GenerationCoordinator<>();
    private final Set<UUID> sentTo = ConcurrentHashMap.newKeySet();

    private volatile PackState state;

    public ResourcePackManagerImpl(AndreaitemmakerPlugin plugin) {
        this.plugin = plugin;
        this.httpServer = new PackHttpServer(plugin.getLogger(), this::rawPackBytes, this::getSha1);
    }

    @Override
    public boolean isGenerated() {
        return state != null;
    }

    /**
     * Regenerate the pack in the background from the current content. Returns true when a
     * generation was scheduled (or is already running and will pick up the latest content).
     * The pack is only swapped once it is fully generated, so the previous pack stays valid
     * until then.
     */
    @Override
    public boolean generate() {
        PackSnapshot snapshot = snapshot();
        if (!coordinator.claim(snapshot)) {
            return true; // a worker is already running and will pick this snapshot up
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::runWorker);
        return true;
    }

    /** Take an immutable snapshot of the current content/config. Must be called on the main thread. */
    private PackSnapshot snapshot() {
        PluginConfig cfg = plugin.getConfigValues();
        return new PackSnapshot(
                cfg.namespace,
                plugin.getPackTarget(),
                plugin.getServerVersion(),
                cfg.pack.textureSize,
                cfg.pack.description,
                plugin.getDataFolder(),
                List.copyOf(plugin.getContentRegistry().getAll()),
                cfg.pack,
                plugin.getLogger());
    }

    /** Background worker: generates the pack and publishes the finished state on the main thread. */
    private void runWorker() {
        while (true) {
            PackSnapshot snapshot = coordinator.next();
            try {
                PackGenerator.Context ctx = new PackGenerator.Context(
                        snapshot.namespace(), snapshot.target(), snapshot.version(), snapshot.textureSize(),
                        snapshot.description(), snapshot.dataFolder(), snapshot.items(), snapshot.logger());
                Map<String, byte[]> entries = PackGenerator.buildEntries(ctx);
                byte[] bytes = PackGenerator.zip(entries);
                byte[] sha1Bytes = MessageDigest.getInstance("SHA-1").digest(bytes);
                String sha1 = hex(sha1Bytes);
                int format = snapshot.target().format();

                File packFile = new File(snapshot.dataFolder(), "pack.zip");
                Files.write(packFile.toPath(), bytes);
                // Always write the unzipped pack folder too, so admins can host it manually
                // (file host, web server) when the built-in server is unreachable.
                File packFolder = new File(snapshot.dataFolder(), "pack");
                PackGenerator.writeFolder(entries, packFolder);
                snapshot.logger().info("Unzipped pack written to " + packFolder.getPath()
                        + " (host it anywhere and set pack.public-url)");

                // Uploads are plain HTTP, safe on this thread.
                String uploadedUrl = null;
                PluginConfig.Pack pack = snapshot.pack();
                if (pack.uploadEnabled && !pack.uploadUrl.isEmpty()) {
                    try {
                        uploadedUrl = uploader.upload(pack, bytes);
                        snapshot.logger().info("Uploaded resource pack (" + bytes.length + " bytes) to " + uploadedUrl);
                    } catch (Exception e) {
                        snapshot.logger().warning("Pack upload failed: " + e.getMessage());
                    }
                }

                GenerationOutcome outcome = new GenerationOutcome(
                        entries, bytes, sha1Bytes, sha1, format, packFile, packFolder, uploadedUrl);
                plugin.getServer().getScheduler().runTask(plugin, () -> publish(outcome, snapshot));
            } catch (Exception e) {
                snapshot.logger().severe("Failed to generate resource pack: " + e);
                // No state change: the previously generated pack (if any) keeps being served.
            }
            if (!coordinator.finish()) {
                return;
            }
        }
    }

    /** Main thread: hosting decisions, atomic publish of the finished pack, resend. */
    private void publish(GenerationOutcome outcome, PackSnapshot snapshot) {
        try {
            publish0(outcome, snapshot);
        } catch (Exception e) {
            snapshot.logger().severe("Failed to publish the generated resource pack: " + e);
        }
    }

    private void publish0(GenerationOutcome outcome, PackSnapshot snapshot) {
        PluginConfig.Pack pack = snapshot.pack();
        String url = "";
        boolean serving = false;
        if (outcome.uploadedUrl() != null && !outcome.uploadedUrl().isEmpty()) {
            url = outcome.uploadedUrl();
        } else if (pack.serveEnabled) {
            if (httpServer.start(pack.servePort)) {
                serving = true;
                url = buildUrl(pack);
            } else if (!pack.publicUrl.isEmpty()) {
                url = pack.publicUrl;
            }
        } else if (!pack.publicUrl.isEmpty()) {
            url = pack.publicUrl;
        } else {
            snapshot.logger().warning("No automatic hosting configured (upload disabled, pack server disabled, "
                    + "no public-url). Players will not receive the pack automatically.\n"
                    + "Host the unzipped pack in " + outcome.packFolder().getPath()
                    + " (or pack.zip) anywhere and set pack.public-url.");
        }

        state = new PackState(outcome.bytes(), outcome.sha1Bytes(), outcome.sha1(), url,
                outcome.format(), serving, outcome.packFile(), outcome.packFolder());
        sentTo.clear();
        snapshot.logger().info("Resource pack generated: " + outcome.bytes().length + " bytes, SHA-1 "
                + outcome.sha1() + ", format " + outcome.format());
        if (pack.resendOnReload) {
            sendToAll();
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
        PackState s = state;
        return s == null ? null : s.packFile();
    }

    @Override
    public File getPackFolder() {
        return new File(plugin.getDataFolder(), "pack");
    }

    @Override
    public byte[] getPackBytes() {
        PackState s = state;
        return s == null ? null : s.packBytes().clone();
    }

    /** Raw pack bytes for the HTTP server (no defensive copy per request). */
    private byte[] rawPackBytes() {
        PackState s = state;
        return s == null ? null : s.packBytes();
    }

    @Override
    public String getSha1() {
        PackState s = state;
        return s == null ? null : s.sha1();
    }

    @Override
    public String getUrl() {
        PackState s = state;
        return s == null ? null : s.url();
    }

    @Override
    public int getFormat() {
        PackState s = state;
        return s == null ? 0 : s.format();
    }

    @Override
    public String getNamespace() {
        return plugin.getConfigValues().namespace;
    }

    @Override
    public boolean isServing() {
        return state != null && state.serving() && httpServer.isRunning();
    }

    /** Whether the player already received the current pack (used to avoid double prompts). */
    public boolean wasSentTo(Player player) {
        return player != null && sentTo.contains(player.getUniqueId());
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
        sentTo.add(player.getUniqueId());
        Runnable task = () -> send0(player, packUrl);
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private void send0(Player player, String packUrl) {
        PackState s = state;
        if (s == null) {
            return;
        }
        PluginConfig.Pack pack = plugin.getConfigValues().pack;
        UUID packId = UUID.nameUUIDFromBytes(s.sha1Bytes());
        try {
            Method m = Player.class.getMethod("setResourcePack",
                    UUID.class, String.class, byte[].class, String.class, boolean.class);
            m.invoke(player, packId, packUrl, s.sha1Bytes(), pack.prompt, pack.required);
            return;
        } catch (NoSuchMethodException ignored) {
            // older server
        } catch (Exception e) {
            plugin.getLogger().fine("setResourcePack(UUID,...) failed: " + e.getMessage());
        }
        try {
            Method m = Player.class.getMethod("setResourcePack",
                    String.class, byte[].class, String.class, boolean.class);
            m.invoke(player, packUrl, s.sha1Bytes(), pack.prompt, pack.required);
        } catch (NoSuchMethodException ignored) {
            player.setResourcePack(packUrl, s.sha1Bytes());
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
        state = null;
        sentTo.clear();
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
