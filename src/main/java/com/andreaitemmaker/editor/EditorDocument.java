package com.andreaitemmaker.editor;

import com.andreaitemmaker.api.CustomItemType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The editor's working copy of one content YAML file.
 *
 * <p>The file on disk is never changed by editing the GUI: {@link #load} keeps an immutable
 * snapshot of the original text, all edits happen in the in-memory {@link YamlConfiguration},
 * and {@link #isDirty()} compares the two. Keys the editor does not know about are part of that
 * in-memory copy, so they survive a save untouched — the editor never rewrites a file from a
 * schema-shaped model.
 */
public final class EditorDocument {

    /** How many edit steps one session can step back through. Bounded so history stays cheap. */
    public static final int MAX_HISTORY = 50;

    private File file;
    private YamlConfiguration working;
    private String originalText;
    private boolean brandNew;

    /** Serialized states to step back to, newest last (the state *before* each recorded action). */
    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();
    /** The state when the current user action started, or null while no action is open. */
    private String actionStart;
    /** Nesting depth of the open action, so an inner bracket cannot close the outer one early. */
    private int actionDepth;
    /** Set by {@link #undo()}/{@link #redo()} so the enclosing action is not recorded itself. */
    private boolean skipRecord;

    private EditorDocument(File file, YamlConfiguration working, String originalText, boolean brandNew) {
        this.file = file;
        this.working = working;
        this.originalText = originalText;
        this.brandNew = brandNew;
    }

    /** Load an existing content file as a working copy. */
    public static EditorDocument load(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return new EditorDocument(file, yaml, yaml.saveToString(), false);
    }

    /**
     * Load an existing file as a working copy bound to a different target file (used to
     * duplicate content): every key, value and comment of the source is carried over and only
     * the id is changed by the caller.
     */
    public static EditorDocument loadAs(File source, File target) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source);
        return new EditorDocument(target, yaml, null, true);
    }

    /** Create a brand-new working copy (not written yet) for the given type and id. */
    public static EditorDocument create(File file, CustomItemType type, String id) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", id);
        yaml.set("type", type.name());
        yaml.set("material", defaultMaterial(type).name());
        yaml.set("display-name", "&f" + humanize(id));
        if (type == CustomItemType.BLOCK) {
            yaml.set("base-block", Material.WHITE_WOOL.name());
        }
        return new EditorDocument(file, yaml, null, true);
    }

    public File file() {
        return file;
    }

    /**
     * Point the document at a different file. Used when the id changed: the entry must be written
     * under the file name that matches its id.
     */
    public void retarget(File file) {
        if (file != null) {
            this.file = file;
        }
    }

    public YamlConfiguration yaml() {
        return working;
    }

    /** The entry id: the explicit {@code id} key, or the file name without its extension. */
    public String id() {
        String explicit = working.getString("id");
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /** The {@code type:} key, or null when absent or unrecognised. */
    public CustomItemType declaredType() {
        return com.andreaitemmaker.config.ContentLoader.parseTypeOrNull(working.getString("type"));
    }

    public boolean isDirty() {
        if (brandNew) {
            return true;
        }
        return !working.saveToString().equals(originalText);
    }

    /** True for a document that has never been written to disk. */
    public boolean isNew() {
        return brandNew;
    }

    /**
     * Called after a successful save so the working copy becomes the new baseline. The undo
     * history is deliberately kept: undoing a save is a legitimate (and useful) thing to do.
     */
    public void markSaved() {
        this.originalText = working.saveToString();
        this.brandNew = false;
    }

    // ---- undo history ----

    /**
     * Start recording one user action. Everything a single click or accepted chat input changes is
     * collapsed into a single undo step — internal sub-writes (a section plus its keys) never show
     * up as several invisible steps. Safe to call again while an action is already open.
     */
    public void beginAction() {
        if (actionDepth++ == 0) {
            actionStart = working.saveToString();
        }
    }

    /**
     * Finish the current user action. The state from {@link #beginAction()} is pushed only when the
     * action actually changed something, so navigating menus never fills the history with no-ops.
     */
    public void endAction() {
        if (actionDepth == 0) {
            return;
        }
        if (--actionDepth > 0) {
            return;
        }
        String start = actionStart;
        actionStart = null;
        if (start == null) {
            return;
        }
        if (skipRecord) {
            skipRecord = false;
            return;
        }
        if (start.equals(working.saveToString())) {
            return;
        }
        pushHistory(undoStack, start);
        redoStack.clear();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /** Steps still available to undo / redo, for the GUI. */
    public int undoDepth() {
        return undoStack.size();
    }

    public int redoDepth() {
        return redoStack.size();
    }

    /** Step one action back. Returns false when there is nothing to undo. */
    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }
        String current = working.saveToString();
        applySnapshot(undoStack.removeLast());
        pushHistory(redoStack, current);
        // Only suppress recording when this undo happens inside an open action; called on its own
        // there is no action to keep out of the history.
        skipRecord = actionStart != null;
        return true;
    }

    /** Step one action forward again. Returns false when there is nothing to redo. */
    public boolean redo() {
        if (redoStack.isEmpty()) {
            return false;
        }
        String current = working.saveToString();
        applySnapshot(redoStack.removeLast());
        pushHistory(undoStack, current);
        skipRecord = actionStart != null;
        return true;
    }

    /**
     * Re-read the file into this document (the editor's revert). The document object — and with it
     * the undo history — is kept, and the pre-revert state is recorded, so a revert can be undone
     * like any other edit.
     *
     * @return false when the file no longer exists or cannot be read
     */
    public boolean reloadFromDisk() {
        if (file == null || !file.isFile()) {
            return false;
        }
        String text;
        try {
            text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return false;
        }
        String current = working.saveToString();
        applySnapshot(text);
        String fromFile = working.saveToString();
        if (!current.equals(fromFile)) {
            pushHistory(undoStack, current);
            redoStack.clear();
        }
        this.originalText = fromFile;
        this.brandNew = false;
        // The revert is already recorded above; the enclosing user action must not record it again.
        skipRecord = actionStart != null;
        return true;
    }

    private void pushHistory(Deque<String> stack, String state) {
        if (state == null) {
            return;
        }
        stack.addLast(state);
        while (stack.size() > MAX_HISTORY) {
            stack.removeFirst();
        }
    }

    /**
     * Replace the working copy with a previously serialized state. The whole configuration is
     * re-parsed rather than copied key by key: that keeps nested structures exactly as they are
     * (including comments and any key the editor does not manage).
     */
    private void applySnapshot(String yaml) {
        this.working = YamlConfiguration.loadConfiguration(new StringReader(yaml));
    }

    public Object get(String path) {
        return working.get(path);
    }

    public String getString(String path) {
        return working.getString(path);
    }

    public String getString(String path, String def) {
        return working.getString(path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        return working.getBoolean(path, def);
    }

    public boolean contains(String path) {
        return working.contains(path);
    }

    public boolean isSection(String path) {
        return working.isConfigurationSection(path);
    }

    public boolean isList(String path) {
        return working.isList(path);
    }

    public void set(String path, Object value) {
        working.set(path, value);
    }

    public void remove(String path) {
        working.set(path, null);
    }

    /** All keys of a section (empty for a missing section). */
    public List<String> keys(String path) {
        if (path.isEmpty()) {
            return new ArrayList<>(working.getKeys(false));
        }
        ConfigurationSection section = working.getConfigurationSection(path);
        return section == null ? List.of() : new ArrayList<>(section.getKeys(false));
    }

    /** The serialized document, exactly as it would be written. */
    public String toYaml() {
        return working.saveToString();
    }

    /**
     * Top-level keys present in the file that the editor does not manage for this type. They are
     * preserved on save; this list exists purely so the admin can see that they were noticed.
     */
    public Set<String> unknownTopLevelKeys(CustomItemType type) {
        Set<String> known = EditorSchema.managedTopLevelKeys(type);
        Set<String> unknown = new TreeSet<>();
        for (String key : working.getKeys(false)) {
            if (!known.contains(key)) {
                unknown.add(key);
            }
        }
        return unknown;
    }

    private static Material defaultMaterial(CustomItemType type) {
        return switch (type) {
            case ITEM -> Material.PAPER;
            case WEAPON -> Material.IRON_SWORD;
            case ARMOR -> Material.IRON_HELMET;
            case FOOD -> Material.APPLE;
            case BLOCK, FURNITURE -> Material.STICK;
        };
    }

    private static String humanize(String id) {
        String[] words = id.split("[_.-]");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.length() == 0 ? id : sb.toString();
    }
}
