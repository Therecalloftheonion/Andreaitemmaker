package com.andreaitemmaker.editor;

import com.andreaitemmaker.AndreaitemmakerPlugin;
import com.andreaitemmaker.api.CustomItemType;
import com.andreaitemmaker.config.ContentLoader;
import com.andreaitemmaker.util.AssetPaths;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * All filesystem access for the editor: the content index, creating/duplicating/deleting files,
 * atomic saves with backups, and the scratch workspace used for authoritative validation.
 *
 * <p>Every path is resolved through {@link AssetPaths} against the plugin's data folder, so an
 * id or path coming from the GUI can never escape the plugin folder.
 *
 * <p>The content index is cached: opening a category does not re-read the whole content folder
 * on every click. It is invalidated after a save/delete and on plugin reload.
 */
public final class EditorRepository {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final AndreaitemmakerPlugin plugin;
    private volatile List<EditorEntry> cache;

    public EditorRepository(AndreaitemmakerPlugin plugin) {
        this.plugin = plugin;
    }

    public File dataFolder() {
        return plugin.getDataFolder();
    }

    /** All content entries, sorted by type then id. Cached until {@link #invalidate()}. */
    public List<EditorEntry> entries() {
        List<EditorEntry> cached = cache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (cache != null) {
                return cache;
            }
            List<EditorEntry> out = new ArrayList<>();
            out.addAll(scan("items", null));
            out.addAll(scan("blocks", CustomItemType.BLOCK));
            out.addAll(scan("furniture", CustomItemType.FURNITURE));
            out.sort(Comparator.<EditorEntry, String>comparing(e -> e.type().name())
                    .thenComparing(EditorEntry::id));
            cache = List.copyOf(out);
            return cache;
        }
    }

    public List<EditorEntry> entries(CustomItemType type) {
        List<EditorEntry> out = new ArrayList<>();
        for (EditorEntry entry : entries()) {
            if (entry.type() == type) {
                out.add(entry);
            }
        }
        return List.copyOf(out);
    }

    /** Look up one entry by id (exact, then case-insensitive). */
    public EditorEntry find(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String needle = id.trim();
        for (EditorEntry entry : entries()) {
            if (entry.id().equals(needle)) {
                return entry;
            }
        }
        for (EditorEntry entry : entries()) {
            if (entry.id().equalsIgnoreCase(needle)) {
                return entry;
            }
        }
        return null;
    }

    /** Whether an id is already taken by a different entry. */
    public boolean idTaken(String id, String exceptId) {
        EditorEntry entry = find(id);
        return entry != null && (exceptId == null || !entry.id().equals(exceptId));
    }

    public void invalidate() {
        cache = null;
    }

    /** The file a new/existing entry of this type and id lives in. */
    public File fileFor(CustomItemType type, String id) {
        return new File(new File(dataFolder(), ContentLoader.folderFor(type)), id + ".yml");
    }

    /**
     * Scratch file used to validate/preview unsaved changes. It lives in {@code .editor/}, which
     * the loader never scans, and the name is sanitized so an id can never escape the folder.
     *
     * @return the scratch file, or null when the workspace could not be created
     */
    public File newScratchFile(String id) {
        File dir = new File(dataFolder(), ".editor");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create the editor workspace " + dir.getPath());
            return null;
        }
        String safe = id == null ? "entry" : id.replaceAll("[^a-zA-Z0-9_.-]", "_");
        if (safe.isEmpty() || safe.equals(".") || safe.equals("..")) {
            safe = "entry";
        }
        return safeResolve(new File(dir, safe + ".yml"));
    }

    /**
     * Write a working copy to disk: backups the previous file, writes to a temporary sibling and
     * moves it into place, so a crash mid-write can never leave a truncated content file.
     *
     * @return true when the file now contains the document
     */
    public boolean write(EditorDocument document, CustomItemType type) {
        File target = safeResolve(document.file());
        if (target == null) {
            plugin.getLogger().warning("Refused to write outside the plugin folder: " + document.file());
            return false;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return false;
        }
        if (target.isFile() && backup(target, "backups/editor") == null) {
            plugin.getLogger().warning("Could not back up " + target.getName() + "; save aborted");
            return false;
        }
        String body = document.toYaml();
        if (document.isNew()) {
            body = headerComment(type) + body;
        }
        File temp = new File(parent, target.getName() + ".tmp");
        try {
            Files.writeString(temp.toPath(), body, StandardCharsets.UTF_8);
            move(temp.toPath(), target.toPath());
        } catch (IOException | UncheckedIOException e) {
            temp.delete();
            plugin.getLogger().warning("Could not save " + target.getName() + ": " + e.getMessage());
            return false;
        }
        invalidate();
        return true;
    }

    /** Delete a content file, keeping a recoverable copy in {@code backups/editor/deleted}. */
    public boolean delete(EditorEntry entry) {
        File target = safeResolve(entry.file());
        if (target == null) {
            return false;
        }
        if (backup(target, "backups/editor/deleted") == null) {
            plugin.getLogger().warning("Could not back up " + target.getName() + "; delete aborted");
            return false;
        }
        try {
            Files.deleteIfExists(target.toPath());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not delete " + target.getName() + ": " + e.getMessage());
            return false;
        }
        invalidate();
        return true;
    }

    /** Duplicate a content file under a new id (the new document is written immediately). */
    public EditorDocument duplicate(EditorEntry source, String newId) {
        File target = safeResolve(fileFor(source.type(), newId));
        if (target == null || target.exists()) {
            return null;
        }
        // Start from the source document's own YAML (unknown keys and comments survive), then
        // stamp the new identity. The base block is cleared because one base backs one block.
        EditorDocument copy = EditorDocument.loadAs(source.file(), target);
        copy.set("id", newId);
        copy.set("type", source.type().name());
        if (source.type() == CustomItemType.BLOCK) {
            copy.remove("base-block");
        }
        return copy;
    }

    /** Copy a file into {@code backups/editor[...]} with a timestamp. Returns the copy, or null. */
    public File backup(File source, String subfolder) {
        File target = safeResolve(source);
        if (target == null || !target.isFile()) {
            return null;
        }
        File dir = new File(dataFolder(), subfolder);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return null;
        }
        String stamp = LocalDateTime.now().format(STAMP);
        File copy = new File(dir, target.getName() + "." + stamp + ".bak");
        try {
            Files.copy(target.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return copy;
        } catch (IOException e) {
            plugin.getLogger().warning("Backup failed for " + target.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Resolve a file against the plugin data folder through the centralized
     * {@link AssetPaths} resolver. Returns null when the file is not inside the data folder.
     */
    public File safeResolve(File file) {
        if (file == null) {
            return null;
        }
        try {
            Path base = dataFolder().getCanonicalFile().toPath();
            Path target = file.getCanonicalFile().toPath();
            Path relative = base.relativize(target);
            if (relative.isAbsolute() || relative.startsWith("..")) {
                return null;
            }
            return AssetPaths.resolve(dataFolder(), relative.toString().replace('\\', '/'));
        } catch (IOException e) {
            return null;
        }
    }

    private List<EditorEntry> scan(String folderName, CustomItemType forcedType) {
        File folder = new File(dataFolder(), folderName);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) {
            return List.of();
        }
        Arrays.sort(files);
        List<EditorEntry> out = new ArrayList<>(files.length);
        for (File file : files) {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String name = file.getName();
                String id = yaml.getString("id", name.substring(0, name.lastIndexOf('.')));
                CustomItemType type = forcedType != null
                        ? forcedType
                        : (yaml.getString("type") == null ? CustomItemType.ITEM
                        : ContentLoader.parseTypeOrNull(yaml.getString("type")));
                if (type == null) {
                    type = CustomItemType.ITEM;
                }
                String materialName = yaml.getString("material");
                Material material = materialName == null ? null
                        : Material.matchMaterial(materialName.trim().toUpperCase(Locale.ROOT));
                String baseName = yaml.getString("base-block");
                Material baseBlock = baseName == null ? null
                        : Material.matchMaterial(baseName.trim().toUpperCase(Locale.ROOT));
                Object cmd = yaml.get("custom-model-data");
                Integer customModelData = cmd instanceof Number number ? number.intValue() : null;
                out.add(new EditorEntry(id, type, file, material, yaml.getString("display-name"),
                        baseBlock, customModelData));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Editor could not read " + file.getName() + ": " + e.getMessage());
            }
        }
        return out;
    }

    private String headerComment(CustomItemType type) {
        StringBuilder sb = new StringBuilder();
        for (String line : EditorSchema.newFileComments(type)) {
            sb.append("# ").append(line).append('\n');
        }
        return sb.toString();
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (UnsupportedOperationException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Some filesystems refuse ATOMIC_MOVE across mounts; fall back to a plain replace
            // rather than throwing away the work.
            try {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException inner) {
                throw new UncheckedIOException(inner);
            }
        }
    }
}
