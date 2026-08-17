package com.andreaitemmaker;

import com.andreaitemmaker.api.CustomBlock;
import com.andreaitemmaker.api.CustomItem;
import com.andreaitemmaker.api.CustomItemType;
import com.andreaitemmaker.pack.PackGenerator;
import com.andreaitemmaker.util.ServerVersion;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackGeneratorTest {

    private static final Logger LOG = Logger.getLogger("PackGeneratorTest");

    private static final CustomItem SWORD = new CustomItem(
            "storm_blade", CustomItemType.WEAPON, Material.DIAMOND_SWORD, "Storm Blade", List.of(),
            1001, 1, Map.of(), Map.of(), true, true, "gradient|#3f9bff|#0f2a6b|true", null, Map.of());

    private static final CustomItem ARMOR = new CustomItem(
            "verdant_helmet", CustomItemType.ARMOR, Material.DIAMOND_HELMET, "Verdant Helmet", List.of(),
            1002, 1, Map.of(), Map.of(), false, false, "#4ade80", null, Map.of());

    private static final CustomBlock BLOCK = new CustomBlock(
            "amethyst_crate", Material.STICK, "Amethyst Crate", List.of(),
            1003, 1, Map.of(), Map.of(), false, false, "checker|#c084fc|#581c87|true", null, Map.of(),
            Material.WHITE_WOOL, true);

    @Test
    void generatesModernPack() throws Exception {
        ServerVersion.Version v = ServerVersion.parse("1.21.4-R0.1-SNAPSHOT");
        PackGenerator.Context ctx = new PackGenerator.Context(
                "itemmaker", ServerVersion.targetFor(v), v, 16, "test pack", null,
                List.of(SWORD, ARMOR, BLOCK), LOG);
        byte[] zip = PackGenerator.generate(ctx);
        Map<String, byte[]> entries = readZip(zip);

        assertEntry(entries, "pack.mcmeta");
        assertNotNull(entries.get("pack.png"));
        assertJson(entries, "assets/itemmaker/items/storm_blade.json");
        assertJson(entries, "assets/itemmaker/models/item/storm_blade.json");
        assertPng(entries, "assets/itemmaker/textures/item/storm_blade.png");
        // armor equipment asset
        assertJson(entries, "assets/itemmaker/equipment/verdant_helmet.json");
        assertPng(entries, "assets/itemmaker/textures/models/armor/verdant_helmet_layer_1.png");
        // block
        assertJson(entries, "assets/itemmaker/models/block/amethyst_crate.json");
        assertJson(entries, "assets/minecraft/blockstates/white_wool.json");
        assertPng(entries, "assets/itemmaker/textures/block/amethyst_crate.png");

        JsonObject mcmeta = JsonParser.parseString(new String(entries.get("pack.mcmeta")))
                .getAsJsonObject();
        assertEquals(46, mcmeta.getAsJsonObject("pack").get("pack_format").getAsInt());
    }

    @Test
    void generatesModernRangeMcmeta() throws Exception {
        ServerVersion.Version v = ServerVersion.parse("1.21.9-R0.1-SNAPSHOT");
        PackGenerator.Context ctx = new PackGenerator.Context(
                "itemmaker", ServerVersion.targetFor(v), v, 16, "test", null, List.of(SWORD), LOG);
        Map<String, byte[]> entries = readZip(PackGenerator.generate(ctx));
        JsonObject pack = JsonParser.parseString(new String(entries.get("pack.mcmeta")))
                .getAsJsonObject().getAsJsonObject("pack");
        assertEquals(69, pack.get("min_format").getAsInt());
        assertEquals(69, pack.get("max_format").getAsInt());
    }

    @Test
    void generatesLegacyPack() throws Exception {
        ServerVersion.Version v = ServerVersion.parse("1.21.1-R0.1-SNAPSHOT");
        PackGenerator.Context ctx = new PackGenerator.Context(
                "itemmaker", ServerVersion.targetFor(v), v, 16, "test", null,
                List.of(SWORD, BLOCK), LOG);
        Map<String, byte[]> entries = readZip(PackGenerator.generate(ctx));

        // override file for the diamond_sword base with our predicate
        JsonObject model = JsonParser.parseString(new String(entries.get(
                "assets/minecraft/models/item/diamond_sword.json"))).getAsJsonObject();
        assertEquals("minecraft:item/handheld", model.get("parent").getAsString());
        JsonObject override = model.getAsJsonArray("overrides").get(0).getAsJsonObject();
        assertEquals(1001, override.getAsJsonObject("predicate").get("custom_model_data").getAsInt());
        assertEquals("itemmaker:item/storm_blade", override.get("model").getAsString());

        assertNotNull(entries.get("assets/minecraft/blockstates/white_wool.json"));
        assertTrue(entries.containsKey("assets/itemmaker/models/item/storm_blade.json"));
        // no modern item definitions in legacy mode
        assertTrue(!entries.containsKey("assets/itemmaker/items/storm_blade.json"));
    }

    @Test
    void modelWithoutExplicitTextureUsesImportedPng(@TempDir Path tempDir) throws Exception {
        // Item has an imported model and no texture field: its PNG from assets/textures/
        // must end up in the pack (and not be shadowed by a generated placeholder).
        Path textures = tempDir.resolve("assets/textures");
        Files.createDirectories(textures);
        Files.createDirectories(tempDir.resolve("assets/models"));
        byte[] png = com.andreaitemmaker.util.PngWriter.write(16, 16, new int[16 * 16]);
        Files.write(textures.resolve("spadafulmini.png"), png);
        Files.writeString(tempDir.resolve("assets/models/spadafulmini.json"),
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"itemmaker:item/spadafulmini\"}}");

        CustomItem sword = new CustomItem("spadafulmini", CustomItemType.WEAPON, Material.DIAMOND_SWORD,
                "Spada Fulmini", List.of(), 2001, 1, Map.of(), Map.of(), false, false, null,
                "assets/models/spadafulmini.json", Map.of());
        ServerVersion.Version v = ServerVersion.parse("1.21.4-R0.1-SNAPSHOT");
        PackGenerator.Context ctx = new PackGenerator.Context("itemmaker", ServerVersion.targetFor(v), v,
                16, "test", tempDir.toFile(), List.of(sword), LOG);

        Map<String, byte[]> entries = PackGenerator.buildEntries(ctx);
        byte[] written = entries.get("assets/itemmaker/textures/item/spadafulmini.png");
        assertNotNull(written, "imported texture must be copied for a model without explicit texture");
        assertTrue(java.util.Arrays.equals(png, written), "imported PNG bytes must be preserved");
        // And the model must be the imported one, not the generated fallback.
        String model = new String(entries.get("assets/itemmaker/models/item/spadafulmini.json"));
        assertTrue(model.contains("layer0\":\"itemmaker:item/spadafulmini"));
    }

    @Test
    void writesUnzippedFolderMatchingZip(@TempDir Path tempDir) throws Exception {
        ServerVersion.Version v = ServerVersion.parse("1.21.4-R0.1-SNAPSHOT");
        PackGenerator.Context ctx = new PackGenerator.Context(
                "itemmaker", ServerVersion.targetFor(v), v, 16, "test", null,
                List.of(SWORD, ARMOR, BLOCK), LOG);
        Map<String, byte[]> entries = PackGenerator.buildEntries(ctx);
        byte[] zip = PackGenerator.zip(entries);

        File folder = tempDir.resolve("pack").toFile();
        PackGenerator.writeFolder(entries, folder);

        Map<String, byte[]> zipEntries = readZip(zip);
        assertEquals(zipEntries.keySet(), readFolder(folder).keySet(), "folder must contain the same files as the zip");
        for (Map.Entry<String, byte[]> e : zipEntries.entrySet()) {
            assertTrue(java.util.Arrays.equals(e.getValue(),
                            readFolder(folder).get(e.getKey())),
                    "content differs for " + e.getKey());
        }
    }

    private static Map<String, byte[]> readFolder(File root) throws IOException {
        Map<String, byte[]> out = new java.util.LinkedHashMap<>();
        try (var paths = Files.walk(root.toPath())) {
            for (Path p : paths.filter(Files::isRegularFile).toList()) {
                String rel = root.toPath().relativize(p).toString().replace('\\', '/');
                out.put(rel, Files.readAllBytes(p));
            }
        }
        return out;
    }

    @Test
    void newLayoutArmorTexturesOnLatest() throws Exception {
        ServerVersion.Version v = ServerVersion.parse("26.2-R0.1-SNAPSHOT");
        PackGenerator.Context ctx = new PackGenerator.Context(
                "itemmaker", ServerVersion.targetFor(v), v, 16, "test", null, List.of(ARMOR), LOG);
        Map<String, byte[]> entries = readZip(PackGenerator.generate(ctx));
        assertPng(entries, "assets/itemmaker/textures/entity/equipment/humanoid/verdant_helmet.png");
        assertPng(entries, "assets/itemmaker/textures/entity/equipment/humanoid_leggings/verdant_helmet.png");
        assertJson(entries, "assets/itemmaker/equipment/verdant_helmet.json");
    }

    private static void assertEntry(Map<String, byte[]> entries, String path) {
        assertNotNull(entries.get(path), "missing entry " + path);
    }

    private static void assertJson(Map<String, byte[]> entries, String path) {
        byte[] data = entries.get(path);
        assertNotNull(data, "missing entry " + path);
        JsonParser.parseString(new String(data)); // must parse
    }

    private static void assertPng(Map<String, byte[]> entries, String path) throws IOException {
        byte[] data = entries.get(path);
        assertNotNull(data, "missing entry " + path);
        assertNotNull(ImageIO.read(new ByteArrayInputStream(data)), "entry is not a valid PNG: " + path);
    }

    private static Map<String, byte[]> readZip(byte[] zip) throws IOException {
        java.util.Map<String, byte[]> out = new java.util.LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                out.put(entry.getName(), in.readAllBytes());
            }
        }
        return out;
    }
}
