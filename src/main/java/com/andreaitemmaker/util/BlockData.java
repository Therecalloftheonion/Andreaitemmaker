package com.andreaitemmaker.util;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Persistent, coordinate-keyed identity for placed custom blocks.
 *
 * <p>Blocks themselves have no persistent data container in the Bukkit API, so the id is
 * stored in the <b>chunk's</b> PDC under {@link Keys#BLOCK_DATA} as a nested container whose
 * keys are coordinate-based {@link NamespacedKey}s ({@code b_&lt;x&gt;_&lt;y&gt;_&lt;z&gt;})
 * mapping to the content id. Chunk PDC survives restarts, chunk unload/reload and works on
 * every block type and every supported server version. Because the key includes the
 * coordinates, ids can never collide across worlds, and a normal vanilla block never carries
 * a tag unless this plugin placed it there.
 */
public final class BlockData {

    private static final PersistentDataType<PersistentDataContainer, PersistentDataContainer> CONTAINER =
            PersistentDataType.TAG_CONTAINER;

    private BlockData() {
    }

    /** The custom block id placed at {@code block}, or null when it is a normal block. */
    public static String get(Block block) {
        PersistentDataContainer chunkData = block.getChunk().getPersistentDataContainer();
        PersistentDataContainer blocks = chunkData.get(Keys.BLOCK_DATA, CONTAINER);
        return blocks == null ? null : blocks.get(key(block), PersistentDataType.STRING);
    }

    /** Tag {@code block} as a placed custom block with the given content id. */
    public static void set(Block block, String id) {
        Chunk chunk = block.getChunk();
        PersistentDataContainer chunkData = chunk.getPersistentDataContainer();
        PersistentDataContainer blocks = chunkData.getOrDefault(Keys.BLOCK_DATA, CONTAINER,
                chunkData.getAdapterContext().newPersistentDataContainer());
        blocks.set(key(block), PersistentDataType.STRING, id);
        // Write the container back so the chunk data is updated even on implementations
        // where getPersistentDataContainer() hands out a detached copy.
        chunkData.set(Keys.BLOCK_DATA, CONTAINER, blocks);
    }

    /** Remove the custom block tag at {@code block} (e.g. when the block is broken/replaced). */
    public static void remove(Block block) {
        Chunk chunk = block.getChunk();
        PersistentDataContainer chunkData = chunk.getPersistentDataContainer();
        PersistentDataContainer blocks = chunkData.get(Keys.BLOCK_DATA, CONTAINER);
        if (blocks == null) {
            return;
        }
        blocks.remove(key(block));
        if (blocks.isEmpty()) {
            chunkData.remove(Keys.BLOCK_DATA);
        } else {
            chunkData.set(Keys.BLOCK_DATA, CONTAINER, blocks);
        }
    }

    private static NamespacedKey key(Block block) {
        // NamespacedKey keys allow lowercase letters, digits, '_', '-', '.' and '/'.
        return new NamespacedKey("andreaitemmaker",
                "b_" + block.getX() + "_" + block.getY() + "_" + block.getZ());
    }
}
