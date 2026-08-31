package com.andreaitemmaker.config;

import com.andreaitemmaker.AndreaitemmakerPlugin;
import com.andreaitemmaker.api.CustomBlock;
import com.andreaitemmaker.api.CustomFood;
import com.andreaitemmaker.api.CustomFurniture;
import com.andreaitemmaker.api.CustomItem;
import com.andreaitemmaker.api.CustomItemType;
import com.andreaitemmaker.content.ItemFactory;
import com.andreaitemmaker.util.AssetPaths;
import com.andreaitemmaker.util.Chat;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Loads content from the items/, blocks/ and furniture/ folders.
 * Invalid entries are skipped with a precise error message; one bad file never
 * prevents the rest of the content (or the plugin) from loading.
 */
public final class ContentLoader {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_.-]+");
    private static final Set<String> BLOCK_DENYLIST = Set.of(
            "TNT", "BEDROCK", "BARRIER", "LIGHT", "SPAWNER", "TRIAL_SPAWNER",
            "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "COMMAND_BLOCK",
            "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK");

    private final AndreaitemmakerPlugin plugin;
    private final int customModelDataStart;

    /**
     * Load content using the plugin's current configuration. Used by in-memory callers that
     * already hold an up-to-date {@link PluginConfig} snapshot so loading can run on a
     * background thread without depending on mutable plugin state (see
     * {@link #ContentLoader(AndreaitemmakerPlugin, PluginConfig)}).
     */
    public ContentLoader(AndreaitemmakerPlugin plugin) {
        this(plugin, plugin.getConfigValues());
    }

    /**
     * Load content against an explicit configuration snapshot. This is the form used by the
     * asynchronous reload, which computes a fresh {@link PluginConfig} on the background
     * thread and must not read it from the plugin (which is still serving the old state).
     */
    public ContentLoader(AndreaitemmakerPlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.customModelDataStart = config == null ? 1000 : config.customModelDataStart;
    }

    public LoadResult load() {
        LoadResult result = new LoadResult();
        Set<String> usedIds = new HashSet<>();
        Set<Integer> usedCmd = new HashSet<>();
        Set<Material> usedBases = new HashSet<>();
        int[] nextCmd = {customModelDataStart};

        File dataFolder = plugin.getDataFolder();
        loadFolder(new File(dataFolder, "items"), null, result, usedIds, usedCmd, usedBases, nextCmd);
        loadFolder(new File(dataFolder, "blocks"), CustomItemType.BLOCK, result, usedIds, usedCmd, usedBases, nextCmd);
        loadFolder(new File(dataFolder, "furniture"), CustomItemType.FURNITURE, result, usedIds, usedCmd, usedBases, nextCmd);

        return result;
    }

    private void loadFolder(File folder, CustomItemType forcedType, LoadResult result,
                            Set<String> usedIds, Set<Integer> usedCmd, Set<Material> usedBases, int[] nextCmd) {
        if (!folder.isDirectory()) {
            return;
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) {
            return;
        }
        java.util.Arrays.sort(files);
        for (File file : files) {
            parse(file, forcedType, result, usedIds, usedCmd, usedBases, nextCmd);
        }
    }

    private void parse(File file, CustomItemType forcedType, LoadResult result,
                       Set<String> usedIds, Set<Integer> usedCmd, Set<Material> usedBases, int[] nextCmd) {
        String fileName = file.getName();
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String id = yaml.getString("id", fileName.substring(0, fileName.length() - 4));
            if (!ID_PATTERN.matcher(id).matches()) {
                throw new ConfigException(fileName, "invalid id '" + id + "' (use lowercase letters, numbers, '_', '-' or '.')");
            }
            if (!usedIds.add(id)) {
                throw new ConfigException(fileName, "duplicate id '" + id + "' (already defined in another file)");
            }

            CustomItemType type = forcedType != null ? forcedType : parseType(yaml.getString("type", "ITEM"));
            Material material = requireMaterial(yaml, "material");
            String displayName = Chat.color(yaml.getString("display-name", humanize(id)));
            List<String> lore = yaml.getStringList("lore");
            int cmd;
            if (yaml.contains("custom-model-data")) {
                cmd = requireInt(yaml, "custom-model-data", 0, Integer.MAX_VALUE);
                if (!usedCmd.add(cmd)) {
                    throw new ConfigException(fileName, "custom-model-data " + cmd + " is already used by another item");
                }
            } else {
                cmd = nextFreeCmd(nextCmd, usedCmd);
            }
            int maxStack = yaml.contains("max-stack-size")
                    ? requireInt(yaml, "max-stack-size", 1, 99)
                    : defaultMaxStack(type);
            Map<String, Double> attributes = parseAttributes(yaml);
            Map<String, Integer> enchantments = parseEnchantments(yaml);
            boolean unbreakable = yaml.getBoolean("unbreakable", false);
            boolean glow = yaml.getBoolean("glow", false);
            String texture = parseTexture(yaml, fileName, "texture");
            // Optional dedicated worn-armor texture (ARMOR items only). Falls back to the
            // model's own texture / generated layer when absent.
            String armorTexture = parseTexture(yaml, fileName, "armor-texture");
            String model = parseModel(yaml, fileName);
            Map<String, Map<String, Object>> mechanics = parseMechanics(yaml);

            CustomItem item = switch (type) {
                case BLOCK -> parseBlock(id, yaml, fileName, material, displayName, lore, cmd, maxStack,
                        attributes, enchantments, unbreakable, glow, texture, armorTexture, model, mechanics, usedBases);
                case FURNITURE -> parseFurniture(id, yaml, fileName, material, displayName, lore, cmd, maxStack,
                        attributes, enchantments, unbreakable, glow, texture, armorTexture, model, mechanics);
                case FOOD -> parseFood(id, yaml, fileName, material, displayName, lore, cmd, maxStack,
                        attributes, enchantments, unbreakable, glow, texture, armorTexture, model, mechanics);
                default -> new CustomItem(id, type, material, displayName, lore, cmd, maxStack,
                        attributes, enchantments, unbreakable, glow, texture, armorTexture, model, mechanics);
            };
            result.items.add(item);
            result.loaded++;
        } catch (ConfigException e) {
            result.errors.add(fileName + ": " + e.getMessage());
            plugin.getLogger().warning("Skipped " + fileName + ": " + e.getMessage());
        } catch (Exception e) {
            result.errors.add(fileName + ": unexpected error: " + e);
            plugin.getLogger().warning("Skipped " + fileName + " (unexpected error): " + e);
        }
    }

    private CustomItem parseBlock(String id, YamlConfiguration yaml, String fileName, Material material,
                                  String displayName, List<String> lore, int cmd, int maxStack,
                                  Map<String, Double> attributes, Map<String, Integer> enchantments,
                                  boolean unbreakable, boolean glow, String texture, String armorTexture,
                                  String model, Map<String, Map<String, Object>> mechanics, Set<Material> usedBases) {
        Material base = requireMaterial(yaml, "base-block");
        if (!base.isBlock()) {
            throw new ConfigException(fileName, "base-block '" + base + "' is not a block");
        }
        if (!base.isOccluding()) {
            throw new ConfigException(fileName, "base-block '" + base + "' must be a full solid block "
                    + "(glass, stairs, slabs and similar cannot be used as a base)");
        }
        if (BLOCK_DENYLIST.contains(base.name())) {
            throw new ConfigException(fileName, "base-block '" + base + "' is not allowed");
        }
        if (!usedBases.add(base)) {
            throw new ConfigException(fileName, "base-block '" + base + "' is already used by another block");
        }
        boolean dropsItem = yaml.getBoolean("drops-item", true);
        return new CustomBlock(id, material, displayName, lore, cmd, maxStack, attributes, enchantments,
                unbreakable, glow, texture, armorTexture, model, mechanics, base, dropsItem);
    }

    private CustomItem parseFurniture(String id, YamlConfiguration yaml, String fileName, Material material,
                                      String displayName, List<String> lore, int cmd, int maxStack,
                                      Map<String, Double> attributes, Map<String, Integer> enchantments,
                                      boolean unbreakable, boolean glow, String texture, String armorTexture,
                                      String model, Map<String, Map<String, Object>> mechanics) {
        boolean small = yaml.getBoolean("small", false);
        boolean consumable = yaml.getBoolean("consumable", true);
        boolean dropsItem = yaml.getBoolean("drops-item", true);
        double offsetY = yaml.getDouble("offset-y", 0.0);
        if (offsetY < -2 || offsetY > 2) {
            throw new ConfigException(fileName, "offset-y must be between -2 and 2");
        }
        org.bukkit.Sound placeSound = com.andreaitemmaker.util.Sounds.parse(yaml.getString("place-sound", ""));
        org.bukkit.Sound breakSound = com.andreaitemmaker.util.Sounds.parse(yaml.getString("break-sound", ""));
        return new CustomFurniture(id, material, displayName, lore, cmd, maxStack, attributes, enchantments,
                unbreakable, glow, texture, armorTexture, model, mechanics, small, consumable, dropsItem, offsetY,
                placeSound, breakSound);
    }

    private CustomItem parseFood(String id, YamlConfiguration yaml, String fileName, Material material,
                                 String displayName, List<String> lore, int cmd, int maxStack,
                                 Map<String, Double> attributes, Map<String, Integer> enchantments,
                                 boolean unbreakable, boolean glow, String texture, String armorTexture,
                                 String model, Map<String, Map<String, Object>> mechanics) {
        ConfigurationSection food = yaml.getConfigurationSection("food");
        int hunger = 4;
        float saturation = 6f;
        int cooldown = 5;
        if (food != null) {
            hunger = requireInt(food, "hunger", 1, 20);
            saturation = (float) requireDouble(food, "saturation", 0, 20);
            cooldown = requireInt(food, "cooldown", 0, 60);
        }
        return new CustomFood(id, material, displayName, lore, cmd, maxStack, attributes, enchantments,
                unbreakable, glow, texture, armorTexture, model, mechanics, hunger, saturation, cooldown);
    }

    // ---- helpers ----

    private static CustomItemType parseType(String s) {
        try {
            return CustomItemType.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ConfigException("?", "unknown type '" + s + "' (use ITEM, WEAPON, ARMOR, FOOD)");
        }
    }

    private static Material requireMaterial(YamlConfiguration yaml, String path) {
        String s = yaml.getString(path);
        if (s == null || s.isEmpty()) {
            throw new ConfigException("?", "missing required field '" + path + "'");
        }
        Material material = Material.matchMaterial(s);
        if (material == null) {
            throw new ConfigException("?", "unknown material '" + s + "' in '" + path + "'");
        }
        return material;
    }

    private static int requireInt(ConfigurationSection yaml, String path, int min, int max) {
        Object v = yaml.get(path);
        if (!(v instanceof Number n)) {
            throw new ConfigException("?", "'" + path + "' must be a number");
        }
        int i = n.intValue();
        if (i < min || i > max) {
            throw new ConfigException("?", "'" + path + "' must be between " + min + " and " + max);
        }
        return i;
    }

    private static double requireDouble(ConfigurationSection yaml, String path, double min, double max) {
        Object v = yaml.get(path);
        if (!(v instanceof Number n)) {
            throw new ConfigException("?", "'" + path + "' must be a number");
        }
        double d = n.doubleValue();
        if (d < min || d > max) {
            throw new ConfigException("?", "'" + path + "' must be between " + min + " and " + max);
        }
        return d;
    }

    private Map<String, Double> parseAttributes(YamlConfiguration yaml) {
        Map<String, Double> out = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("attributes");
        if (section == null) {
            return out;
        }
        for (String key : section.getKeys(false)) {
            Object v = section.get(key);
            if (!(v instanceof Number n)) {
                plugin.getLogger().warning("Attribute '" + key + "' must be a number, ignored");
                continue;
            }
            String normalized = key.trim().toLowerCase(Locale.ROOT);
            if (ItemFactory.parseAttribute(normalized) == null) {
                plugin.getLogger().warning("Unknown attribute '" + key + "', ignored");
                continue;
            }
            out.put(normalized, n.doubleValue());
        }
        return out;
    }

    private Map<String, Integer> parseEnchantments(YamlConfiguration yaml) {
        Map<String, Integer> out = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("enchantments");
        if (section == null) {
            return out;
        }
        for (String key : section.getKeys(false)) {
            Object v = section.get(key);
            if (!(v instanceof Number n)) {
                plugin.getLogger().warning("Enchantment '" + key + "' must be a number, ignored");
                continue;
            }
            out.put(key.trim().toLowerCase(Locale.ROOT), Math.max(1, n.intValue()));
        }
        return out;
    }

    private String parseTexture(YamlConfiguration yaml, String fileName, String key) {
        if (!yaml.contains(key)) {
            return null;
        }
        Object v = yaml.get(key);
        if (v instanceof String s) {
            String t = s.trim();
            if (t.startsWith("#")) {
                try {
                    com.andreaitemmaker.pack.TextureGenerator.parseColor(t);
                } catch (IllegalArgumentException e) {
                    throw new ConfigException(fileName, e.getMessage());
                }
                return t;
            }
            if (t.endsWith(".png")) {
                if (!AssetPaths.isSafeAssetPath(t)) {
                    throw new ConfigException(fileName, "unsafe texture path '" + t
                            + "' (must be a relative path inside assets/textures/)");
                }
                return t;
            }
            throw new ConfigException(fileName, "unsupported texture '" + t + "' "
                    + "(use a hex color like '#4f7cff', a texture map, or a path to a .png in assets/textures/)");
        }
        if (v instanceof ConfigurationSection section) {
            String pattern = section.getString("pattern", "gradient").toLowerCase(Locale.ROOT);
            if (!Set.of("solid", "gradient", "diagonal", "checker").contains(pattern)) {
                throw new ConfigException(fileName, "unknown texture pattern '" + pattern + "'");
            }
            String color = section.getString("color");
            String color2 = section.getString("color2", null);
            boolean outline = section.getBoolean("outline", true);
            try {
                com.andreaitemmaker.pack.TextureGenerator.parseColor(color);
                if (color2 != null) {
                    com.andreaitemmaker.pack.TextureGenerator.parseColor(color2);
                }
            } catch (IllegalArgumentException e) {
                throw new ConfigException(fileName, e.getMessage());
            }
            return pattern + "|" + color + "|" + (color2 == null ? "" : color2) + "|" + outline;
        }
        throw new ConfigException(fileName, "unsupported texture definition");
    }

    private String parseModel(YamlConfiguration yaml, String fileName) {
        String model = yaml.getString("model");
        if (model == null) {
            return null;
        }
        if (!model.endsWith(".json")) {
            throw new ConfigException(fileName, "model must be a path to a .json file inside assets/models/");
        }
        if (!AssetPaths.isSafeAssetPath(model)) {
            throw new ConfigException(fileName, "unsafe model path '" + model
                    + "' (must be a relative path inside assets/models/)");
        }
        return model;
    }

    private Map<String, Map<String, Object>> parseMechanics(YamlConfiguration yaml) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("mechanics");
        if (section == null) {
            return out;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection mechanic = section.getConfigurationSection(key);
            out.put(key, mechanic == null ? Map.of() : sectionToMap(mechanic));
        }
        return out;
    }

    static Map<String, Object> sectionToMap(ConfigurationSection section) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object v = section.get(key);
            if (v instanceof ConfigurationSection nested) {
                out.put(key, sectionToMap(nested));
            } else if (v instanceof List<?> list) {
                out.put(key, list.stream()
                        .map(e -> e instanceof ConfigurationSection cs ? sectionToMap(cs) : e)
                        .toList());
            } else {
                out.put(key, v);
            }
        }
        return out;
    }

    private static int nextFreeCmd(int[] counter, Set<Integer> used) {
        int candidate = counter[0]++;
        while (!used.add(candidate)) {
            candidate = counter[0]++;
        }
        return candidate;
    }

    private static int defaultMaxStack(CustomItemType type) {
        return switch (type) {
            case WEAPON, ARMOR, BLOCK, FURNITURE -> 1;
            default -> 64;
        };
    }

    static String humanize(String id) {
        String[] words = id.split("[_.-]");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.length() == 0 ? id : sb.toString();
    }

    /** Result of a load pass. */
    public static final class LoadResult {
        public final List<CustomItem> items = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
        public int loaded;
    }
}
