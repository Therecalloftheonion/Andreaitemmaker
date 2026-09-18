package com.andreaitemmaker.editor;

import com.andreaitemmaker.api.CustomItemType;
import org.bukkit.Material;

import java.io.File;

/**
 * One content entry found on disk (used by the editor's content lists and its cross-entry
 * validation).
 *
 * <p>Every field is read once while the index is built, so opening a category or validating a
 * field never re-reads the whole content folder.
 *
 * @param id              content id
 * @param type            resolved content type (from the folder, or the file's {@code type:} key)
 * @param file            the YAML file backing it
 * @param material        the entry's base material, or null when it is missing/invalid
 * @param displayName     the raw display name, or null when not set
 * @param baseBlock       for custom blocks: the vanilla block backing it, or null
 * @param customModelData the explicit custom-model-data value, or null when automatic
 */
public record EditorEntry(String id, CustomItemType type, File file, Material material,
                          String displayName, Material baseBlock, Integer customModelData) {

    /** Case-insensitive match against the id or display name. */
    public boolean matches(String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        String q = query.toLowerCase();
        if (id.toLowerCase().contains(q)) {
            return true;
        }
        return displayName != null && displayName.toLowerCase().contains(q);
    }
}
