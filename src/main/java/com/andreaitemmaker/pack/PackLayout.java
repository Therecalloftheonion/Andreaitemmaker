package com.andreaitemmaker.pack;

import com.andreaitemmaker.api.CustomItem;
import com.andreaitemmaker.util.ServerVersion;
import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * Isolates everything that differs between the legacy (&lt; 1.21.2) and modern (&gt;= 1.21.2)
 * resource-pack layouts, so {@link PackGenerator} itself has no scattered version checks.
 * Select an implementation with {@link #forTarget(ServerVersion.PackTarget)}.
 */
interface PackLayout {

    /** Whether this layout uses the modern per-namespace item definitions. */
    boolean isModern();

    /**
     * Write the item definition that wires a stack to its model: a per-namespace
     * {@code items/<id>.json} on modern servers, or a group into the legacy override
     * lists keyed by base material.
     */
    void writeItemDefinition(Map<String, byte[]> entries, PackGenerator.Context ctx,
                             CustomItem item, Map<Material, List<CustomItem>> legacyGroups);

    /**
     * Write the version-specific part of armor assets (layer PNGs are written by the
     * generator itself since every version needs them).
     */
    void writeArmorAssets(Map<String, byte[]> entries, PackGenerator.Context ctx,
                          CustomItem item, byte[] layer1, byte[] layer2);

    /** Legacy-only: patch vanilla base-item models with custom_model_data predicates. */
    default void writeLegacyOverrides(Map<String, byte[]> entries, PackGenerator.Context ctx,
                                      Map<Material, List<CustomItem>> legacyGroups) {
        // no-op on modern layouts
    }

    static PackLayout forTarget(ServerVersion.PackTarget target) {
        return target.mode() == ServerVersion.Mode.MODERN
                ? new ModernPackLayout()
                : new LegacyPackLayout();
    }
}
