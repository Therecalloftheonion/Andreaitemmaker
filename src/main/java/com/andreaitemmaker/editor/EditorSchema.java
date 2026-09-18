package com.andreaitemmaker.editor;

import com.andreaitemmaker.api.CustomItemType;
import com.andreaitemmaker.config.ContentLoader;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Describes the Andreaitemmaker YAML format to the editor.
 *
 * <p>This is a GUI frontend description of the <em>existing</em> format — the same fields
 * {@link ContentLoader} reads. No second configuration format exists: every value the editor
 * writes is written to the normal per-entry YAML file, and any key that is not listed here is
 * preserved untouched (see {@link EditorDocument#unknownTopLevelKeys}).
 */
public final class EditorSchema {

    private EditorSchema() {
    }

    /** Types the editor can create (mirrors the folders the loader scans). */
    public static final List<CustomItemType> CREATABLE = List.of(
            CustomItemType.ITEM, CustomItemType.WEAPON, CustomItemType.ARMOR,
            CustomItemType.FOOD, CustomItemType.BLOCK, CustomItemType.FURNITURE);

    private static final List<EditorField> SHARED = List.of(
            EditorField.text("id", "Content id",
                            "Unique id used by /aitem give, the API and the resource pack.")
                    .required(true),
            EditorField.colored("display-name", "Display name", "Shown in game. '&' color codes work."),
            EditorField.material("material", "Base material",
                    "The vanilla item this content is built on (diamond_sword, paper, ...)."),
            EditorField.strings("lore", "Lore", "One line per entry. '&' color codes work."),
            EditorField.integer("max-stack-size", "Max stack size", 1, 99,
                    "Vanilla stack size is 64 (1 is enforced for weapons/armor/blocks/furniture)."),
            EditorField.toggle("unbreakable", "Unbreakable", "Never takes durability damage."),
            EditorField.toggle("glow", "Glow", "Adds the enchantment glint without an enchantment."),
            EditorField.integer("custom-model-data", "Custom model data", 0, Integer.MAX_VALUE,
                    "Only used on 1.21.1 and older servers. Leave empty for automatic."),
            EditorField.attributes("attributes", "Attributes",
                    "attack_damage, attack_speed, armor, armor_toughness, max_health, ..."),
            EditorField.enchantments("enchantments", "Enchantments", "Enchantment name to level."),
            EditorField.texture("texture", "Texture",
                    "Generated pattern, a hex color like #4f7cff, or a .png in assets/textures/."),
            EditorField.assetPath("model", "3D model",
                    "Path to a Blockbench .json in assets/models/ (auto-converted to Java format)."),
            EditorField.mechanics("mechanics", "Mechanics",
                    "Built-in behaviors: heal, feed, effect, launch, lightning, ignite, knockback,"
                            + " armor-effects, sound."));

    private static final List<EditorField> ARMOR_FIELDS = List.of(
            EditorField.assetPath("armor-texture", "Worn armor texture",
                    "64x32 humanoid texture for the worn layer (chest/legs/feet)."));

    private static final List<EditorField> FOOD_FIELDS = List.of(
            EditorField.integer("food.hunger", "Hunger restored", 1, 20, "Hunger points per bite."),
            EditorField.decimal("food.saturation", "Saturation restored", 0, 20, "Saturation per bite."),
            EditorField.integer("food.cooldown", "Eat cooldown (s)", 0, 60, "Seconds between bites."));

    private static final List<EditorField> BLOCK_FIELDS = List.of(
            EditorField.baseBlock("base-block", "Base block",
                    "The vanilla block used as the hitbox. One base block backs one custom block.")
                    .required(true),
            EditorField.toggle("drops-item", "Drops the item",
                    "Break the block to get the custom block item back."));

    private static final List<EditorField> FURNITURE_FIELDS = List.of(
            EditorField.toggle("small", "Small model", "Renders the armor stand small (lamps, plates)."),
            EditorField.toggle("consumable", "Consumable",
                    "Placing consumes one item from the player's hand."),
            EditorField.toggle("drops-item", "Drops the item", "Breaking returns the furniture item."),
            EditorField.decimal("offset-y", "Vertical offset", -2, 2,
                    "Places the model higher or lower, in blocks."),
            EditorField.sound("place-sound", "Place sound", "e.g. block.wood.place (empty = default)."),
            EditorField.sound("break-sound", "Break sound", "e.g. block.wood.break (empty = default)."));

    private static final java.util.Map<CustomItemType, List<EditorField>> FIELDS =
            buildFieldTable();

    private static java.util.Map<CustomItemType, List<EditorField>> buildFieldTable() {
        java.util.Map<CustomItemType, List<EditorField>> map = new java.util.LinkedHashMap<>();
        map.put(CustomItemType.ITEM, SHARED);
        map.put(CustomItemType.WEAPON, SHARED);
        map.put(CustomItemType.ARMOR, concat(SHARED, ARMOR_FIELDS));
        map.put(CustomItemType.FOOD, concat(SHARED, FOOD_FIELDS));
        map.put(CustomItemType.BLOCK, concat(SHARED, BLOCK_FIELDS));
        map.put(CustomItemType.FURNITURE, concat(SHARED, FURNITURE_FIELDS));
        return java.util.Map.copyOf(map);
    }

    /** The fields the editor shows for a content type, in display order. */
    public static List<EditorField> fields(CustomItemType type) {
        return FIELDS.getOrDefault(type, SHARED);
    }

    /** Look up a field by YAML path, or null when the type has no such field. */
    public static EditorField field(CustomItemType type, String path) {
        for (EditorField field : fields(type)) {
            if (field.path().equals(path)) {
                return field;
            }
        }
        return null;
    }

    /**
     * The top-level YAML keys the editor owns for a type. Anything else found in a file is
     * unknown content and is never modified (the editor only reports it).
     */
    public static Set<String> managedTopLevelKeys(CustomItemType type) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add("id");
        keys.add("type");
        for (EditorField field : fields(type)) {
            keys.add(topLevel(field.path()));
        }
        return Set.copyOf(keys);
    }

    /** Top-level key of a dotted YAML path ({@code food.hunger} -> {@code food}). */
    public static String topLevel(String path) {
        int dot = path.indexOf('.');
        return dot < 0 ? path : path.substring(0, dot);
    }

    /** The folder a type's file lives in, e.g. {@code items}. */
    public static String folderFor(CustomItemType type) {
        return ContentLoader.folderFor(type);
    }

    /** Human label for a type used in titles and lore. */
    public static String label(CustomItemType type) {
        return switch (type) {
            case ITEM -> "Items";
            case WEAPON -> "Weapons";
            case ARMOR -> "Armor";
            case FOOD -> "Food";
            case BLOCK -> "Blocks";
            case FURNITURE -> "Furniture";
        };
    }

    /** Singular lowercase name of a type used in messages. */
    public static String singular(CustomItemType type) {
        return label(type).toLowerCase(Locale.ROOT).replaceAll("s$", "");
    }

    /** Base-file header written for newly created content. */
    public static List<String> newFileComments(CustomItemType type) {
        return List.of("Created with the in-game editor (/aitem editor).",
                "Folder: " + folderFor(type) + " — type: " + type.name());
    }

    private static List<EditorField> concat(List<EditorField> first, List<EditorField> second) {
        List<EditorField> out = new ArrayList<>(first.size() + second.size());
        out.addAll(first);
        out.addAll(second);
        return List.copyOf(out);
    }
}
