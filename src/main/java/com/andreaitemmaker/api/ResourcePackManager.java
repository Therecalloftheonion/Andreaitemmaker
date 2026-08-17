package com.andreaitemmaker.api;

import org.bukkit.entity.Player;

import java.io.File;

/**
 * Manages the generated resource pack: building the zip, hosting or uploading it,
 * and sending it to players.
 */
public interface ResourcePackManager {

    /** Whether a pack has been generated successfully. */
    boolean isGenerated();

    /** Regenerate the pack from the current content. Returns true on success. */
    boolean generate();

    /** The generated zip file (may not exist until {@link #generate()} succeeds). */
    File getPackFile();

    /**
     * The unzipped pack folder, written next to the zip on every generation. Handy for
     * hosting manually (file host, web server) when the built-in server is unreachable.
     */
    File getPackFolder();

    /** The zip bytes (null until generated). */
    byte[] getPackBytes();

    /** Lowercase SHA-1 hex of the pack, used by the client to verify the download. */
    String getSha1();

    /** The URL players are told to download the pack from. */
    String getUrl();

    /** The pack format number the pack was generated for. */
    int getFormat();

    /** The namespace used for pack assets, e.g. "itemmaker". */
    String getNamespace();

    /** Whether the built-in HTTP server is currently serving the pack. */
    boolean isServing();

    /** Send the pack to a single player (no-op when not generated). */
    void sendTo(Player player);

    /** Send the pack to every online player. */
    void sendToAll();

    /** Stop the HTTP server if it is running. */
    void shutdown();
}
