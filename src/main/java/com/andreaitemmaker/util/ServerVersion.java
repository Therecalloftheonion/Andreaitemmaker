package com.andreaitemmaker.util;

import java.util.Objects;

/**
 * Parses {@code Bukkit.getBukkitVersion()} style strings ("1.21.4-R0.1-SNAPSHOT", "26.2-R0.1-SNAPSHOT")
 * and decides which resource pack layout to generate for the running server.
 */
public final class ServerVersion {

    /** How the resource pack is wired to items. */
    public enum Mode {
        /** &lt; 1.21.2: patch vanilla models with custom_model_data predicates. */
        LEGACY,
        /** &gt;= 1.21.2: per-namespace item definitions + the item_model component. */
        MODERN
    }

    public record Version(int major, int minor, int patch) implements Comparable<Version> {
        public boolean isAtLeast(int major, int minor) {
            return compareTo(new Version(major, minor, 0)) >= 0;
        }

        public boolean isAtLeast(int major, int minor, int patch) {
            return compareTo(new Version(major, minor, patch)) >= 0;
        }

        @Override
        public int compareTo(Version o) {
            if (major != o.major) {
                return Integer.compare(major, o.major);
            }
            if (minor != o.minor) {
                return Integer.compare(minor, o.minor);
            }
            return Integer.compare(patch, o.patch);
        }

        @Override
        public String toString() {
            return major + "." + minor + (patch > 0 ? "." + patch : "");
        }
    }

    /** Result of mapping a server version to pack generation settings. */
    public record PackTarget(int format, boolean rangeFormat, Mode mode) {
    }

    private ServerVersion() {
    }

    /**
     * Parse a Bukkit version string. Accepts "1.21.4-R0.1-SNAPSHOT", "26.2-R0.1-SNAPSHOT", "1.21", etc.
     * Returns {@code null} when the string cannot be parsed.
     */
    public static Version parse(String bukkitVersion) {
        if (bukkitVersion == null || bukkitVersion.isEmpty()) {
            return null;
        }
        String core = bukkitVersion.trim();
        int dash = core.indexOf('-');
        if (dash >= 0) {
            core = core.substring(0, dash);
        }
        String[] parts = core.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return new Version(major, minor, patch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Map a server version to the resource pack format number (and whether pack.mcmeta
     * needs the min_format/max_format range form used since 1.21.9).
     */
    public static PackTarget targetFor(Version v) {
        int format;
        if (v.major() == 1) {
            if (v.minor() == 20) {
                format = switch (v.patch()) {
                    case 5, 6 -> 32;
                    default -> unknown(v);
                };
            } else if (v.minor() == 21) {
                format = switch (v.patch()) {
                    case 0, 1 -> 34;
                    case 2, 3 -> 42;
                    case 4 -> 46;
                    case 5 -> 55;
                    case 6 -> 63;
                    case 7, 8 -> 64;
                    case 9, 10 -> 69;
                    case 11 -> 75;
                    default -> unknown(v);
                };
            } else {
                format = unknown(v);
            }
        } else if (v.major() == 26) {
            format = switch (v.minor()) {
                case 1 -> 84;
                default -> 88; // 26.2 and later (including unknown future 26.x)
            };
        } else {
            format = unknown(v);
        }
        boolean rangeFormat = v.major() > 1 || v.minor() >= 21 && v.patch() >= 9 || v.major() == 26;
        Mode mode = v.isAtLeast(1, 21, 2) ? Mode.MODERN : Mode.LEGACY;
        return new PackTarget(format, rangeFormat, mode);
    }

    private static int unknown(Version v) {
        // Newer than anything we know about: assume the newest layout so packs keep working.
        return 88;
    }

    /** True when the pack.mcmeta should use min_format/max_format (1.21.9+). */
    public static boolean usesRangeFormat(Version v) {
        Objects.requireNonNull(v, "version");
        if (v.major() > 1) {
            return true;
        }
        return v.major() == 1 && v.minor() == 21 && v.patch() >= 9;
    }
}
