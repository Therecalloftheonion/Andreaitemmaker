package com.andreaitemmaker.pack;

import com.andreaitemmaker.api.CustomBlock;
import com.andreaitemmaker.api.CustomItem;
import com.andreaitemmaker.api.CustomItemType;
import com.andreaitemmaker.util.Json;
import com.andreaitemmaker.util.PngWriter;
import com.andreaitemmaker.util.ServerVersion;
import org.bukkit.Material;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the resource pack for the current server version. Generation produces an in-memory
 * map of file path to bytes, which callers can turn into {@code pack.zip} (for serving,
 * hashing and uploading) and/or an unzipped {@code pack/} folder (so admins can host it
 * manually when the built-in HTTP server is unreachable).
 *
 * <p>Two layouts are produced depending on the server version:
 * <ul>
 *   <li><b>Legacy</b> (&lt; 1.21.2): items are wired with CustomModelData predicates patched into
 *       {@code assets/minecraft/models/item/&lt;base&gt;.json}.</li>
 *   <li><b>Modern</b> (&gt;= 1.21.2): each item gets its own definition in
 *       {@code assets/&lt;ns&gt;/items/&lt;id&gt;.json} and stacks carry the
 *       {@code minecraft:item_model} component, so any base material works.</li>
 * </ul>
 * Custom blocks additionally override {@code assets/minecraft/blockstates/&lt;base&gt;.json}
 * (one base block per custom block, all versions). Armor gets worn-layer textures and, on
 * modern versions, an equipment asset wired through the equippable component.
 */
public final class PackGenerator {

    private static final Set<String> HANDHELD_BASES = Set.of(
            "netherite_sword", "diamond_sword", "iron_sword", "golden_sword", "stone_sword", "wooden_sword",
            "netherite_axe", "diamond_axe", "iron_axe", "golden_axe", "stone_axe", "wooden_axe",
            "netherite_pickaxe", "diamond_pickaxe", "iron_pickaxe", "golden_pickaxe", "stone_pickaxe", "wooden_pickaxe",
            "netherite_shovel", "diamond_shovel", "iron_shovel", "golden_shovel", "stone_shovel", "wooden_shovel",
            "netherite_hoe", "diamond_hoe", "iron_hoe", "golden_hoe", "stone_hoe", "wooden_hoe");

    private static final Set<String> GENERATED_BASES = Set.of(
            "stick", "paper", "diamond", "emerald", "iron_ingot", "gold_ingot", "netherite_ingot", "copper_ingot",
            "iron_nugget", "gold_nugget", "coal", "charcoal", "flint", "feather", "string", "leather", "bone",
            "blaze_rod", "ender_pearl", "snowball", "egg", "arrow", "apple", "golden_apple", "bread",
            "cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_cod", "cooked_salmon", "carrot", "potato",
            "cookie", "melon_slice", "sweet_berries", "glow_berries", "pumpkin_pie", "cake",
            "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots",
            "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots",
            "golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots",
            "netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots",
            "leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots",
            "chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots", "turtle_helmet",
            "quartz", "redstone", "lapis_lazuli", "amethyst_shard", "echo_shard", "nether_star",
            "prismarine_shard", "prismarine_crystals", "phantom_membrane", "rabbit_foot", "ghast_tear",
            "blaze_powder", "gunpowder", "sugar", "slime_ball", "clay_ball", "brick", "nether_brick");

    private static final Map<String, String> BASE_PARENT = new HashMap<>();

    static {
        for (String name : HANDHELD_BASES) {
            BASE_PARENT.put(name, "minecraft:item/handheld");
        }
        for (String name : GENERATED_BASES) {
            BASE_PARENT.put(name, "minecraft:item/generated");
        }
    }

    /** Everything the generator needs. */
    public record Context(
            String namespace,
            ServerVersion.PackTarget target,
            ServerVersion.Version version,
            int textureSize,
            String description,
            File dataFolder,
            Collection<CustomItem> items,
            Logger logger) {
    }

    private record TexSpec(TextureGenerator.Pattern pattern, int c1, int c2, boolean outline) {
    }

    private PackGenerator() {
    }

    /** Generate the pack as a map of file path to bytes (paths use '/'). */
    public static Map<String, byte[]> buildEntries(Context ctx) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        putString(entries, "pack.mcmeta", packMcmeta(ctx));
        put(entries, "pack.png", packIcon());        Map<Material, List<CustomItem>> legacyGroups = new LinkedHashMap<>();
        Set<String> writtenItemTextures = new java.util.HashSet<>();
        Set<String> writtenItemModels = new java.util.HashSet<>();
        for (CustomItem item : ctx.items()) {
            // Every item gets a models/item/<id>.json entry (generated or imported).
            writtenItemModels.add(item.getId());
        }

        for (CustomItem item : ctx.items()) {
            writeItemAssets(entries, ctx, item, legacyGroups, writtenItemTextures);
            if (item.getType().isArmor()) {
                writeArmorAssets(entries, ctx, item);
            }
            if (item instanceof CustomBlock block) {
                writeBlockAssets(entries, ctx, block);
            }
        }

        if (ctx.target().mode() == ServerVersion.Mode.LEGACY) {
            writeLegacyOverrides(entries, ctx, legacyGroups);
        }
        copyImportedTextures(entries, ctx, writtenItemTextures);
        copyImportedModels(entries, ctx, writtenItemModels);
        return entries;
    }

    /** Zip the generated entries into a single {@code .zip} byte array. */
    public static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(64 * 1024);
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    /** Convenience: generate and zip in one call. */
    public static byte[] generate(Context ctx) throws IOException {
        return zip(buildEntries(ctx));
    }

    /**
     * Write the generated entries as an unzipped resource pack folder, so admins can host it
     * anywhere (file host, web server) or re-zip it by hand. The folder is cleared first.
     */
    public static void writeFolder(Map<String, byte[]> entries, File folder) throws IOException {
        deleteRecursively(folder);
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            File target = new File(folder, entry.getKey());
            File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("could not create folder " + parent);
            }
            Files.write(target.toPath(), entry.getValue());
        }
    }

    private static void deleteRecursively(File file) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    // ---- pack root files ----

    private static String packMcmeta(Context ctx) {
        int format = ctx.target().format();
        String desc = ctx.description();
        return ctx.target().rangeFormat()
                ? Json.obj("pack", Map.of("min_format", format, "max_format", format, "description", desc))
                : Json.obj("pack", Map.of("pack_format", format, "description", desc));
    }

    private static byte[] packIcon() {
        int[] icon = TextureGenerator.generate(128, TextureGenerator.Pattern.GRADIENT,
                TextureGenerator.parseColor("#4f7cff"), TextureGenerator.parseColor("#1b2a6b"), true);
        return PngWriter.write(128, 128, icon);
    }

    // ---- per-item assets ----

    private static void writeItemAssets(Map<String, byte[]> entries, Context ctx, CustomItem item,
                                        Map<Material, List<CustomItem>> legacyGroups,
                                        Set<String> writtenItemTextures) throws IOException {
        String ns = ctx.namespace();
        String id = item.getId();
        // With an imported model and no explicit texture, the texture is the model's own PNG
        // from assets/textures/ (copied below). Don't generate a placeholder over that path.
        if (item.getModelFile() == null || item.getTextureSpec() != null) {
            put(entries, "assets/" + ns + "/textures/item/" + id + ".png", itemTexturePng(ctx, item));
            writtenItemTextures.add(id);
        }
        String modelJson;
        if (item.getModelFile() != null) {
            modelJson = readModelOrFallback(ctx, item);
        } else {
            String parent = item.getType() == CustomItemType.WEAPON
                    ? "minecraft:item/handheld"
                    : "minecraft:item/generated";
            modelJson = Json.obj("parent", parent,
                    "textures", Map.of("layer0", ns + ":item/" + id));
        }
        if (!(item instanceof CustomBlock)) {
            // Blocks write their own item model (parented to the block model) in writeBlockAssets.
            putString(entries, "assets/" + ns + "/models/item/" + id + ".json", modelJson);
        }
        if (ctx.target().mode() == ServerVersion.Mode.MODERN) {
            putString(entries, "assets/" + ns + "/items/" + id + ".json",
                    Json.obj("model", Map.of("type", "minecraft:model", "model", ns + ":item/" + id)));
        } else {
            legacyGroups.computeIfAbsent(item.getMaterial(), k -> new ArrayList<>()).add(item);
        }
    }

    private static void writeBlockAssets(Map<String, byte[]> entries, Context ctx, CustomBlock block) throws IOException {
        String ns = ctx.namespace();
        String id = block.getId();
        String baseKey = block.getBaseBlock().name().toLowerCase(Locale.ROOT);
        byte[] texture = itemTexturePng(ctx, block);
        put(entries, "assets/" + ns + "/textures/block/" + id + ".png", texture);
        String blockModel = block.getModelFile() != null
                ? readModelOrFallback(ctx, block)
                : Json.obj("parent", "minecraft:block/cube_all", "textures", Map.of("all", ns + ":block/" + id));
        putString(entries, "assets/" + ns + "/models/block/" + id + ".json", blockModel);
        putString(entries, "assets/" + ns + "/models/item/" + id + ".json",
                Json.obj("parent", ns + ":block/" + id));
        // The item definition for the block item (modern layout) is written by writeItemAssets.
        putString(entries, "assets/minecraft/blockstates/" + baseKey + ".json",
                Json.obj("variants", Map.of("", Map.of("model", ns + ":block/" + id))));
    }

    private static void writeArmorAssets(Map<String, byte[]> entries, Context ctx, CustomItem item) throws IOException {
        String ns = ctx.namespace();
        String id = item.getId();
        byte[] layer1 = armorLayerPng(ctx, item, true);
        byte[] layer2 = armorLayerPng(ctx, item, false);
        put(entries, "assets/" + ns + "/textures/models/armor/" + id + "_layer_1.png", layer1);
        put(entries, "assets/" + ns + "/textures/models/armor/" + id + "_layer_2.png", layer2);
        if (ctx.target().mode() == ServerVersion.Mode.MODERN) {
            if (ctx.target().format() >= 75) {
                // 1.21.11+: textures moved under textures/entity/equipment/...
                put(entries, "assets/" + ns + "/textures/entity/equipment/humanoid/" + id + ".png", layer1);
                put(entries, "assets/" + ns + "/textures/entity/equipment/humanoid_leggings/" + id + ".png", layer2);
                putString(entries, "assets/" + ns + "/equipment/" + id + ".json", Json.obj("layers", Map.of(
                        "humanoid", List.of(Map.of("texture", ns + ":" + id)),
                        "humanoid_leggings", List.of(Map.of("texture", ns + ":" + id)))));
            } else {
                putString(entries, "assets/" + ns + "/equipment/" + id + ".json", Json.obj("layers", Map.of(
                        "humanoid", List.of(ns + ":" + id),
                        "humanoid_leggings", List.of(ns + ":" + id))));
            }
        }
    }

    // ---- legacy (1.20.5 - 1.21.1) overrides ----

    private static void writeLegacyOverrides(Map<String, byte[]> entries, Context ctx,
                                             Map<Material, List<CustomItem>> groups) {
        for (Map.Entry<Material, List<CustomItem>> e : groups.entrySet()) {
            String baseKey = e.getKey().name().toLowerCase(Locale.ROOT);
            String parent = BASE_PARENT.get(baseKey);
            if (parent == null) {
                ctx.logger().warning("Cannot wire custom items on material '" + baseKey
                        + "' for this server version (no safe base model). Use a material from the documented list.");
                continue;
            }
            List<Object> overrides = new ArrayList<>();
            for (CustomItem item : e.getValue()) {
                overrides.add(Map.of(
                        "predicate", Map.of("custom_model_data", item.getCustomModelData()),
                        "model", ctx.namespace() + ":item/" + item.getId()));
            }
            String json = Json.obj(
                    "parent", parent,
                    "textures", Map.of("layer0", "minecraft:item/" + baseKey),
                    "overrides", overrides);
            putString(entries, "assets/minecraft/models/item/" + baseKey + ".json", json);
        }
    }

    // ---- imported assets ----

    /** Copy every JSON in assets/models/ that isn't an item's own model (e.g. model parents). */
    private static void copyImportedModels(Map<String, byte[]> entries, Context ctx,
                                           Set<String> writtenItemModels) throws IOException {
        File models = new File(ctx.dataFolder(), "assets/models");
        File[] files = models.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (files == null) {
            return;
        }
        for (File f : files) {
            String base = f.getName().substring(0, f.getName().length() - 5);
            if (writtenItemModels.contains(base)) {
                continue; // already written as this item's model
            }
            put(entries, "assets/" + ctx.namespace() + "/models/item/" + f.getName(), Files.readAllBytes(f.toPath()));
        }
    }

    private static void copyImportedTextures(Map<String, byte[]> entries, Context ctx,
                                             Set<String> writtenItemTextures) throws IOException {
        File textures = new File(ctx.dataFolder(), "assets/textures");
        File[] files = textures.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
        if (files == null) {
            return;
        }
        for (File f : files) {
            String base = f.getName().substring(0, f.getName().length() - 4);
            if (writtenItemTextures.contains(base)) {
                continue; // already written as this item's texture
            }
            put(entries, "assets/" + ctx.namespace() + "/textures/item/" + f.getName(), Files.readAllBytes(f.toPath()));
            // Animated textures: copy the matching .png.mcmeta file next to the texture.
            File mcmeta = new File(textures, f.getName() + ".mcmeta");
            if (mcmeta.isFile()) {
                put(entries, "assets/" + ctx.namespace() + "/textures/item/" + f.getName() + ".mcmeta",
                        Files.readAllBytes(mcmeta.toPath()));
            }
        }
    }

    // ---- texture resolution ----

    private static byte[] itemTexturePng(Context ctx, CustomItem item) throws IOException {
        String spec = item.getTextureSpec();
        if (spec != null && spec.startsWith("assets/")) {
            File file = new File(ctx.dataFolder(), spec);
            if (!file.isFile()) {
                ctx.logger().warning("Texture file '" + spec + "' not found for '" + item.getId()
                        + "' (looked at " + file.getAbsolutePath() + "), using a generated texture");
            } else {
                return scalePng(file, ctx.textureSize(), ctx.textureSize());
            }
        }
        TexSpec tex = resolveSpec(spec != null ? spec : defaultSpec(item.getId()));
        int[] px = TextureGenerator.generate(ctx.textureSize(), tex.pattern(), tex.c1(), tex.c2(), tex.outline());
        return PngWriter.write(ctx.textureSize(), ctx.textureSize(), px);
    }

    private static byte[] armorLayerPng(Context ctx, CustomItem item, boolean upper) throws IOException {
        String spec = item.getTextureSpec();
        if (spec != null && spec.startsWith("assets/")) {
            File file = new File(ctx.dataFolder(), spec);
            if (file.isFile()) {
                return scalePng(file, 64, 32);
            }
        }
        TexSpec tex = resolveSpec(spec != null ? spec : defaultSpec(item.getId()));
        int[] px = TextureGenerator.armorLayer(tex.pattern(), tex.c1(), tex.c2(), tex.outline());
        return PngWriter.write(64, 32, px);
    }

    private static byte[] scalePng(File file, int w, int h) throws IOException {
        BufferedImage img;
        try {
            img = ImageIO.read(file);
        } catch (java.lang.NoClassDefFoundError e) {
            // java.desktop (ImageIO) unavailable on this JVM; treat as unreadable.
            throw new IOException("image decoding unavailable on this JVM", e);
        }
        if (img == null) {
            throw new IOException("not a readable image: " + file.getName());
        }
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return PngWriter.write(w, h, scaled.getRGB(0, 0, w, h, null, 0, w));
    }

    private static TexSpec resolveSpec(String spec) {
        String[] parts = spec.split("\\|");
        TextureGenerator.Pattern pattern = TextureGenerator.Pattern.GRADIENT;
        String color = parts.length > 1 ? parts[1] : parts[0];
        String color2 = parts.length > 2 && !parts[2].isEmpty() ? parts[2] : color;
        boolean outline = parts.length <= 3 || Boolean.parseBoolean(parts[3]);
        if (parts.length > 0) {
            try {
                pattern = TextureGenerator.Pattern.valueOf(parts[0].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                pattern = TextureGenerator.Pattern.SOLID;
            }
        }
        return new TexSpec(pattern,
                TextureGenerator.parseColor(color),
                TextureGenerator.parseColor(color2),
                outline);
    }

    /** Pick a deterministic pleasant gradient based on the item id. */
    static String defaultSpec(String id) {
        int hue = (id.hashCode() & 0x7fffffff) % 360;
        int c1 = hslToRgb(hue, 0.55f, 0.55f);
        int c2 = hslToRgb((hue + 40) % 360, 0.60f, 0.30f);
        return "gradient|" + toHex(c1) + "|" + toHex(c2) + "|true";
    }

    private static int hslToRgb(float h, float s, float l) {
        float c = (1 - Math.abs(2 * l - 1)) * s;
        float x = c * (1 - Math.abs((h / 60f) % 2 - 1));
        float m = l - c / 2;
        float r = 0, g = 0, b = 0;
        if (h < 60) {
            r = c; g = x;
        } else if (h < 120) {
            r = x; g = c;
        } else if (h < 180) {
            g = c; b = x;
        } else if (h < 240) {
            g = x; b = c;
        } else if (h < 300) {
            r = x; b = c;
        } else {
            r = c; b = x;
        }
        int ri = Math.round((r + m) * 255);
        int gi = Math.round((g + m) * 255);
        int bi = Math.round((b + m) * 255);
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }

    private static String toHex(int argb) {
        return String.format("#%06x", argb & 0xFFFFFF);
    }

    /** Read an imported model, falling back to a generated model (with a warning) when missing. */
    private static String readModelOrFallback(Context ctx, CustomItem item) {
        File file = new File(ctx.dataFolder(), item.getModelFile());
        if (file.isFile()) {
            try {
                return Files.readString(file.toPath(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                ctx.logger().warning("Could not read model '" + item.getModelFile() + "': " + e.getMessage());
            }
        } else {
            ctx.logger().warning("Model file '" + item.getModelFile() + "' not found for '" + item.getId()
                    + "' (looked at " + file.getAbsolutePath() + "), using a generated model");
        }
        return Json.obj("parent", item.getType() == CustomItemType.WEAPON
                ? "minecraft:item/handheld" : "minecraft:item/generated",
                "textures", Map.of("layer0", ctx.namespace() + ":item/" + item.getId()));
    }

    // ---- entry helpers ----

    private static void put(Map<String, byte[]> entries, String path, byte[] data) {
        entries.put(path, data);
    }

    private static void putString(Map<String, byte[]> entries, String path, String content) {
        entries.put(path, content.getBytes(StandardCharsets.UTF_8));
    }
}
