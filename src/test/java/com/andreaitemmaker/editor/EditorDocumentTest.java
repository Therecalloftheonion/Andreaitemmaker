package com.andreaitemmaker.editor;

import com.andreaitemmaker.api.CustomItemType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The editor must never damage a file it does not fully understand. These tests pin down the two
 * guarantees the working copy relies on: unknown keys/comments/order survive a round trip, and the
 * change tracking is exact.
 */
class EditorDocumentTest {

    @TempDir
    Path tempDir;

    private File write(String name, String content) throws IOException {
        File file = new File(tempDir.toFile(), name);
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void unknownKeysCommentsAndOrderSurviveAnEdit() throws IOException {
        File file = write("magic_sword.yml", """
                # header comment
                id: magic_sword
                material: DIAMOND_SWORD
                custom-feature:
                  something: true
                future-field: 123
                """);

        EditorDocument doc = EditorDocument.load(file);
        assertFalse(doc.isDirty(), "a freshly loaded document must not look dirty");

        doc.set("display-name", "&bMagic Sword");
        assertTrue(doc.isDirty());

        String yaml = doc.toYaml();
        assertTrue(yaml.contains("# header comment"), "comments must be preserved: " + yaml);
        assertTrue(yaml.contains("custom-feature"), "unknown keys must be preserved: " + yaml);
        assertTrue(yaml.contains("future-field"), "unknown keys must be preserved: " + yaml);
        assertTrue(yaml.contains("something: true"), "unknown values must be preserved: " + yaml);
        assertTrue(yaml.contains("123"), "unknown values must be preserved: " + yaml);
        assertTrue(yaml.indexOf("id:") < yaml.indexOf("material:"), "key order must be preserved");
        assertTrue(yaml.indexOf("material:") < yaml.indexOf("custom-feature:"), "key order must be preserved");
    }

    @Test
    void unknownTopLevelKeysAreReported() throws IOException {
        File file = write("magic_sword.yml", """
                id: magic_sword
                material: DIAMOND_SWORD
                custom-feature:
                  something: true
                future-field: 123
                """);
        EditorDocument doc = EditorDocument.load(file);
        assertEquals(Set.of("custom-feature", "future-field"),
                doc.unknownTopLevelKeys(CustomItemType.ITEM));
    }

    @Test
    void managedKeysAreNotReportedAsUnknown() throws IOException {
        File file = write("food.yml", """
                id: apple_pie
                type: FOOD
                material: APPLE
                display-name: "&ePie"
                lore:
                  - "&7Tasty"
                food:
                  hunger: 6
                mechanics:
                  heal:
                    amount: 2
                """);
        EditorDocument doc = EditorDocument.load(file);
        assertTrue(doc.unknownTopLevelKeys(CustomItemType.FOOD).isEmpty(),
                "every key of a well formed entry must be recognised");
    }

    @Test
    void markSavedClearsTheDirtyFlag() throws IOException {
        File file = write("sword.yml", "id: sword\nmaterial: IRON_SWORD\n");
        EditorDocument doc = EditorDocument.load(file);
        doc.set("display-name", "&bSword");
        assertTrue(doc.isDirty());
        doc.markSaved();
        assertFalse(doc.isDirty());
        doc.set("glow", true);
        assertTrue(doc.isDirty());
    }

    @Test
    void removeDeletesTheKey() throws IOException {
        File file = write("sword.yml", "id: sword\nmaterial: IRON_SWORD\nglow: true\n");
        EditorDocument doc = EditorDocument.load(file);
        doc.remove("glow");
        assertFalse(doc.contains("glow"));
        assertTrue(doc.isDirty());
    }

    @Test
    void idFallsBackToTheFileName() throws IOException {
        File file = write("storm_blade.yml", "material: DIAMOND_SWORD\n");
        EditorDocument doc = EditorDocument.load(file);
        assertEquals("storm_blade", doc.id());
    }

    @Test
    void createdDocumentHasTheFieldsTheLoaderNeeds() {
        File file = new File(tempDir.toFile(), "new_sword.yml");
        EditorDocument doc = EditorDocument.create(file, CustomItemType.WEAPON, "new_sword");

        assertTrue(doc.isNew());
        assertTrue(doc.isDirty(), "an unwritten document is dirty by definition");
        assertEquals("new_sword", doc.id());
        assertEquals(CustomItemType.WEAPON, doc.declaredType());
        assertEquals("IRON_SWORD", doc.getString("material"));
        assertEquals("&fNew Sword", doc.getString("display-name"));
    }

    @Test
    void createdBlockGetsABaseBlock() {
        File file = new File(tempDir.toFile(), "new_block.yml");
        EditorDocument doc = EditorDocument.create(file, CustomItemType.BLOCK, "new_block");
        assertEquals("WHITE_WOOL", doc.getString("base-block"));
    }

    @Test
    void idChangeIsPickedUpFromTheExplicitKey() throws IOException {
        File file = write("old_name.yml", "id: old_name\nmaterial: STICK\n");
        EditorDocument doc = EditorDocument.load(file);
        doc.set("id", "new_name");
        doc.retarget(new File(tempDir.toFile(), "new_name.yml"));
        assertEquals("new_name", doc.id());
        assertEquals("new_name.yml", doc.file().getName());
    }

    /** A document must round trip through a re-parse to exactly the same values. */
    @Test
    void yamlRoundTripsThroughReparsing() throws IOException {
        File file = write("lamp.yml", """
                material: STICK
                display-name: "&eWarm Lamp"
                small: true
                offset-y: 0.5
                texture:
                  pattern: gradient
                  color: "#fde047"
                """);
        EditorDocument doc = EditorDocument.load(file);
        doc.set("consumable", false);

        YamlConfiguration reparsed = YamlConfiguration.loadConfiguration(
                new java.io.StringReader(doc.toYaml()));
        assertEquals("STICK", reparsed.getString("material"));
        assertEquals(true, reparsed.getBoolean("small"));
        assertEquals(0.5, reparsed.getDouble("offset-y"));
        assertEquals(false, reparsed.getBoolean("consumable"));
        assertEquals("gradient", reparsed.getString("texture.pattern"));
        assertEquals("#fde047", reparsed.getString("texture.color"));
    }
}
