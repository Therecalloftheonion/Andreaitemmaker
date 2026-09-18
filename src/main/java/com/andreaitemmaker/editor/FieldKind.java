package com.andreaitemmaker.editor;

/**
 * How a content YAML field is presented and edited in the in-game editor.
 *
 * <p>Adding a new kind of field to the editor means adding a value here, a factory in
 * {@link EditorField} and one case in {@link EditorManager#openControl} — nothing else in
 * the editor has to change.
 */
public enum FieldKind {
    /** Free text. */
    STRING,
    /** Free text with '&amp;' color codes, previewed in the GUI. */
    COLORED_STRING,
    /** A YAML string list, edited one line at a time. */
    TEXT_LIST,
    /** Whole number with a range. */
    INT,
    /** Decimal number with a range. */
    DOUBLE,
    /** true/false toggle. */
    BOOLEAN,
    /** One of a fixed set of values. */
    ENUM,
    /** A vanilla item material. */
    MATERIAL,
    /** A vanilla full-block material usable as a custom block's hitbox. */
    BASE_BLOCK,
    /** A Bukkit sound name. */
    SOUND,
    /** A generated texture: pattern/color/color2/outline, a hex color or a PNG. */
    TEXTURE,
    /** A path to a .json/.png inside the plugin's assets folder. */
    ASSET_PATH,
    /** A map of attribute name to number. */
    ATTRIBUTES,
    /** A map of enchantment name to level. */
    ENCHANTMENTS,
    /** The {@code mechanics:} section. */
    MECHANICS
}
