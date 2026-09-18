package com.andreaitemmaker.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
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
 * Undo history.
 *
 * <p>An admin must be able to step back further than one revert, and one undo step must correspond
 * to one thing they did — not to the several internal writes a single field change can produce.
 */
class EditorDocumentUndoTest {

    @TempDir
    Path tempDir;

    private File file;

    private EditorDocument document(String content) throws IOException {
        file = new File(tempDir.toFile(), "storm_blade.yml");
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        return EditorDocument.load(file);
    }

    /** Applies a change the way the manager does: one bracketed user action. */
    private static void action(Runnable change, EditorDocument doc) {
        doc.beginAction();
        try {
            change.run();
        } finally {
            doc.endAction();
        }
    }

    @Test
    void nothingToUndoOnAFreshDocument() throws IOException {
        EditorDocument doc = document("id: storm_blade\nmaterial: IRON_SWORD\n");
        assertFalse(doc.canUndo());
        assertFalse(doc.canRedo());
        assertFalse(doc.undo());
        assertFalse(doc.redo());
        assertEquals(0, doc.undoDepth());
    }

    @Test
    void eachEditIsOneStepAndCanBeSteppedBackStepByStep() throws IOException {
        EditorDocument doc = document("id: storm_blade\nmaterial: IRON_SWORD\n");

        action(() -> doc.set("display-name", "&bStorm Blade"), doc);
        action(() -> doc.set("glow", true), doc);
        action(() -> doc.set("unbreakable", true), doc);

        assertEquals(3, doc.undoDepth());

        assertTrue(doc.undo());
        assertFalse(doc.contains("unbreakable"));
        assertTrue(doc.contains("glow"));

        assertTrue(doc.undo());
        assertFalse(doc.contains("glow"));
        assertTrue(doc.contains("display-name"));

        assertTrue(doc.undo());
        assertFalse(doc.contains("display-name"));
        assertFalse(doc.isDirty(), "back at the original file state, so nothing is dirty");
        assertFalse(doc.canUndo());
    }

    @Test
    void oneActionWithSeveralInternalWritesIsASingleStep() throws IOException {
        EditorDocument doc = document("id: storm_blade\nmaterial: IRON_SWORD\n");

        // A nested map write produces several internal set calls (section + each key).
        action(() -> {
            EditorNode node = new EditorNode.SectionNode(doc, "mechanics");
            Map<String, Object> heal = new LinkedHashMap<>();
            heal.put("amount", 5);
            heal.put("cooldown", 3);
            node.put("heal", heal);
        }, doc);

        assertEquals(1, doc.undoDepth(), "a single field change must be a single undo step");
        assertTrue(doc.undo());
        assertFalse(doc.contains("mechanics"), "the whole node is undone in one step");
    }

    @Test
    void anActionThatChangesNothingIsNotRecorded() throws IOException {
        EditorDocument doc = document("id: storm_blade\nmaterial: IRON_SWORD\n");
        action(() -> doc.set("glow", true), doc);
        assertEquals(1, doc.undoDepth());

        // Pure navigation: reads values but writes nothing.
        action(() -> {
            doc.getString("material");
            doc.keys("");
            doc.unknownTopLevelKeys(com.andreaitemmaker.api.CustomItemType.WEAPON);
        }, doc);

        assertEquals(1, doc.undoDepth(), "a no-op action must not fill the history");
    }

    @Test
    void redoReappliesAnUndoneEdit() throws IOException {
        EditorDocument doc = document("id: storm_blade\nmaterial: IRON_SWORD\n");
        action(() -> doc.set("display-name", "&bStorm Blade"), doc);
        action(() -> doc.set("glow", true), doc);

        assertTrue(doc.undo());
        assertFalse(doc.contains("glow"));
        assertEquals(1, doc.redoDepth());

        assertTrue(doc.redo());
        assertEquals(true, doc.getBoolean("glow", false));
        assertEquals(0, doc.redoDepth());
        assertEquals(2, doc.undoDepth());
    }

    @Test
    void undoThenNewEditClearsTheRedoStack() throws IOException {
        EditorDocument doc = document("id: storm_blade\nmaterial: IRON_SWORD\n");
        action(() -> doc.set("glow", true), doc);
        assertTrue(doc.undo());
        assertTrue(doc.canRedo());

        action(() -> doc.set("unbreakable", true), doc);
        assertFalse(doc.canRedo(), "a new edit makes the undone branch unreachable");
        assertEquals(true, doc.getBoolean("unbreakable", false));
    }

    @Test
    void undoSurvivesASaveAndMakesTheDocumentDirtyAgain() throws IOException {
        EditorDocument doc = document("id: storm_blade\nmaterial: IRON_SWORD\n");
        action(() -> doc.set("glow", true), doc);
        doc.markSaved();
        assertFalse(doc.isDirty());
        assertTrue(doc.canUndo(), "saving must not wipe the history");

        assertTrue(doc.undo());
        assertFalse(doc.contains("glow"));
        assertTrue(doc.isDirty(), "undoing a save is a change again");
    }

    @Test
    void historyIsBounded() throws IOException {
        EditorDocument doc = document("id: storm_blade\nmaterial: IRON_SWORD\n");
        for (int i = 0; i < EditorDocument.MAX_HISTORY + 20; i++) {
            int value = i;
            action(() -> doc.set("custom-model-data", value), doc);
        }
        assertEquals(EditorDocument.MAX_HISTORY, doc.undoDepth(),
                "the history must stay bounded per session");
    }

    @Test
    void undoRestoresUnknownKeysCommentsAndOrder() throws IOException {
        EditorDocument doc = document("""
                # keep me
                id: storm_blade
                material: IRON_SWORD
                custom-feature:
                  something: true
                future-field: 123
                """);
        action(() -> doc.set("display-name", "&bChanged"), doc);
        assertTrue(doc.undo());

        String yaml = doc.toYaml();
        assertTrue(yaml.contains("# keep me"), yaml);
        assertTrue(yaml.contains("custom-feature"), yaml);
        assertTrue(yaml.contains("something: true"), yaml);
        assertTrue(yaml.contains("future-field"), yaml);
        assertFalse(yaml.contains("display-name"), yaml);
    }

    @Test
    void revertingReloadsTheFileAndIsItselfUndoable() throws IOException {
        EditorDocument doc = document("id: storm_blade\nmaterial: IRON_SWORD\n");
        action(() -> doc.set("display-name", "&bUnsaved"), doc);
        assertEquals(1, doc.undoDepth());

        assertTrue(doc.reloadFromDisk(), "the file still exists, so the revert must succeed");
        assertFalse(doc.contains("display-name"), "the revert mirrors the file on disk");
        assertFalse(doc.isDirty(), "after a revert the working copy matches the file");
        assertEquals(2, doc.undoDepth(), "the revert itself must be one undoable step");

        assertTrue(doc.undo());
        assertEquals("&bUnsaved", doc.getString("display-name"),
                "undoing the revert brings the unsaved edit back");
        assertTrue(doc.isDirty());
    }

    @Test
    void revertFailsWhenTheFileIsGone() throws IOException {
        EditorDocument doc = document("id: storm_blade\nmaterial: IRON_SWORD\n");
        action(() -> doc.set("glow", true), doc);
        assertTrue(file.delete());
        assertFalse(doc.reloadFromDisk(), "a missing file must be reported, not guessed around");
        assertTrue(doc.getBoolean("glow", false), "the working copy is untouched by a failed revert");
        assertEquals(1, doc.undoDepth());
    }

    @Test
    void nestedActionBracketsStillProduceOneStep() throws IOException {
        EditorDocument doc = document("id: storm_blade\nmaterial: IRON_SWORD\n");

        // A handler that (for whatever reason) brackets its own work must not split the outer step
        // or close the outer action early.
        doc.beginAction();
        doc.set("display-name", "&bOuter");
        doc.beginAction();
        doc.set("glow", true);
        doc.endAction();
        doc.set("unbreakable", true);
        doc.endAction();

        assertEquals(1, doc.undoDepth());
        assertTrue(doc.undo());
        assertFalse(doc.contains("display-name"));
        assertFalse(doc.contains("glow"));
        assertFalse(doc.contains("unbreakable"));
    }

    @Test
    void revertingAnUnchangedDocumentAddsNoStep() throws IOException {
        EditorDocument doc = document("id: storm_blade\nmaterial: IRON_SWORD\n");
        action(() -> doc.set("glow", true), doc);

        assertTrue(doc.reloadFromDisk());
        assertEquals(2, doc.undoDepth(), "the edit and the revert are both undoable");

        // Nothing left to revert: a repeated revert must not pile up empty history entries.
        assertTrue(doc.reloadFromDisk());
        assertEquals(2, doc.undoDepth());
        assertFalse(doc.isDirty());

        // Undo walks back through the revert to the unsaved edit.
        assertTrue(doc.undo());
        assertTrue(doc.getBoolean("glow", false));
    }

    @Test
    void historyIsPerDocument() throws IOException {
        EditorDocument first = document("id: storm_blade\nmaterial: IRON_SWORD\n");
        action(() -> first.set("glow", true), first);

        File other = new File(tempDir.toFile(), "lamp.yml");
        Files.writeString(other.toPath(), "id: lamp\nmaterial: STICK\n", StandardCharsets.UTF_8);
        EditorDocument second = EditorDocument.load(other);

        assertEquals(1, first.undoDepth());
        assertEquals(0, second.undoDepth(), "sessions must never share history");
        assertFalse(second.canUndo());
    }

    @Test
    void undoingEveryStepReturnsTheDocumentToItsOriginalText() throws IOException {
        EditorDocument doc = document("id: storm_blade\nmaterial: IRON_SWORD\nlore:\n  - \"&7One\"\n");
        String original = doc.toYaml();

        action(() -> doc.set("display-name", "&bOne"), doc);
        action(() -> doc.set("glow", true), doc);
        action(() -> doc.set("lore", List.of("&7Two", "&7Three")), doc);

        while (doc.undo()) {
            // step all the way back
        }
        assertEquals(original, doc.toYaml(), "the full history must lead back to the original file");
        assertFalse(doc.isDirty());
    }
}
