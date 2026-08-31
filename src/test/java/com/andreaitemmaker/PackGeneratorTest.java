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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackGeneratorTest {

    private static final Logger LOG = Logger.getLogger("PackGeneratorTest");

    private static final CustomItem SWORD = new CustomItem(
            "storm_blade", CustomItemType.WEAPON, Material.DIAMOND_SWORD, "Storm Blade", List.of(),
            1001, 1, Map.of(), Map.of(), true, true, "gradient|#3f9bff|#0f2a6b|true", null, null, Map.of());

    private static final CustomItem ARMOR = new CustomItem(
            "verdant_helmet", CustomItemType.ARMOR, Material.DIAMOND_HELMET, "Verdant Helmet", List.of(),
            1002, 1, Map.of(), Map.of(), false, false, "#4ade80", null, null, Map.of());

    private static final CustomBlock BLOCK = new CustomBlock(
            "amethyst_crate", Material.STICK, "Amethyst Crate", List.of(),
            1003, 1, Map.of(), Map.of(), false, false, "checker|#c084fc|#581c87|true", null, null, Map.of(),
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
                "Spada Fulmini", List.of(), 2001, 1, Map.of(), Map.of(), false, false, null, null,
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

    @Test
    void helmetKeepsHumanoidLayer() throws Exception {
        // Worn armor renders exclusively from the equipment asset's 2D layers (verified in the
        // 1.21.5 client renderer); both layer types must always be present, even for 3D-model helmets.
        CustomItem helmet3d = new CustomItem("eternal_helmet", CustomItemType.ARMOR, Material.DIAMOND_HELMET,
                "Eternal Helmet", List.of(), 1004, 1, Map.of(), Map.of(), true, false, null, null,
                "assets/models/eternal_helmet.json", Map.of());
        ServerVersion.Version v = ServerVersion.parse("1.21.5-R0.1-SNAPSHOT");
        PackGenerator.Context ctx = new PackGenerator.Context(
                "itemmaker", ServerVersion.targetFor(v), v, 16, "test", null, List.of(helmet3d), LOG);
        Map<String, byte[]> entries = readZip(PackGenerator.generate(ctx));

        String asset = new String(entries.get("assets/itemmaker/equipment/eternal_helmet.json"));
        assertTrue(asset.contains("\"humanoid\":[{\"texture\":\"itemmaker:eternal_helmet\"}]"),
                "humanoid layer must be present: " + asset);
        assertTrue(asset.contains("humanoid_leggings"), "leggings layer must be present: " + asset);
        assertPng(entries, "assets/itemmaker/textures/entity/equipment/humanoid/eternal_helmet.png");
        assertPng(entries, "assets/itemmaker/textures/entity/equipment/humanoid_leggings/eternal_helmet.png");
    }

    @Test
    void helmetLayerTextureComesFromModel(@TempDir Path tempDir) throws Exception {
        // A 3D-model helmet with no texture spec uses the model's own PNG for the worn
        // layer only when it is already a flat 64x32 armor layer (not a square UV atlas).
        Files.createDirectories(tempDir.resolve("assets/textures"));
        Files.createDirectories(tempDir.resolve("assets/models"));
        byte[] helmetPng = com.andreaitemmaker.util.PngWriter.write(64, 32, new int[64 * 32]);
        Files.write(tempDir.resolve("assets/textures/eternal_helmet.png"), helmetPng);
        Files.writeString(tempDir.resolve("assets/models/eternal_helmet.json"),
                "{\"textures\":{\"layer0\":\"itemmaker:item/eternal_helmet\"},"
                        + "\"elements\":[{\"from\":[4,4,4],\"to\":[12,12,12]}]}");
        CustomItem helmet3d = new CustomItem("eternal_helmet", CustomItemType.ARMOR, Material.DIAMOND_HELMET,
                "Eternal Helmet", List.of(), 1004, 1, Map.of(), Map.of(), true, false, null, null,
                "assets/models/eternal_helmet.json", Map.of());
        ServerVersion.Version v = ServerVersion.parse("1.21.5-R0.1-SNAPSHOT");
        PackGenerator.Context ctx = new PackGenerator.Context(
                "itemmaker", ServerVersion.targetFor(v), v, 16, "test", tempDir.toFile(),
                List.of(helmet3d), LOG);
        Map<String, byte[]> entries = PackGenerator.buildEntries(ctx);
        byte[] layer = entries.get("assets/itemmaker/textures/entity/equipment/humanoid/eternal_helmet.png");
        assertNotNull(layer, "worn layer texture must exist");
        assertPngSize(layer, 64, 32);
    }

    @Test
    void squareModelAtlasNeverUsedForWornLayer(@TempDir Path tempDir) throws Exception {
        // A square UV atlas (64x64, typical Blockbench export) must NOT be squashed into the
        // worn layer: the generated layer is used instead of producing garbage.
        Files.createDirectories(tempDir.resolve("assets/textures"));
        Files.createDirectories(tempDir.resolve("assets/models"));
        byte[] atlas = com.andreaitemmaker.util.PngWriter.write(64, 64, new int[64 * 64]);
        Files.write(tempDir.resolve("assets/textures/eternal_helmet.png"), atlas);
        Files.writeString(tempDir.resolve("assets/models/eternal_helmet.json"),
                "{\"textures\":{\"layer0\":\"itemmaker:item/eternal_helmet\"},"
                        + "\"elements\":[{\"from\":[4,4,4],\"to\":[12,12,12]}]}");
        CustomItem helmet3d = new CustomItem("eternal_helmet", CustomItemType.ARMOR, Material.DIAMOND_HELMET,
                "Eternal Helmet", List.of(), 1004, 1, Map.of(), Map.of(), true, false, null, null,
                "assets/models/eternal_helmet.json", Map.of());
        ServerVersion.Version v = ServerVersion.parse("1.21.5-R0.1-SNAPSHOT");
        PackGenerator.Context ctx = new PackGenerator.Context(
                "itemmaker", ServerVersion.targetFor(v), v, 16, "test", tempDir.toFile(),
                List.of(helmet3d), LOG);
        Map<String, byte[]> entries = PackGenerator.buildEntries(ctx);
        byte[] layer = entries.get("assets/itemmaker/textures/entity/equipment/humanoid/eternal_helmet.png");
        assertNotNull(layer, "worn layer texture must exist");
        assertPngSize(layer, 64, 32);
    }

    @Test
    void armorTextureConfigControlsWornLayer(@TempDir Path tempDir) throws Exception {
        // armor-texture: is the dedicated worn-layer texture; the file is used as the layer.
        Files.createDirectories(tempDir.resolve("assets/textures"));
        byte[] worn = com.andreaitemmaker.util.PngWriter.write(64, 32, new int[64 * 32]);
        Files.write(tempDir.resolve("assets/textures/custom_layer.png"), worn);
        CustomItem armor = new CustomItem("eternal_helmet", CustomItemType.ARMOR, Material.DIAMOND_HELMET,
                "Eternal Helmet", List.of(), 1004, 1, Map.of(), Map.of(), true, false, null,
                "assets/textures/custom_layer.png", null, Map.of());
        ServerVersion.Version v = ServerVersion.parse("1.21.5-R0.1-SNAPSHOT");
        PackGenerator.Context ctx = new PackGenerator.Context(
                "itemmaker", ServerVersion.targetFor(v), v, 16, "test", tempDir.toFile(),
                List.of(armor), LOG);
        Map<String, byte[]> entries = PackGenerator.buildEntries(ctx);
        byte[] layer = entries.get("assets/itemmaker/textures/entity/equipment/humanoid/eternal_helmet.png");
        assertNotNull(layer, "worn layer texture must exist");
        assertPngSize(layer, 64, 32);
    }

    @Test
    void setLevelArmorLayerAutoDetected(@TempDir Path tempDir) throws Exception {
        // Drag-and-drop convention: id eternal_helmet with assets/textures/eternal_armor_layer_1.png
        // gets that texture as the worn layer without any armor-texture: config.
        Files.createDirectories(tempDir.resolve("assets/textures"));
        byte[] worn = com.andreaitemmaker.util.PngWriter.write(64, 32, new int[64 * 32]);
        Files.write(tempDir.resolve("assets/textures/eternal_armor_layer_1.png"), worn);
        CustomItem armor = new CustomItem("eternal_helmet", CustomItemType.ARMOR, Material.DIAMOND_HELMET,
                "Eternal Helmet", List.of(), 1004, 1, Map.of(), Map.of(), true, false, null, null,
                null, Map.of());
        ServerVersion.Version v = ServerVersion.parse("1.21.5-R0.1-SNAPSHOT");
        PackGenerator.Context ctx = new PackGenerator.Context(
                "itemmaker", ServerVersion.targetFor(v), v, 16, "test", tempDir.toFile(),
                List.of(armor), LOG);
        Map<String, byte[]> entries = PackGenerator.buildEntries(ctx);
        byte[] layer = entries.get("assets/itemmaker/textures/entity/equipment/humanoid/eternal_helmet.png");
        assertNotNull(layer, "worn layer texture must exist");
        assertPngSize(layer, 64, 32);
    }

    @Test
    void importedBedrockModelIsAutoConverted(@TempDir Path tempDir) throws Exception {
        // Drag-and-drop: a Blockbench Bedrock export dropped into assets/models/ is converted
        // to the Java format when it lands in the pack (even when no item references it).
        Files.createDirectories(tempDir.resolve("assets/models"));
        Files.writeString(tempDir.resolve("assets/models/dropped_sword.json"), """
                {"format_version": "1.9.0", "credit": "Made with Blockbench",
                 "textures": {"0": "itemmaker:item/dropped_sword"},
                 "groups": [],
                 "elements": [
                   {"from": [7, -1, 7], "to": [9, 3, 9]},
                   {"from": [8, 3, 8], "to": [6, 12, 10],
                    "rotation": {"angle": 45, "axis": "y", "origin": [8, 0, 8]}}
                 ]}
                """);
        ServerVersion.Version v = ServerVersion.parse("1.21.5-R0.1-SNAPSHOT");
        PackGenerator.Context ctx = new PackGenerator.Context(
                "itemmaker", ServerVersion.targetFor(v), v, 16, "test", tempDir.toFile(),
                List.of(SWORD), LOG);
        Map<String, byte[]> entries = PackGenerator.buildEntries(ctx);
        String model = new String(entries.get("assets/itemmaker/models/item/dropped_sword.json"));
        assertNotNull(model, "imported model must be in the pack");
        assertFalse(model.contains("format_version"), "Bedrock marker must be stripped: " + model);
        assertFalse(model.contains("\"groups\""), "groups must be stripped: " + model);
        assertTrue(model.contains("layer0"), "texture key must be renamed: " + model);
        // The inverted element (from [8,3,8] -> to [6,12,10]) must be ordered.
        assertTrue(model.contains("\"from\":[6.0,3.0,8.0],\"to\":[8.0,12.0,10.0]"),
                "from/to must be ordered: " + model);
    }

    private static void assertPngSize(byte[] png, int w, int h) throws Exception {
        var img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
        assertNotNull(img, "decodable PNG");
        assertEquals(w, img.getWidth());
        assertEquals(h, img.getHeight());
    }

    @Test
    void equipmentAssetUsesObjectFormLayers() throws Exception {
        ServerVersion.Version v = ServerVersion.parse("1.21.5-R0.1-SNAPSHOT");
        PackGenerator.Context ctx = new PackGenerator.Context(
                "itemmaker", ServerVersion.targetFor(v), v, 16, "test", null, List.of(ARMOR), LOG);
        Map<String, byte[]> entries = readZip(PackGenerator.generate(ctx));
        String asset = new String(entries.get("assets/itemmaker/equipment/verdant_helmet.json"));
        // Object-form layers ({"texture": "ns:id"}), not the legacy string form.
        assertTrue(asset.contains("{\"texture\":\"itemmaker:verdant_helmet\"}"), "object-form layer expected: " + asset);
    }

    @Test
    void armorAssetUnderModelsEquipmentOn1212() throws Exception {
        // 1.21.2-1.21.3 stored equipment models under models/equipment/ (moved to
        // equipment/ in 1.21.4), but already used object-form layers + entity/equipment textures.
        ServerVersion.Version v = ServerVersion.parse("1.21.2-R0.1-SNAPSHOT");
        PackGenerator.Context ctx = new PackGenerator.Context(
                "itemmaker", ServerVersion.targetFor(v), v, 16, "test", null, List.of(ARMOR), LOG);
        Map<String, byte[]> entries = readZip(PackGenerator.generate(ctx));
        String asset = new String(entries.get("assets/itemmaker/models/equipment/verdant_helmet.json"));
        assertTrue(asset.contains("{\"texture\":\"itemmaker:verdant_helmet\"}"), "object-form layer expected: " + asset);
        assertPng(entries, "assets/itemmaker/textures/entity/equipment/humanoid/verdant_helmet.png");
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

    @Test
    void strayModelCannotShadowGeneratedModel(@TempDir Path tempDir) throws Exception {
        // A stray file whose basename matches an item id must NOT overwrite the generated model.
        Path models = tempDir.resolve("assets/models");
        Files.createDirectories(models);
        Files.writeString(models.resolve("storm_blade.json"),
                "{\"parent\":\"minecraft:item/handheld\",\"textures\":{\"layer0\":\"evil:thing\"}}");
        ServerVersion.Version v = ServerVersion.parse("1.21.4-R0.1-SNAPSHOT");
        PackGenerator.Context ctx = new PackGenerator.Context("itemmaker", ServerVersion.targetFor(v), v,
                16, "test", tempDir.toFile(), List.of(SWORD), LOG);
        Map<String, byte[]> entries = PackGenerator.buildEntries(ctx);
        String model = new String(entries.get("assets/itemmaker/models/item/storm_blade.json"));
        assertTrue(model.contains("itemmaker:item/storm_blade"), "generated model must win over stray file");
        assertFalse(model.contains("evil:thing"));
    }

    @Test
    void invalidImportedModelIsSkipped(@TempDir Path tempDir) throws Exception {
        Path models = tempDir.resolve("assets/models");
        Files.createDirectories(models);
        Files.writeString(models.resolve("broken.json"), "{ this is not json");
        ServerVersion.Version v = ServerVersion.parse("1.21.4-R0.1-SNAPSHOT");
        PackGenerator.Context ctx = new PackGenerator.Context("itemmaker", ServerVersion.targetFor(v), v,
                16, "test", tempDir.toFile(), List.of(SWORD), LOG);
        Map<String, byte[]> entries = PackGenerator.buildEntries(ctx);
        assertFalse(entries.containsKey("assets/itemmaker/models/item/broken.json"),
                "invalid JSON must not be injected into the pack");
    }

    @Test
    void escapingModelPathFallsBackToGenerated(@TempDir Path tempDir) throws Exception {
        CustomItem evil = new CustomItem("evil_blade", CustomItemType.WEAPON, Material.DIAMOND_SWORD,
                "Evil", List.of(), 3001, 1, Map.of(), Map.of(), false, false, null, null,
                "../outside.json", Map.of());
        ServerVersion.Version v = ServerVersion.parse("1.21.4-R0.1-SNAPSHOT");
        PackGenerator.Context ctx = new PackGenerator.Context("itemmaker", ServerVersion.targetFor(v), v,
                16, "test", tempDir.toFile(), List.of(evil), LOG);
        Map<String, byte[]> entries = PackGenerator.buildEntries(ctx);
        String model = new String(entries.get("assets/itemmaker/models/item/evil_blade.json"));
        assertTrue(model.contains("itemmaker:item/evil_blade"), "must fall back to a generated model");
    }

    @Test
    void strayTextureCannotShadowItemTexture(@TempDir Path tempDir) throws Exception {
        // Item has an explicit texture; a stray PNG with the item's basename must not
        // overwrite the item's own (scaled) texture entry.
        Path textures = tempDir.resolve("assets/textures");
        Files.createDirectories(textures);
        byte[] stray = new byte[]{1, 2, 3, 4}; // not even a PNG
        Files.write(textures.resolve("storm_blade.png"), stray);
        CustomItem withTexture = new CustomItem("storm_blade", CustomItemType.WEAPON, Material.DIAMOND_SWORD,
                "Storm Blade", List.of(), 1001, 1, Map.of(), Map.of(), false, false,
                "#ff0000", null, null, Map.of());
        ServerVersion.Version v = ServerVersion.parse("1.21.4-R0.1-SNAPSHOT");
        PackGenerator.Context ctx = new PackGenerator.Context("itemmaker", ServerVersion.targetFor(v), v,
                16, "test", tempDir.toFile(), List.of(withTexture), LOG);
        Map<String, byte[]> entries = PackGenerator.buildEntries(ctx);
        byte[] written = entries.get("assets/itemmaker/textures/item/storm_blade.png");
        assertNotNull(written);
        assertTrue(!java.util.Arrays.equals(stray, written), "item texture must win over stray file");
    }

    @Test
    void duplicateImportedTextureCopyIsIdempotent(@TempDir Path tempDir) throws Exception {
        // A stray PNG copied once must not duplicate entries; two items sharing the same
        // imported texture still produce exactly one pack entry.
        Path textures = tempDir.resolve("assets/textures");
        Files.createDirectories(textures);
        byte[] png = com.andreaitemmaker.util.PngWriter.write(16, 16, new int[16 * 16]);
        Files.write(textures.resolve("shared.png"), png);
        CustomItem one = new CustomItem("one", CustomItemType.WEAPON, Material.DIAMOND_SWORD,
                "One", List.of(), 1001, 1, Map.of(), Map.of(), false, false, null, null,
                "assets/models/one.json", Map.of());
        CustomItem two = new CustomItem("two", CustomItemType.WEAPON, Material.DIAMOND_SWORD,
                "Two", List.of(), 1002, 1, Map.of(), Map.of(), false, false, null, null,
                "assets/models/two.json", Map.of());
        ServerVersion.Version v = ServerVersion.parse("1.21.4-R0.1-SNAPSHOT");
        PackGenerator.Context ctx = new PackGenerator.Context("itemmaker", ServerVersion.targetFor(v), v,
                16, "test", tempDir.toFile(), List.of(one, two), LOG);
        Map<String, byte[]> entries = PackGenerator.buildEntries(ctx);
        assertNotNull(entries.get("assets/itemmaker/textures/item/shared.png"));
        // The shared texture appears exactly once.
        long count = entries.keySet().stream()
                .filter(k -> k.equals("assets/itemmaker/textures/item/shared.png")).count();
        assertEquals(1, count);
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
