package com.andreaitemmaker.editor;

import com.andreaitemmaker.AndreaitemmakerPlugin;
import com.andreaitemmaker.api.CustomItemType;
import com.andreaitemmaker.config.ContentLoader;
import com.andreaitemmaker.config.PluginConfig;
import com.andreaitemmaker.content.ItemFactory;
import com.andreaitemmaker.pack.TextureGenerator;
import com.andreaitemmaker.util.AssetPaths;
import com.andreaitemmaker.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates an {@link EditorDocument} before it is written.
 *
 * <p>Two layers, deliberately:
 * <ol>
 *   <li>{@link #validate} checks every field the GUI can produce and attributes each problem to
 *       the exact YAML path, so the editor can point at the offending field and the error text
 *       names the file, the field and the cause.</li>
 *   <li>{@link #authoritative} writes the candidate to a scratch file and runs the real
 *       {@link ContentLoader} over it. If the loader would skip the file, the editor refuses the
 *       save — the editor can therefore never accept something the plugin would reject at load
 *       time (no second copy of the parsing rules to drift out of sync).</li>
 * </ol>
 */
public final class EditorValidator {

    private static final Set<String> TEXTURE_PATTERNS = Set.of("solid", "gradient", "diagonal", "checker");

    private EditorValidator() {
    }

    /**
     * Everything the validator needs to know about the rest of the content set.
     *
     * @param otherIds            ids used by other entries
     * @param otherBlockBases     base block to owning id, for every other custom block
     * @param otherCustomModelData custom model data values used by other entries
     * @param knownMechanics      ids of registered mechanics
     * @param assetsFolder        the plugin data folder, used to check asset files exist
     */
    public record Context(Set<String> otherIds,
                          Map<Material, String> otherBlockBases,
                          Set<Integer> otherCustomModelData,
                          Set<String> knownMechanics,
                          File assetsFolder) {
    }

    /** Build the cross-entry context from the (cached) content index, excluding this document. */
    public static Context context(AndreaitemmakerPlugin plugin, EditorRepository repository,
                                 EditorDocument document) {
        String ownPath = document.file() == null ? null : document.file().getAbsolutePath();
        Set<String> ids = new LinkedHashSet<>();
        Map<Material, String> bases = new java.util.LinkedHashMap<>();
        Set<Integer> cmds = new LinkedHashSet<>();
        for (EditorEntry entry : repository.entries()) {
            boolean self = ownPath != null && entry.file().getAbsolutePath().equals(ownPath);
            if (!self) {
                ids.add(entry.id());
            }
            if (entry.baseBlock() != null) {
                bases.putIfAbsent(entry.baseBlock(), entry.id());
            }
            if (entry.customModelData() != null) {
                cmds.add(entry.customModelData());
            }
        }
        Set<String> mechanics = new LinkedHashSet<>();
        for (var mechanic : plugin.getMechanicRegistry().getAll()) {
            mechanics.add(mechanic.getId());
        }
        return new Context(ids, bases, cmds, mechanics, plugin.getDataFolder());
    }

    /** All issues of the working copy, field by field. */
    public static List<EditorIssue> validate(EditorDocument doc, CustomItemType type, Context ctx) {
        List<EditorIssue> issues = new ArrayList<>();
        validateId(doc, ctx, issues);
        validateType(doc, type, issues);
        validateMaterial(doc, type, issues);
        validateDisplayName(doc, issues);
        validateLore(doc, issues);
        validateNumbers(doc, type, issues);
        validateCustomModelData(doc, ctx, issues);
        validateAttributes(doc, issues);
        validateEnchantments(doc, issues);
        validateTexture(doc, "texture", issues);
        validateArmorTexture(doc, type, issues);
        validateModel(doc, ctx, issues);
        validateMechanics(doc, ctx, issues);
        switch (type) {
            case BLOCK -> validateBlock(doc, ctx, issues);
            case FURNITURE -> validateFurniture(doc, issues);
            case FOOD -> validateFood(doc, issues);
            default -> {
                // nothing type specific
            }
        }
        return List.copyOf(issues);
    }

    /** True when the issues contain at least one error (errors block saving). */
    public static boolean hasErrors(List<EditorIssue> issues) {
        for (EditorIssue issue : issues) {
            if (issue.isError()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Run the real content loader over the candidate. Returns the loader's error messages, or an
     * empty list when the loader accepts the file.
     */
    public static List<String> authoritative(AndreaitemmakerPlugin plugin, EditorRepository repository,
                                             PluginConfig config, EditorDocument doc, CustomItemType type) {
        File scratch = repository.newScratchFile(doc.id());
        if (scratch == null) {
            return List.of("could not create the editor workspace file");
        }
        try {
            Files.writeString(scratch.toPath(), doc.toYaml(), StandardCharsets.UTF_8);
            ContentLoader.LoadResult result = new ContentLoader(plugin, config, true)
                    .loadFile(scratch, type);
            return result.items.isEmpty() ? List.copyOf(result.errors) : List.of();
        } catch (IOException e) {
            return List.of("could not validate: " + e.getMessage());
        } finally {
            if (!scratch.delete()) {
                scratch.deleteOnExit();
            }
        }
    }

    // ---- individual checks ----

    private static void validateId(EditorDocument doc, Context ctx, List<EditorIssue> issues) {
        String id = doc.id();
        if (id == null || id.isBlank()) {
            issues.add(EditorIssue.error("id", "an id is required"));
            return;
        }
        if (!ContentLoader.isValidId(id)) {
            issues.add(EditorIssue.error("id", "invalid id '" + id
                    + "' (use lowercase letters, numbers, '_', '-' or '.')"));
            return;
        }
        if (ctx.otherIds().contains(id)) {
            issues.add(EditorIssue.error("id", "duplicate id '" + id + "' (already defined in another file)"));
        }
        String fileName = stripExtension(doc.file() == null ? "" : doc.file().getName());
        if (!fileName.isEmpty() && !fileName.equals(id)) {
            issues.add(EditorIssue.warning("id", "the file is named " + fileName + ".yml but the id is '"
                    + id + "' (the id key wins; consider renaming the file)"));
        }
    }

    private static void validateType(EditorDocument doc, CustomItemType type, List<EditorIssue> issues) {
        if (type == CustomItemType.BLOCK || type == CustomItemType.FURNITURE) {
            // The folder decides the type; a stray type key is ignored by the loader.
            return;
        }
        String raw = doc.getString("type");
        if (raw == null || raw.isBlank()) {
            return; // absent means ITEM
        }
        if (doc.declaredType() == null) {
            issues.add(EditorIssue.error("type", "unknown type '" + raw + "' (use ITEM, WEAPON, ARMOR, FOOD)"));
        }
    }

    private static void validateMaterial(EditorDocument doc, CustomItemType type, List<EditorIssue> issues) {
        String raw = doc.getString("material");
        if (raw == null || raw.isBlank()) {
            issues.add(EditorIssue.error("material", "missing required field 'material'"));
            return;
        }
        Material material = Material.matchMaterial(raw.trim());
        if (material == null) {
            issues.add(EditorIssue.error("material", "unknown material '" + raw + "'"));
            return;
        }
        if (registryAvailable() && !material.isItem()) {
            issues.add(EditorIssue.warning("material", material.name().toLowerCase(Locale.ROOT)
                    + " is not an item material; the entry cannot be given to a player"));
        }
        if (type == CustomItemType.ARMOR && !isArmorPiece(material)) {
            issues.add(EditorIssue.warning("material", material.name().toLowerCase(Locale.ROOT)
                    + " is not a helmet/chestplate/leggings/boots; the worn slot is derived from the material"));
        }
        if (type == CustomItemType.FOOD && !isEdible(material)) {
            issues.add(EditorIssue.warning("material", material.name().toLowerCase(Locale.ROOT)
                    + " is not edible, so right-clicking will not eat it"));
        }
    }

    private static void validateDisplayName(EditorDocument doc, List<EditorIssue> issues) {
        if (doc.contains("display-name") && doc.getString("display-name", "").isBlank()) {
            issues.add(EditorIssue.warning("display-name", "empty display name (an auto-generated one is used)"));
        }
    }

    private static void validateLore(EditorDocument doc, List<EditorIssue> issues) {
        if (doc.contains("lore") && !doc.isList("lore")) {
            issues.add(EditorIssue.warning("lore", "must be a list of lines (the current value is ignored)"));
        }
    }

    /** Schema-driven numeric checks: covers max-stack-size, food.*, offset-y and similar. */
    private static void validateNumbers(EditorDocument doc, CustomItemType type, List<EditorIssue> issues) {
        for (EditorField field : EditorSchema.fields(type)) {
            if (field.kind() != FieldKind.INT && field.kind() != FieldKind.DOUBLE) {
                continue;
            }
            Object value = doc.get(field.path());
            if (value == null) {
                continue;
            }
            if (!(value instanceof Number number)) {
                issues.add(EditorIssue.error(field.path(),
                        "must be a number, found " + describe(value)));
                continue;
            }
            double d = number.doubleValue();
            if (field.kind() == FieldKind.INT && d != Math.floor(d)) {
                issues.add(EditorIssue.error(field.path(), "must be a whole number"));
                continue;
            }
            if (d < field.min() || d > field.max()) {
                issues.add(EditorIssue.error(field.path(),
                        "must be between " + trim(field.min()) + " and " + trim(field.max())));
            }
        }
        validateNestedNumber(doc, "food.hunger", 1, 20, issues);
        validateNestedNumber(doc, "food.saturation", 0, 20, issues);
        validateNestedNumber(doc, "food.cooldown", 0, 60, issues);
    }

    private static void validateNestedNumber(EditorDocument doc, String path, double min, double max,
                                            List<EditorIssue> issues) {
        Object value = doc.get(path);
        if (value == null || !(value instanceof Number number)) {
            return; // handled as "must be a number" above when present
        }
        double d = number.doubleValue();
        if (d < min || d > max) {
            issues.add(EditorIssue.error(path, "must be between " + trim(min) + " and " + trim(max)));
        }
    }

    private static void validateCustomModelData(EditorDocument doc, Context ctx, List<EditorIssue> issues) {
        Object value = doc.get("custom-model-data");
        if (value instanceof Number number && ctx.otherCustomModelData().contains(number.intValue())) {
            issues.add(EditorIssue.error("custom-model-data", "value " + number.intValue()
                    + " is already used by another item"));
        }
    }

    private static void validateAttributes(EditorDocument doc, List<EditorIssue> issues) {
        if (!doc.contains("attributes")) {
            return;
        }
        if (!doc.isSection("attributes")) {
            issues.add(EditorIssue.warning("attributes", "must be a section of attribute: value pairs"));
            return;
        }
        for (String key : doc.keys("attributes")) {
            String path = "attributes." + key;
            Object value = doc.get(path);
            if (!(value instanceof Number)) {
                issues.add(EditorIssue.error(path, "must be a number, found " + describe(value)));
            } else if (ItemFactory.parseAttribute(key) == null) {
                issues.add(EditorIssue.warning(path, "unknown attribute '" + key + "', it will be ignored"));
            }
        }
    }

    private static void validateEnchantments(EditorDocument doc, List<EditorIssue> issues) {
        if (!doc.contains("enchantments")) {
            return;
        }
        if (!doc.isSection("enchantments")) {
            issues.add(EditorIssue.warning("enchantments", "must be a section of enchantment: level pairs"));
            return;
        }
        for (String key : doc.keys("enchantments")) {
            String path = "enchantments." + key;
            Object value = doc.get(path);
            if (!(value instanceof Number number)) {
                issues.add(EditorIssue.error(path, "must be a number, found " + describe(value)));
                continue;
            }
            if (number.intValue() < 1) {
                issues.add(EditorIssue.error(path, "level must be at least 1"));
            }
            if (registryAvailable()
                    && Enchantment.getByKey(NamespacedKey.minecraft(key.trim().toLowerCase(Locale.ROOT))) == null) {
                issues.add(EditorIssue.warning(path, "unknown enchantment '" + key + "', it will be ignored"));
            }
        }
    }

    private static void validateTexture(EditorDocument doc, String path, List<EditorIssue> issues) {
        if (!doc.contains(path)) {
            return;
        }
        Object value = doc.get(path);
        if (value instanceof String text) {
            String t = text.trim();
            if (t.startsWith("#")) {
                try {
                    TextureGenerator.parseColor(t);
                } catch (IllegalArgumentException e) {
                    issues.add(EditorIssue.error(path, e.getMessage()));
                }
                return;
            }
            if (t.endsWith(".png")) {
                if (!AssetPaths.isSafeAssetPath(t)) {
                    issues.add(EditorIssue.error(path, "unsafe texture path '" + t
                            + "' (must be a relative path inside assets/textures/)"));
                }
                return;
            }
            issues.add(EditorIssue.error(path, "unsupported texture '" + t
                    + "' (use a hex color like '#4f7cff', a pattern section, or a .png in assets/textures/)"));
            return;
        }
        Map<String, Object> section = asMap(value);
        if (section != null) {
            String pattern = String.valueOf(section.getOrDefault("pattern", "gradient")).toLowerCase(Locale.ROOT);
            if (!TEXTURE_PATTERNS.contains(pattern)) {
                issues.add(EditorIssue.error(path + ".pattern", "unknown pattern '" + pattern
                        + "' (solid, gradient, diagonal, checker)"));
            }
            for (String key : new String[]{"color", "color2"}) {
                Object colourValue = section.get(key);
                if (colourValue == null) {
                    continue;
                }
                String color = String.valueOf(colourValue);
                try {
                    TextureGenerator.parseColor(color);
                } catch (IllegalArgumentException e) {
                    issues.add(EditorIssue.error(path + "." + key, e.getMessage()));
                }
            }
            return;
        }
        issues.add(EditorIssue.error(path, "unsupported texture definition"));
    }

    private static void validateArmorTexture(EditorDocument doc, CustomItemType type, List<EditorIssue> issues) {
        if (!doc.contains("armor-texture")) {
            return;
        }
        if (type != CustomItemType.ARMOR) {
            issues.add(EditorIssue.warning("armor-texture",
                    "only used by ARMOR items; it is ignored here"));
            return;
        }
        validateTexture(doc, "armor-texture", issues);
    }

    private static void validateModel(EditorDocument doc, Context ctx, List<EditorIssue> issues) {
        String model = doc.getString("model");
        if (model == null) {
            return;
        }
        if (!model.endsWith(".json")) {
            issues.add(EditorIssue.error("model", "must be a path to a .json file inside assets/models/"));
            return;
        }
        if (!AssetPaths.isSafeAssetPath(model)) {
            issues.add(EditorIssue.error("model", "unsafe model path '" + model
                    + "' (must be a relative path inside assets/models/)"));
            return;
        }
        if (AssetPaths.resolve(ctx.assetsFolder(), model) == null) {
            issues.add(EditorIssue.error("model", "unsafe model path '" + model + "'"));
        } else if (!AssetPaths.resolve(ctx.assetsFolder(), model).isFile()) {
            issues.add(EditorIssue.warning("model", "file not found: " + model
                    + " (a generated model is used instead)"));
        }
    }

    private static void validateMechanics(EditorDocument doc, Context ctx, List<EditorIssue> issues) {
        if (!doc.contains("mechanics")) {
            return;
        }
        if (!doc.isSection("mechanics")) {
            issues.add(EditorIssue.error("mechanics", "must be a section of mechanic: parameters"));
            return;
        }
        for (String key : doc.keys("mechanics")) {
            String path = "mechanics." + key;
            if (!ctx.knownMechanics().contains(key)) {
                issues.add(EditorIssue.warning(path, "unknown mechanic '" + key + "' (registered: "
                        + String.join(", ", ctx.knownMechanics()) + ")"));
            } else if (!doc.isSection(path)) {
                issues.add(EditorIssue.warning(path, "expected a section of parameters; leave it empty to use defaults"));
            }
        }
    }

    private static void validateBlock(EditorDocument doc, Context ctx, List<EditorIssue> issues) {
        String raw = doc.getString("base-block");
        if (raw == null || raw.isBlank()) {
            issues.add(EditorIssue.error("base-block", "missing required field 'base-block'"));
            return;
        }
        Material base = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        if (base == null) {
            issues.add(EditorIssue.error("base-block", "unknown material '" + raw + "'"));
            return;
        }
        if (registryAvailable()) {
            String problem = ContentLoader.blockBaseProblem(base);
            if (problem != null) {
                issues.add(EditorIssue.error("base-block", problem));
                return;
            }
        } else if (ContentLoader.isDeniedBaseName(base.name())) {
            issues.add(EditorIssue.error("base-block", "base-block '" + base + "' is not allowed"));
            return;
        }
        String owner = ctx.otherBlockBases().get(base);
        if (owner != null) {
            issues.add(EditorIssue.error("base-block", "base block "
                    + base.name().toLowerCase(Locale.ROOT) + " is already used by '" + owner
                    + "' (one base block backs one custom block)"));
        }
    }

    private static void validateFurniture(EditorDocument doc, List<EditorIssue> issues) {
        for (String path : new String[]{"place-sound", "break-sound"}) {
            String raw = doc.getString(path);
            if (raw != null && !raw.isBlank() && Sounds.parse(raw) == null) {
                issues.add(EditorIssue.warning(path, "unknown sound '" + raw
                        + "' (see the Bukkit sound list, e.g. block.wood.place)"));
            }
        }
    }

    private static void validateFood(EditorDocument doc, List<EditorIssue> issues) {
        if (!doc.isSection("food")) {
            return;
        }
        // The loader reads hunger/saturation/cooldown as soon as the section exists, so a section
        // without them is rejected at load time.
        record Required(String path, String label) {
        }
        for (Required required : List.of(new Required("food.hunger", "hunger"),
                new Required("food.saturation", "saturation"),
                new Required("food.cooldown", "cooldown"))) {
            if (doc.get(required.path()) == null) {
                issues.add(EditorIssue.error(required.path(), "required when the 'food' section is present"));
            }
        }
    }

    // ---- small helpers ----

    /**
     * A few Bukkit helpers ({@code Material.isItem()}, {@code Material.isBlock()},
     * {@code Enchantment.getByKey()}) consult the live registry, which only exists on a running
     * server. Those specific checks are skipped when there is no registry — unit tests load the
     * API without a server — instead of failing the whole validation. Every other rule is
     * registry-free and therefore tested exactly as it runs in production.
     */
    private static boolean registryAvailable() {
        return Bukkit.getServer() != null;
    }

    private static boolean isArmorPiece(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    private static boolean isEdible(Material material) {
        String name = material.name();
        return name.equals("APPLE") || name.equals("BREAD") || name.equals("GOLDEN_APPLE")
                || name.equals("ENCHANTED_GOLDEN_APPLE") || name.equals("COOKED_PORKCHOP")
                || name.contains("PORKCHOP") || name.contains("BEEF") || name.contains("CHICKEN")
                || name.contains("MUTTON") || name.contains("RABBIT") || name.contains("COD")
                || name.contains("SALMON") || name.contains("TROPICAL_FISH") || name.contains("PUFFERFISH")
                || name.contains("POTATO") || name.contains("CARROT") || name.contains("BEETROOT")
                || name.contains("MELON") || name.contains("BERRIES") || name.contains("COOKIE")
                || name.contains("STEW") || name.contains("SOUP") || name.contains("PIE")
                || name.contains("CAKE") || name.contains("DRIED_KELP") || name.contains("HONEY")
                || name.contains("CHORUS_FRUIT") || name.contains("MUSHROOM_STEW");
    }

    /** A section as a plain map, whether it is a configuration section or a raw map value. */
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof ConfigurationSection section) {
            return ContentLoader.sectionToMap(section);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return out;
        }
        return null;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static String describe(Object value) {
        if (value == null) {
            return "nothing";
        }
        if (value instanceof ConfigurationSection) {
            return "a section";
        }
        return "'" + value + "'";
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
