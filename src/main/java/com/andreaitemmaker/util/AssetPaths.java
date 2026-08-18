package com.andreaitemmaker.util;

import java.io.File;
import java.io.IOException;

/**
 * Centralized resolver for every path that comes from YAML config, the developer API or
 * imported asset files. All asset paths must be relative, stay inside the plugin's data
 * folder, and never contain {@code ..} segments, absolute paths or symlink escapes.
 */
public final class AssetPaths {

    /** Asset paths are documented as living under this folder. */
    public static final String ASSETS_PREFIX = "assets/";

    private AssetPaths() {
    }

    /**
     * True when {@code path} is a safe relative path (no absolute paths, no {@code ..}
     * segments, no drive letters, no NUL bytes). Backslashes are treated as separators so
     * Windows-style traversal attempts are caught too.
     */
    public static boolean isSafeRelative(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        String normalized = normalize(path);
        if (normalized.startsWith("/")) {
            return false; // absolute
        }
        if (normalized.matches("^[A-Za-z]:.*")) {
            return false; // Windows drive letter
        }
        if (normalized.indexOf('\u0000') >= 0) {
            return false;
        }
        for (String segment : normalized.split("/")) {
            if (segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    /**
     * True when {@code path} is both safe and inside the documented {@code assets/} folder.
     * Config entries for textures and models must satisfy this.
     */
    public static boolean isSafeAssetPath(String path) {
        return path != null && path.startsWith(ASSETS_PREFIX) && isSafeRelative(path);
    }

    /**
     * Resolve {@code path} against {@code dataFolder} and return the file it points at, or
     * {@code null} when the path is unsafe, escapes the data folder (including via symlinks)
     * or the canonical form cannot be determined.
     */
    public static File resolve(File dataFolder, String path) {
        if (dataFolder == null || !isSafeRelative(path)) {
            return null;
        }
        File base = canonical(dataFolder);
        if (base == null) {
            return null;
        }
        File candidate = new File(base, normalize(path));
        File canonical = canonical(candidate);
        if (canonical == null) {
            return null;
        }
        String basePath = base.getPath();
        String canonPath = canonical.getPath();
        if (!canonPath.equals(basePath) && !canonPath.startsWith(basePath + File.separator)) {
            return null; // symlink or other escape
        }
        return canonical;
    }

    /** Normalize separators and collapse duplicate slashes for comparison. */
    static String normalize(String path) {
        return path.replace('\\', '/').replaceAll("/{2,}", "/");
    }

    private static File canonical(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return null;
        }
    }
}
