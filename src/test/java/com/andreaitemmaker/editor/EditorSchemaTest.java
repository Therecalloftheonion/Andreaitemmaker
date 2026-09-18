package com.andreaitemmaker.editor;

import com.andreaitemmaker.api.CustomItemType;
import com.andreaitemmaker.config.ContentLoader;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The editor is a frontend for the format {@link ContentLoader} reads. These tests keep the two in
 * step: every documented field of every type must be editable, and every type must live in the
 * folder the loader scans for it.
 */
class EditorSchemaTest {

    private static List<String> paths(CustomItemType type) {
        return EditorSchema.fields(type).stream().map(EditorField::path).toList();
    }

    @Test
    void everyContentTypeIsCreatable() {
        assertEquals(List.of(CustomItemType.ITEM, CustomItemType.WEAPON, CustomItemType.ARMOR,
                CustomItemType.FOOD, CustomItemType.BLOCK, CustomItemType.FURNITURE),
                EditorSchema.CREATABLE);
    }

    @Test
    void foldersMatchTheLoader() {
        assertEquals("items", EditorSchema.folderFor(CustomItemType.ITEM));
        assertEquals("items", EditorSchema.folderFor(CustomItemType.WEAPON));
        assertEquals("items", EditorSchema.folderFor(CustomItemType.ARMOR));
        assertEquals("items", EditorSchema.folderFor(CustomItemType.FOOD));
        assertEquals("blocks", EditorSchema.folderFor(CustomItemType.BLOCK));
        assertEquals("furniture", EditorSchema.folderFor(CustomItemType.FURNITURE));
    }

    @Test
    void sharedFieldsArePresentForEveryType() {
        for (CustomItemType type : EditorSchema.CREATABLE) {
            List<String> paths = paths(type);
            for (String expected : List.of("id", "display-name", "material", "lore",
                    "max-stack-size", "unbreakable", "glow", "custom-model-data",
                    "attributes", "enchantments", "texture", "model", "mechanics")) {
                assertTrue(paths.contains(expected), type + " is missing the field " + expected);
            }
        }
    }

    @Test
    void armorHasTheWornTextureField() {
        assertTrue(paths(CustomItemType.ARMOR).contains("armor-texture"));
        assertFalse(paths(CustomItemType.ITEM).contains("armor-texture"));
    }

    @Test
    void foodFieldsMatchTheLoader() {
        List<String> paths = paths(CustomItemType.FOOD);
        assertTrue(paths.containsAll(List.of("food.hunger", "food.saturation", "food.cooldown")));
        assertTrue(EditorSchema.managedTopLevelKeys(CustomItemType.FOOD).contains("food"));
    }

    @Test
    void blockFieldsMatchTheLoader() {
        List<String> paths = paths(CustomItemType.BLOCK);
        assertTrue(paths.containsAll(List.of("base-block", "drops-item")));
        assertTrue(EditorSchema.field(CustomItemType.BLOCK, "base-block").required());
    }

    @Test
    void furnitureFieldsMatchTheLoader() {
        List<String> paths = paths(CustomItemType.FURNITURE);
        assertTrue(paths.containsAll(List.of("small", "consumable", "drops-item", "offset-y",
                "place-sound", "break-sound")));
    }

    @Test
    void materialIsAlwaysRequired() {
        for (CustomItemType type : EditorSchema.CREATABLE) {
            assertTrue(EditorSchema.field(type, "material").required(), type + " must require a material");
        }
    }

    @Test
    void managedKeysCoverEveryFieldAndIdentity() {
        for (CustomItemType type : EditorSchema.CREATABLE) {
            Set<String> managed = EditorSchema.managedTopLevelKeys(type);
            assertTrue(managed.contains("id") && managed.contains("type"));
            for (EditorField field : EditorSchema.fields(type)) {
                assertTrue(managed.contains(EditorSchema.topLevel(field.path())),
                        field.path() + " is not covered by the managed keys of " + type);
            }
        }
    }

    @Test
    void fieldPathsAreUniqueAndDescribed() {
        for (CustomItemType type : EditorSchema.CREATABLE) {
            Set<String> seen = new HashSet<>();
            for (EditorField field : EditorSchema.fields(type)) {
                assertTrue(seen.add(field.path()), "duplicate field " + field.path() + " on " + type);
                assertFalse(field.label().isBlank(), field.path() + " has no label");
                assertFalse(field.description().isBlank(), field.path() + " has no description");
            }
        }
    }

    @Test
    void numericFieldsHaveSaneRanges() {
        for (CustomItemType type : EditorSchema.CREATABLE) {
            for (EditorField field : EditorSchema.fields(type)) {
                if (field.kind() == FieldKind.INT || field.kind() == FieldKind.DOUBLE) {
                    assertTrue(field.min() < field.max(), field.path() + " has an empty range");
                }
            }
        }
    }

    @Test
    void lookupByIdentifiesFields() {
        assertNotNull(EditorSchema.field(CustomItemType.FOOD, "food.hunger"));
        assertNull(EditorSchema.field(CustomItemType.ITEM, "base-block"));
        assertNull(EditorSchema.field(CustomItemType.ARMOR, "not_a_field"));
    }

    /** The loader helpers the editor relies on must behave the same way the loader's parse does. */
    @Test
    void loaderHelpersUsedByTheEditorAgreeWithTheLoaderRules() {
        assertTrue(ContentLoader.isValidId("magic_sword"));
        assertTrue(ContentLoader.isValidId("lightning.sword-2"));
        assertFalse(ContentLoader.isValidId("Magic Sword"));
        assertFalse(ContentLoader.isValidId(""));

        assertEquals(CustomItemType.WEAPON, ContentLoader.parseTypeOrNull("weapon"));
        assertEquals(CustomItemType.ARMOR, ContentLoader.parseTypeOrNull(" ARMOR "));
        assertNull(ContentLoader.parseTypeOrNull("wizard"));
        assertNull(ContentLoader.parseTypeOrNull(null));

        assertEquals(1, ContentLoader.defaultMaxStack(CustomItemType.WEAPON));
        assertEquals(1, ContentLoader.defaultMaxStack(CustomItemType.ARMOR));
        assertEquals(1, ContentLoader.defaultMaxStack(CustomItemType.BLOCK));
        assertEquals(1, ContentLoader.defaultMaxStack(CustomItemType.FURNITURE));
        assertEquals(64, ContentLoader.defaultMaxStack(CustomItemType.ITEM));
        assertEquals(64, ContentLoader.defaultMaxStack(CustomItemType.FOOD));

        // Material.isBlock()/isOccluding()/isItem() consult the live registry, so the block-base
        // rule itself can only be exercised on a running server (see docs/manual-testing.md).
        // The field-less part of the rule is still checkable here.
        assertNotNull(ContentLoader.blockBaseProblem(null));
        assertEquals("missing required field 'base-block'", ContentLoader.blockBaseProblem(null));
    }
}
