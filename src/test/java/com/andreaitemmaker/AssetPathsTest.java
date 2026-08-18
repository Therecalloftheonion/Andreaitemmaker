package com.andreaitemmaker;

import com.andreaitemmaker.util.AssetPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetPathsTest {

    @Test
    void acceptsSafeRelativePaths() {
        assertTrue(AssetPaths.isSafeRelative("assets/models/sword.json"));
        assertTrue(AssetPaths.isSafeRelative("assets/textures/icon.png"));
        assertTrue(AssetPaths.isSafeRelative("sub/folder/file.json"));
        assertTrue(AssetPaths.isSafeRelative("assets/models/sub/thing.json"));
    }

    @Test
    void rejectsTraversal() {
        assertFalse(AssetPaths.isSafeRelative("../secret.yml"));
        assertFalse(AssetPaths.isSafeRelative("assets/../config.yml"));
        assertFalse(AssetPaths.isSafeRelative("a/b/../../c"));
        assertFalse(AssetPaths.isSafeRelative(".."));
        assertFalse(AssetPaths.isSafeRelative("assets/models/../../config.yml"));
    }

    @Test
    void rejectsAbsolutePaths() {
        assertFalse(AssetPaths.isSafeRelative("/etc/passwd"));
        assertFalse(AssetPaths.isSafeRelative("/absolute/path.json"));
        assertFalse(AssetPaths.isSafeRelative("//etc/passwd")); // UNC-style, absolute after normalization
    }

    @Test
    void rejectsWindowsDriveAndBackslashTraversal() {
        assertFalse(AssetPaths.isSafeRelative("C:\\windows\\system32"));
        assertFalse(AssetPaths.isSafeRelative("C:/windows/system32"));
        // Windows-style traversal written with backslashes must be caught too.
        assertFalse(AssetPaths.isSafeRelative("assets\\..\\..\\config.yml"));
        assertFalse(AssetPaths.isSafeRelative("..\\..\\secret"));
    }

    @Test
    void rejectsNulAndEmpty() {
        assertFalse(AssetPaths.isSafeRelative(null));
        assertFalse(AssetPaths.isSafeRelative(""));
        assertFalse(AssetPaths.isSafeRelative("assets/\u0000x.json"));
    }

    @Test
    void assetPathsMustLiveUnderAssets() {
        assertTrue(AssetPaths.isSafeAssetPath("assets/models/sword.json"));
        assertTrue(AssetPaths.isSafeAssetPath("assets/textures/x.png"));
        assertFalse(AssetPaths.isSafeAssetPath("models/sword.json")); // missing assets/ prefix
        assertFalse(AssetPaths.isSafeAssetPath("../assets/models/sword.json"));
    }

    @Test
    void resolveStaysInsideDataFolder(@TempDir Path tempDir) {
        File dataFolder = tempDir.toFile();
        File resolved = AssetPaths.resolve(dataFolder, "assets/models/sword.json");
        assertNotNull(resolved);
        assertEquals(dataFolder.toPath().resolve("assets/models/sword.json").toString(), resolved.getPath());
    }

    @Test
    void resolveRejectsEscapes(@TempDir Path tempDir) {
        File dataFolder = tempDir.toFile();
        assertNull(AssetPaths.resolve(dataFolder, "../outside.yml"));
        assertNull(AssetPaths.resolve(dataFolder, "assets/../../outside.yml"));
        assertNull(AssetPaths.resolve(dataFolder, "/etc/passwd"));
        assertNull(AssetPaths.resolve(dataFolder, "C:\\windows\\system32"));
        assertNull(AssetPaths.resolve(dataFolder, "..\\..\\secret"));
    }

    @Test
    void resolveRejectsNullFolder() {
        assertNull(AssetPaths.resolve(null, "assets/models/sword.json"));
    }
}
