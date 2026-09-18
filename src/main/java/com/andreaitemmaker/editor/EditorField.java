package com.andreaitemmaker.editor;

import java.util.List;

/**
 * One editable field of a content YAML file.
 *
 * @param path        YAML path, e.g. {@code display-name} or {@code food.hunger}
 * @param label       human readable name shown in the GUI
 * @param kind        which control edits it
 * @param description short help line shown as lore
 * @param min         lower bound for numeric kinds
 * @param max         upper bound for numeric kinds
 * @param values      allowed values for {@link FieldKind#ENUM}
 * @param required    whether the loader refuses the file without it
 */
public record EditorField(String path, String label, FieldKind kind, String description,
                          double min, double max, List<String> values, boolean required) {

    public static EditorField text(String path, String label, String description) {
        return new EditorField(path, label, FieldKind.STRING, description, 0, 0, List.of(), false);
    }

    public static EditorField colored(String path, String label, String description) {
        return new EditorField(path, label, FieldKind.COLORED_STRING, description, 0, 0, List.of(), false);
    }

    public static EditorField strings(String path, String label, String description) {
        return new EditorField(path, label, FieldKind.TEXT_LIST, description, 0, 0, List.of(), false);
    }

    public static EditorField integer(String path, String label, int min, int max, String description) {
        return new EditorField(path, label, FieldKind.INT, description, min, max, List.of(), false);
    }

    public static EditorField decimal(String path, String label, double min, double max, String description) {
        return new EditorField(path, label, FieldKind.DOUBLE, description, min, max, List.of(), false);
    }

    public static EditorField toggle(String path, String label, String description) {
        return new EditorField(path, label, FieldKind.BOOLEAN, description, 0, 0, List.of(), false);
    }

    public static EditorField choice(String path, String label, List<String> values, String description) {
        return new EditorField(path, label, FieldKind.ENUM, description, 0, 0, List.copyOf(values), false);
    }

    public static EditorField material(String path, String label, String description) {
        return new EditorField(path, label, FieldKind.MATERIAL, description, 0, 0, List.of(), true);
    }

    public static EditorField baseBlock(String path, String label, String description) {
        return new EditorField(path, label, FieldKind.BASE_BLOCK, description, 0, 0, List.of(), true);
    }

    public static EditorField sound(String path, String label, String description) {
        return new EditorField(path, label, FieldKind.SOUND, description, 0, 0, List.of(), false);
    }

    public static EditorField texture(String path, String label, String description) {
        return new EditorField(path, label, FieldKind.TEXTURE, description, 0, 0, List.of(), false);
    }

    public static EditorField assetPath(String path, String label, String description) {
        return new EditorField(path, label, FieldKind.ASSET_PATH, description, 0, 0, List.of(), false);
    }

    public static EditorField attributes(String path, String label, String description) {
        return new EditorField(path, label, FieldKind.ATTRIBUTES, description, 0, 0, List.of(), false);
    }

    public static EditorField enchantments(String path, String label, String description) {
        return new EditorField(path, label, FieldKind.ENCHANTMENTS, description, 0, 0, List.of(), false);
    }

    public static EditorField mechanics(String path, String label, String description) {
        return new EditorField(path, label, FieldKind.MECHANICS, description, 0, 0, List.of(), false);
    }

    public EditorField required(boolean value) {
        return new EditorField(path, label, kind, description, min, max, values, value);
    }
}
