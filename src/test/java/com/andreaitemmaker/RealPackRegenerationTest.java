package com.andreaitemmaker;

import com.andreaitemmaker.api.CustomItem;
import com.andreaitemmaker.api.CustomItemType;
import com.andreaitemmaker.pack.PackGenerator;
import com.andreaitemmaker.util.ServerVersion;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Diagnostic: loads the REAL server folder (config.yml + items/*.yml) the way the plugin
 * does and regenerates the resource pack with the current code, then rewrites the stale
 * pack/ folder in the server copy so its content matches what the fixed jar produces.
 * The field mapping replicates ContentLoader for the fields that affect pack generation.
 */
public class RealPackRegenerationTest {

    private static final Logger LOG = Logger.getLogger("aitem-diagnostic");
    private static final File DATA = new File("Andreaitemmaker");

    @Test
    void regenerateRealServerPack() throws Exception {
        assertTrue(DATA.isDirectory(), "server folder Andreaitemmaker/ must exist");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new File(DATA, "config.yml"));
        String ns = cfg.getString("namespace", "itemmaker");
        int texSize = cfg.getConfigurationSection("pack").getInt("texture-size", 16);
        String description = cfg.getConfigurationSection("pack").getString("description", "test");

        List<CustomItem> items = new ArrayList<>();
        File itemsDir = new File(DATA, "items");
        File[] files = itemsDir.listFiles((d, n) -> n.endsWith(".yml") || n.endsWith(".yaml"));
        assertNotNull(files, "items/ folder must exist");
        java.util.Arrays.sort(files);
        int nextCmd = cfg.getConfigurationSection("content").getInt("custom-model-data-start", 1000);
        assertTrue(files.length >= 14, "expected the eternal items, got " + files.length);
        for (File f : files) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            String id = y.getString("id", f.getName().substring(0, f.getName().length() - 4));
            CustomItemType type = CustomItemType.valueOf(y.getString("type", "ITEM").trim().toUpperCase(java.util.Locale.ROOT));
            Material mat = Material.matchMaterial(y.getString("material"));
            assertNotNull(mat, f.getName() + ": unknown material");
            String texture = y.getString("texture");
            if (texture == null && y.get("texture") instanceof ConfigurationSection) {
                texture = "gradient|#888888|#444444|true"; // approximate for the example items
            }
            String model = y.getString("model");
            items.add(new CustomItem(id, type, mat, id, List.of(), nextCmd++, 1,
                    Map.of(), Map.of(), y.getBoolean("unbreakable"), y.getBoolean("glow"),
                    texture, y.getString("armor-texture"), model, Map.of()));
        }

        ServerVersion.Version v = ServerVersion.parse("1.21.5-R0.1-SNAPSHOT");
        PackGenerator.Context ctx = new PackGenerator.Context(ns, ServerVersion.targetFor(v), v,
                texSize, description, DATA, items, LOG);
        Map<String, byte[]> entries = PackGenerator.buildEntries(ctx);

        // critical files for the armor/weapon texture question
        assertNotNull(entries.get("assets/" + ns + "/equipment/eternal_helmet.json"), "helmet equipment asset");
        assertNotNull(entries.get("assets/" + ns + "/textures/entity/equipment/humanoid/eternal_helmet.png"),
                "helmet worn layer texture");
        assertNotNull(entries.get("assets/" + ns + "/textures/item/eternal_helmet.png"), "helmet item texture");
        assertNotNull(entries.get("assets/" + ns + "/models/item/eternal_helmet.json"), "helmet model");
        assertNotNull(entries.get("assets/" + ns + "/models/item/eternal_battle_axe.json"), "axe model");
        assertNotNull(entries.get("assets/" + ns + "/textures/item/eternal_battle_axe.png"), "axe texture");
        assertNotNull(entries.get("assets/" + ns + "/textures/entity/equipment/humanoid/eternal_chestplate.png"),
                "chestplate worn layer texture");

        // regenerate the stale pack/ folder in the server copy so it matches the fixed jar
        PackGenerator.writeFolder(entries, new File(DATA, "pack"));
        System.out.println("REGENERATED " + entries.size() + " files into " + DATA + "/pack");
    }
}
