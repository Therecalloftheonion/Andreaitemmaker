package com.andreaitemmaker.editor;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the nested editing primitives.
 *
 * <p>They exist because of a non-obvious Bukkit behaviour: {@code set(path, map)} serialises
 * correctly but does <em>not</em> create a traversable section, so {@code get(path + ".child")}
 * would then return null. {@link EditorNode.SectionNode} therefore writes nested maps as real
 * sections — these tests fail if that ever regresses.
 */
class EditorNodeTest {

    @TempDir
    Path tempDir;

    private EditorDocument document(String name, String content) throws IOException {
        File file = new File(tempDir.toFile(), name);
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        return EditorDocument.load(file);
    }

    @Test
    void nestedMapsBecomeTraversableSections() throws IOException {
        EditorDocument doc = document("sword.yml", "id: sword\nmaterial: IRON_SWORD\n");
        EditorNode node = new EditorNode.SectionNode(doc, "mechanics");

        Map<String, Object> heal = new LinkedHashMap<>();
        heal.put("amount", 5);
        heal.put("cooldown", 3);
        node.put("heal", heal);

        assertEquals(List.of("heal"), doc.keys("mechanics"));
        assertEquals(List.of("amount", "cooldown"), doc.keys("mechanics.heal"));
        assertEquals(5, doc.get("mechanics.heal.amount"));
        assertEquals(3, doc.get("mechanics.heal.cooldown"));
        assertTrue(doc.isSection("mechanics.heal"));
    }

    @Test
    void deeperNestingIsTraversable() throws IOException {
        EditorDocument doc = document("sword.yml", "id: sword\nmaterial: IRON_SWORD\n");
        EditorNode mechanics = new EditorNode.SectionNode(doc, "mechanics");

        EditorNode armorEffects = mechanics.child("armor-effects");
        armorEffects.put("power", 2);

        assertEquals(List.of("power"), doc.keys("mechanics.armor-effects"));
        assertEquals(2, doc.get("mechanics.armor-effects.power"));
    }

    @Test
    void entriesReadSectionsAsPlainMaps() throws IOException {
        EditorDocument doc = document("helmet.yml", """
                id: helmet
                material: IRON_HELMET
                mechanics:
                  heal:
                    amount: 4
                """);
        Map<String, Object> entries = new EditorNode.SectionNode(doc, "mechanics").entries();
        assertEquals(List.of("heal"), List.copyOf(entries.keySet()));
        assertTrue(entries.get("heal") instanceof Map<?, ?>,
                "a section must be readable as a plain map");
    }

    @Test
    void listsOfMapsSurviveASaveAndReparse() throws IOException {
        EditorDocument doc = document("helmet.yml", "id: helmet\nmaterial: IRON_HELMET\n");
        EditorNode node = new EditorNode.SectionNode(doc, "mechanics").child("armor-effects");

        Map<String, Object> effect = new LinkedHashMap<>();
        effect.put("type", "NIGHT_VISION");
        effect.put("duration", 5);
        node.put("effects", List.of(effect));

        YamlConfiguration reparsed = YamlConfiguration.loadConfiguration(new StringReader(doc.toYaml()));
        // Dotted list indices are not resolvable, so the list is read as a list (which is exactly
        // how the content loader reads mechanic configs too).
        List<?> effects = reparsed.getConfigurationSection("mechanics.armor-effects").getList("effects");
        assertEquals(1, effects.size());
        assertTrue(effects.get(0) instanceof Map<?, ?>, "a list element must survive as a map");
        assertEquals("NIGHT_VISION", ((Map<?, ?>) effects.get(0)).get("type"));
        assertEquals(5, ((Map<?, ?>) effects.get(0)).get("duration"));
    }

    @Test
    void removingKeysKeepsTheRest() throws IOException {
        EditorDocument doc = document("helmet.yml", """
                id: helmet
                material: IRON_HELMET
                mechanics:
                  heal:
                    amount: 4
                    cooldown: 5
                """);
        EditorNode node = new EditorNode.SectionNode(doc, "mechanics.heal");
        node.remove("cooldown");

        assertFalse(doc.contains("mechanics.heal.cooldown"));
        assertEquals(4, doc.get("mechanics.heal.amount"));
    }

    @Test
    void mapNodeWritesBackThroughItsCallback() {
        Map<String, Object> backing = new LinkedHashMap<>();
        List<String> writes = new java.util.ArrayList<>();
        EditorNode node = new EditorNode.MapNode(backing, m -> writes.add(String.valueOf(m.keySet())));

        node.put("power", 1.5);
        assertEquals(1.5, backing.get("power"));
        assertEquals(1, writes.size());

        EditorNode nested = node.child("inner");
        nested.put("flag", true);
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) backing.get("inner");
        assertEquals(true, inner.get("flag"));
        assertEquals(2, writes.size());
    }

    @Test
    void removingFromAMapNodeNotifies() {
        Map<String, Object> backing = new LinkedHashMap<>();
        backing.put("a", 1);
        List<Integer> notifications = new java.util.ArrayList<>();
        EditorNode node = new EditorNode.MapNode(backing, m -> notifications.add(m.size()));

        node.remove("a");
        assertTrue(backing.isEmpty());
        assertEquals(List.of(0), notifications);
    }
}
