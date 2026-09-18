package com.andreaitemmaker.editor;

import com.andreaitemmaker.AndreaitemmakerPlugin;
import com.andreaitemmaker.api.CustomItem;
import com.andreaitemmaker.api.CustomItemType;
import com.andreaitemmaker.config.ContentLoader;
import com.andreaitemmaker.config.PluginConfig;
import com.andreaitemmaker.editor.menu.ChoiceMenu;
import com.andreaitemmaker.editor.menu.ContentEditorMenu;
import com.andreaitemmaker.editor.menu.ContentListMenu;
import com.andreaitemmaker.editor.menu.EditorMenu;
import com.andreaitemmaker.editor.menu.KnownKeyMapMenu;
import com.andreaitemmaker.editor.menu.MainMenu;
import com.andreaitemmaker.editor.menu.MaterialPickerMenu;
import com.andreaitemmaker.editor.menu.MechanicsMenu;
import com.andreaitemmaker.editor.menu.TextureMenu;
import com.andreaitemmaker.editor.menu.UnsavedChangesMenu;
import com.andreaitemmaker.editor.menu.ValidationMenu;
import com.andreaitemmaker.content.ItemFactory;
import com.andreaitemmaker.pack.TextureGenerator;
import com.andreaitemmaker.util.AssetPaths;
import com.andreaitemmaker.util.Chat;
import com.andreaitemmaker.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

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
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * The editor's orchestrator: owns the per-player sessions, opens menus, handles clicks, chat
 * input and saving.
 *
 * <p>Session state is never static and never global: one {@link EditorSession} per player, keyed
 * by UUID, so simultaneous editors cannot interfere. Sessions are removed when the player quits
 * and cleaned up when they go idle, and a pending chat prompt is dropped after two minutes or as
 * soon as the player navigates elsewhere.
 */
public final class EditorManager {

    private static final long CLEANUP_INTERVAL_TICKS = 600L;
    private static final long SESSION_IDLE_MILLIS = 10 * 60_000L;

    private final AndreaitemmakerPlugin plugin;
    private final EditorRepository repository;
    private final Map<UUID, EditorSession> sessions = new ConcurrentHashMap<>();
    private int cleanupTaskId = -1;
    private volatile boolean shuttingDown;

    public EditorManager(AndreaitemmakerPlugin plugin) {
        this.plugin = plugin;
        this.repository = new EditorRepository(plugin);
    }

    public AndreaitemmakerPlugin plugin() {
        return plugin;
    }

    public EditorRepository repository() {
        return repository;
    }

    /** Register the interaction listener and start the idle-session cleanup. */
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(new EditorListener(this), plugin);
        cleanupTaskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::cleanupSessions, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS)
                .getTaskId();
    }

    /** Stop everything: no listener work, no sessions, no cached content index. */
    public void shutdown() {
        shuttingDown = true;
        if (cleanupTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(cleanupTaskId);
            cleanupTaskId = -1;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            EditorSession session = sessions.get(player.getUniqueId());
            if (session != null && session.currentMenu() != null
                    && player.getOpenInventory().getTopInventory().getHolder() instanceof EditorMenu) {
                player.closeInventory();
            }
        }
        sessions.clear();
        repository.invalidate();
    }

    private void cleanupSessions() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, EditorSession> entry : sessions.entrySet()) {
            EditorSession session = entry.getValue();
            TextInputRequest pending = session.pendingInput();
            if (pending != null && pending.expired(now)) {
                session.clearPendingInput();
            }
            boolean busy = session.isDirty() || session.pendingInput() != null
                    || session.currentMenu() != null;
            if (!busy && now - session.lastActivity() > SESSION_IDLE_MILLIS) {
                sessions.remove(entry.getKey(), session);
            }
        }
    }

    // ---- sessions ----

    public EditorSession openSession(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), EditorSession::new);
    }

    public void handleQuit(Player player) {
        EditorSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.clearPendingInput();
            session.setCurrentMenu(null);
        }
    }

    // ---- menus ----

    /** Open a menu: renders it first, remembers it as current, then shows it. */
    public void open(Player player, EditorMenu menu) {
        EditorSession session = openSession(player);
        session.clearPendingInput();
        session.setCurrentMenu(menu);
        session.touch();
        menu.refresh();
        player.openInventory(menu.getInventory());
    }

    public void openMain(Player player) {
        open(player, new MainMenu(this, openSession(player)));
    }

    public void openList(Player player, CustomItemType type, EditorMenu parent) {
        open(player, new ContentListMenu(this, openSession(player), parent, type, 0, ""));
    }

    /** Search across every category (used by the command and the search prompt). */
    public void openSearch(Player player, String query) {
        open(player, new ContentListMenu(this, openSession(player), null, null, 0, query));
    }

    public void handleClick(Player player, EditorMenu menu, int slot, ClickType click) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null || menu != session.currentMenu()) {
            return;
        }
        session.touch();
        // One click = one undo step: everything the handler writes is grouped into a single action.
        EditorDocument document = session.document();
        if (document != null) {
            document.beginAction();
        }
        try {
            menu.onClick(player, slot, click);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Editor click failed for " + player.getName(), e);
            message(player, "&cSomething went wrong; see the console for the details.");
        } finally {
            if (document != null) {
                document.endAction();
            }
        }
    }

    /** Step one edit back (grouped per click / per accepted chat value). */
    public void undo(Player player, EditorSession session, EditorMenu menu) {
        EditorDocument document = session.document();
        if (document == null) {
            return;
        }
        if (!document.undo()) {
            message(player, "&7Nothing left to undo.");
            return;
        }
        message(player, "&eUndo &7(" + document.undoDepth() + " more step(s) available)");
        if (menu != null) {
            menu.refresh();
        }
    }

    /** Step one edit forward again, after an undo. */
    public void redo(Player player, EditorSession session, EditorMenu menu) {
        EditorDocument document = session.document();
        if (document == null) {
            return;
        }
        if (!document.redo()) {
            message(player, "&7Nothing left to redo.");
            return;
        }
        message(player, "&eRedo &7(" + document.redoDepth() + " more step(s) available)");
        if (menu != null) {
            menu.refresh();
        }
    }

    /**
     * A menu was closed by the player. Navigation (opening another menu) is distinguished from a
     * real close by comparing the closed menu with the session's current one, which is always
     * updated before a new inventory is shown.
     */
    public void handleClose(Player player, EditorMenu menu) {
        if (shuttingDown) {
            return;
        }
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null || menu != session.currentMenu()) {
            return;
        }
        session.setCurrentMenu(null);
        if (menu instanceof UnsavedChangesMenu) {
            menu.onClose(player);
            return;
        }
        if (session.isDirty()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && session.isDirty() && session.currentMenu() == null) {
                    open(player, new UnsavedChangesMenu(this, session, menu));
                }
            });
        } else {
            menu.onClose(player);
        }
    }

    public void message(Player player, String message) {
        player.sendMessage(Chat.color("&8[&bEditor&8] &r" + message));
    }

    // ---- chat input ----

    /** True when the message was consumed as editor input (the chat event must be cancelled). */
    public boolean handleChat(Player player, String message) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        TextInputRequest request = session.pendingInput();
        if (request == null) {
            return false;
        }
        if (request.expired(System.currentTimeMillis())) {
            session.clearPendingInput();
            return false;
        }
        // Chat is asynchronous: all player/inventory work happens back on the main thread.
        plugin.getServer().getScheduler().runTask(plugin, () -> completeInput(player, request, message));
        return true;
    }

    private void completeInput(Player player, TextInputRequest request, String message) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null || session.pendingInput() != request) {
            return;
        }
        session.touch();
        if (message.equalsIgnoreCase("cancel")) {
            session.clearPendingInput();
            message(player, "&eCancelled &f" + request.label() + "&e.");
            reopen(player, session, request);
            return;
        }
        String error = request.validator().apply(message);
        if (error != null) {
            message(player, "&c" + error + " &8(" + request.path() + ")");
            message(player, "&7Type the value again, or &fcancel&7.");
            return;
        }
        session.clearPendingInput();
        session.setCurrentMenu(null);
        EditorDocument document = session.document();
        if (document != null) {
            document.beginAction();
        }
        try {
            request.onAccept().accept(player, message);
        } finally {
            if (document != null) {
                document.endAction();
            }
        }
        // The consumer may have navigated itself (e.g. the search prompt); only fall back to the
        // return menu when it did not.
        if (session.currentMenu() == null) {
            reopen(player, session, request);
        }
    }

    private void reopen(Player player, EditorSession session, TextInputRequest request) {
        if (request.returnTo() != null) {
            open(player, request.returnTo());
        } else if (session.isEditing()) {
            open(player, new ContentEditorMenu(this, session, null, 0));
        } else {
            openMain(player);
        }
    }

    public void prompt(Player player, TextInputRequest request) {
        EditorSession session = openSession(player);
        session.setPendingInput(request);
        session.setCurrentMenu(null);
        player.closeInventory();
        for (String line : request.prompt()) {
            message(player, line);
        }
    }

    public void promptSearch(Player player, EditorMenu parent) {
        EditorSession session = openSession(player);
        prompt(player, new TextInputRequest("search", "search", List.of(
                "&eType the search text in chat.",
                "&7Type &fcancel&7 to go back."),
                input -> input.isBlank() ? "type at least one character" : null,
                (p, input) -> openSearch(p, input.trim()),
                parent, System.currentTimeMillis()));
    }

    public void promptMaterialFilter(Player player, EditorMenu parent, EditorField field,
                                     MaterialPickerMenu.Mode mode, int page) {
        prompt(player, new TextInputRequest(field.path(), field.label() + " filter", List.of(
                "&eType a filter for the material list.",
                "&7Type &fcancel&7 to go back."),
                input -> null,
                (p, input) -> open(p, new MaterialPickerMenu(this, openSession(p), parent, field, mode,
                        page, input.trim())),
                parent, System.currentTimeMillis()));
    }

    public void promptMaterialName(Player player, EditorMenu parent, EditorField field) {
        prompt(player, new TextInputRequest(field.path(), field.label(), List.of(
                "&eType the exact material name.",
                "&7e.g. &fdiamond_sword&7, &fwhite_wool&7.",
                "&7Type &fcancel&7 to go back."),
                input -> {
                    Material material = Material.matchMaterial(input.trim());
                    return material == null ? "unknown material '" + input.trim() + "'" : null;
                },
                (p, input) -> applyFieldValue(openSession(p), field,
                        input.trim().toLowerCase(Locale.ROOT)),
                parent, System.currentTimeMillis()));
    }

    public void promptTextureColor(Player player, EditorMenu parent, String path, String key) {
        prompt(player, new TextInputRequest(path, "texture " + key, List.of(
                "&eType a hex color for &f" + key + "&e (e.g. &f#4f7cff&e).",
                "&7Type &fcancel&7 to go back, or &fclear&7 to remove it."),
                input -> {
                    String value = input.trim();
                    if (value.isEmpty() || value.equalsIgnoreCase("clear")) {
                        return null;
                    }
                    try {
                        TextureGenerator.parseColor(value);
                        return null;
                    } catch (IllegalArgumentException e) {
                        return e.getMessage();
                    }
                },
                (p, input) -> {
                    EditorSession session = openSession(p);
                    // Written through the section node so the texture always becomes a real,
                    // readable YAML section rather than a raw map value.
                    EditorNode node = new EditorNode.SectionNode(session.document(), path);
                    String value = input.trim();
                    if (value.isEmpty() || value.equalsIgnoreCase("clear")) {
                        node.remove(key);
                    } else {
                        node.put(key, value);
                    }
                },
                parent, System.currentTimeMillis()));
    }

    public void promptTextureFile(Player player, EditorMenu parent, String path) {
        prompt(player, new TextInputRequest(path, "texture file", List.of(
                "&eType the path of a .png inside &fassets/textures/&e.",
                "&7e.g. &fassets/textures/my_item.png",
                "&7Type &fcancel&7 to go back."),
                input -> {
                    String value = input.trim().replace('\\', '/');
                    if (!value.endsWith(".png")) {
                        return "the path must end with .png";
                    }
                    return AssetPaths.isSafeAssetPath(value) ? null
                            : "the path must be a relative path inside assets/textures/";
                },
                (p, input) -> {
                    EditorSession session = openSession(p);
                    session.document().set(path, input.trim().replace('\\', '/'));
                },
                parent, System.currentTimeMillis()));
    }

    public void promptKeyedNumber(Player player, EditorMenu parent, String path, String key,
                                  boolean enchantments) {
        EditorSession session = openSession(player);
        Object current = session.document().get(path + "." + key);
        String label = (enchantments ? "level for " : "value for ") + key;
        prompt(player, new TextInputRequest(path + "." + key, label, List.of(
                "&eType the " + label + ".",
                enchantments ? "&7Whole number, e.g. &f3" : "&7Number, e.g. &f9.0",
                "&7Type &fcancel&7 to go back, or &fclear&7 to remove it."),
                input -> numberProblem(input.trim(), enchantments, current),
                (p, input) -> {
                    EditorSession s = openSession(p);
                    String value = input.trim();
                    if (value.isEmpty() || value.equalsIgnoreCase("clear")) {
                        s.document().remove(path + "." + key);
                    } else {
                        s.document().set(path + "." + key, parseNumber(value, enchantments));
                    }
                },
                parent, System.currentTimeMillis()));
    }

    public void promptNodeValue(Player player, EditorMenu parent, EditorNode node, String key,
                                Object current) {
        boolean booleanish = current instanceof Boolean;
        prompt(player, new TextInputRequest(key, key, List.of(
                "&eType the new value for &f" + key + "&e.",
                booleanish ? "&7e.g. &ftrue&7 / &ffalse" : "&7e.g. &f5&7, &f1.5&7, &ftext",
                "&7Type &fcancel&7 to go back, or &fclear&7 to remove it."),
                input -> nodeProblem(input.trim(), current),
                (p, input) -> {
                    String value = input.trim();
                    if (value.isEmpty() || value.equalsIgnoreCase("clear")) {
                        node.remove(key);
                    } else {
                        node.put(key, parseLike(value, current));
                    }
                },
                parent, System.currentTimeMillis()));
    }

    public void promptNodeEntry(Player player, EditorMenu parent, EditorNode node, String heading) {
        prompt(player, new TextInputRequest(heading, heading + " parameter", List.of(
                "&eType &fkey=value&7 for the new parameter.",
                "&7e.g. &fpower=1.5&7, &fparticles=false&7, &fsound=BLOCK_ANVIL_USE",
                "&7Type &fcancel&7 to go back."),
                input -> {
                    int eq = input.indexOf('=');
                    if (eq <= 0 || eq == input.length() - 1) {
                        return "use the form key=value";
                    }
                    String key = input.substring(0, eq).trim();
                    if (!key.matches("[A-Za-z0-9_.-]+")) {
                        return "the key may only contain letters, numbers, '_', '-' and '.'";
                    }
                    return null;
                },
                (p, input) -> {
                    int eq = input.indexOf('=');
                    String key = input.substring(0, eq).trim();
                    node.put(key, parseFree(input.substring(eq + 1).trim()));
                },
                parent, System.currentTimeMillis()));
    }

    public void promptListEntry(Player player, EditorMenu parent, EditorNode owner, String key,
                                int index, Object current) {
        boolean adding = index < 0;
        prompt(player, new TextInputRequest(key, key + " entry", List.of(
                adding ? "&eType the new value to add." : "&eType the new value.",
                "&7Type &fcancel&7 to go back."),
                input -> input.isBlank() ? "type a value" : null,
                (p, input) -> {
                    List<Object> list = new ArrayList<>();
                    Object existing = owner.entries().get(key);
                    if (existing instanceof List<?> raw) {
                        list.addAll(raw);
                    }
                    Object value = parseLike(input.trim(), current);
                    if (adding) {
                        list.add(value);
                    } else if (index >= 0 && index < list.size()) {
                        list.set(index, value);
                    }
                    owner.put(key, list);
                },
                parent, System.currentTimeMillis()));
    }

    // ---- field editing ----

    public void applyFieldValue(EditorSession session, EditorField field, Object value) {
        if (session.document() == null) {
            return;
        }
        if (value == null) {
            session.document().remove(field.path());
        } else {
            session.document().set(field.path(), value);
        }
    }

    /** Open the right control for a field, based on its kind. */
    public void openField(Player player, EditorMenu parent, EditorField field) {
        EditorSession session = openSession(player);
        EditorDocument doc = session.document();
        if (doc == null) {
            return;
        }
        switch (field.kind()) {
            case BOOLEAN -> {
                boolean value = doc.getBoolean(field.path(), false);
                doc.set(field.path(), !value);
                message(player, "&a" + field.label() + " &7is now " + (!value ? "&atrue" : "&cfalse") + "&7.");
                parent.refresh();
            }
            case MATERIAL -> new MaterialPickerMenu(this, session, parent, field, materialMode(session, field), 0, "")
                    .open(player);
            case BASE_BLOCK -> new MaterialPickerMenu(this, session, parent, field,
                    MaterialPickerMenu.Mode.BLOCK_BASE, 0, "").open(player);
            case TEXTURE -> new TextureMenu(this, session, parent, field.path()).open(player);
            case ASSET_PATH -> promptAssetPath(player, parent, field);
            case SOUND -> promptSound(player, parent, field);
            case ATTRIBUTES -> new KnownKeyMapMenu(this, session, parent, field.path(), false, 0).open(player);
            case ENCHANTMENTS -> new KnownKeyMapMenu(this, session, parent, field.path(), true, 0).open(player);
            case MECHANICS -> new MechanicsMenu(this, session, parent, 0).open(player);
            case ENUM -> new ChoiceMenu(this, session, parent, field).open(player);
            case TEXT_LIST -> new com.andreaitemmaker.editor.menu.ListEditorMenu(this, session, parent,
                    new EditorNode.SectionNode(doc, ""), field.path(), field.label(), 0).open(player);
            case INT -> promptNumber(player, parent, field, true);
            case DOUBLE -> promptNumber(player, parent, field, false);
            case STRING, COLORED_STRING -> promptString(player, parent, field);
        }
    }

    private MaterialPickerMenu.Mode materialMode(EditorSession session, EditorField field) {
        if (session.type() == CustomItemType.ARMOR && field.path().equals("material")) {
            return MaterialPickerMenu.Mode.ARMOR;
        }
        return MaterialPickerMenu.Mode.ITEM;
    }

    private void promptString(Player player, EditorMenu parent, EditorField field) {
        EditorSession session = openSession(player);
        prompt(player, new TextInputRequest(field.path(), field.label(), List.of(
                "&eType the " + field.label().toLowerCase(Locale.ROOT) + " for &f"
                        + session.document().id() + "&e.",
                field.kind() == FieldKind.COLORED_STRING
                        ? "&7'&' color codes work, e.g. &f&bStorm Blade"
                        : "&7Type the value in chat.",
                field.required() ? "&7This field is required."
                        : "&7Type &fcancel&7 to go back, or &fclear&7 to remove it."),
                input -> field.required() && input.trim().isEmpty() && !input.trim().equalsIgnoreCase("clear")
                        ? "a value is required" : null,
                (p, input) -> {
                    EditorSession s = openSession(p);
                    String value = input.trim();
                    if (!field.required() && (value.isEmpty() || value.equalsIgnoreCase("clear"))) {
                        s.document().remove(field.path());
                    } else {
                        s.document().set(field.path(), input);
                    }
                },
                parent, System.currentTimeMillis()));
    }

    private void promptNumber(Player player, EditorMenu parent, EditorField field, boolean integer) {
        EditorSession session = openSession(player);
        Object current = session.document().get(field.path());
        prompt(player, new TextInputRequest(field.path(), field.label(), List.of(
                "&eType the " + field.label().toLowerCase(Locale.ROOT) + " for &f"
                        + session.document().id() + "&e.",
                "&7Allowed: &f" + trim(field.min()) + " &7to &f" + trim(field.max()),
                current == null ? "" : "&7Current: &f" + current,
                "&7Type &fcancel&7 to go back, or &fclear&7 to remove it."),
                input -> {
                    String value = input.trim();
                    if (value.isEmpty() || value.equalsIgnoreCase("clear")) {
                        return null;
                    }
                    try {
                        double number = Double.parseDouble(value);
                        if (integer && number != Math.floor(number)) {
                            return "must be a whole number";
                        }
                        if (number < field.min() || number > field.max()) {
                            return "must be between " + trim(field.min()) + " and " + trim(field.max());
                        }
                        return null;
                    } catch (NumberFormatException e) {
                        return "must be a number";
                    }
                },
                (p, input) -> {
                    EditorSession s = openSession(p);
                    String value = input.trim();
                    if (value.isEmpty() || value.equalsIgnoreCase("clear")) {
                        s.document().remove(field.path());
                    } else if (integer) {
                        s.document().set(field.path(), (int) Double.parseDouble(value));
                    } else {
                        s.document().set(field.path(), Double.parseDouble(value));
                    }
                },
                parent, System.currentTimeMillis()));
    }

    private void promptSound(Player player, EditorMenu parent, EditorField field) {
        prompt(player, new TextInputRequest(field.path(), field.label(), List.of(
                "&eType the sound name for &f" + field.label().toLowerCase(Locale.ROOT) + "&e.",
                "&7e.g. &fblock.wood.place&7, &fentity.player.levelup",
                "&7Type &fcancel&7 to go back, or &fclear&7 to use the default."),
                input -> {
                    String value = input.trim();
                    if (value.isEmpty() || value.equalsIgnoreCase("clear")) {
                        return null;
                    }
                    return Sounds.parse(value) == null ? "unknown sound '" + value + "'" : null;
                },
                (p, input) -> {
                    EditorSession s = openSession(p);
                    String value = input.trim();
                    if (value.isEmpty() || value.equalsIgnoreCase("clear")) {
                        s.document().remove(field.path());
                    } else {
                        s.document().set(field.path(), value);
                    }
                },
                parent, System.currentTimeMillis()));
    }

    private void promptAssetPath(Player player, EditorMenu parent, EditorField field) {
        boolean json = field.path().equals("model");
        prompt(player, new TextInputRequest(field.path(), field.label(), List.of(
                "&eType the path of your " + (json ? ".json model" : ".png texture") + ".",
                "&7Inside &f" + (json ? "assets/models/" : "assets/textures/")
                        + "&7, e.g. &fassets/" + (json ? "models/my_model.json" : "textures/my_item.png"),
                "&7Type &fcancel&7 to go back, or &fclear&7 to remove it."),
                input -> {
                    String value = input.trim().replace('\\', '/');
                    if (value.isEmpty() || value.equalsIgnoreCase("clear")) {
                        return null;
                    }
                    if (!value.endsWith(json ? ".json" : ".png")) {
                        return "the path must end with " + (json ? ".json" : ".png");
                    }
                    return AssetPaths.isSafeAssetPath(value) ? null
                            : "the path must be a relative path inside the assets folder";
                },
                (p, input) -> {
                    EditorSession s = openSession(p);
                    String value = input.trim().replace('\\', '/');
                    if (value.isEmpty() || value.equalsIgnoreCase("clear")) {
                        s.document().remove(field.path());
                    } else {
                        s.document().set(field.path(), value);
                    }
                },
                parent, System.currentTimeMillis()));
    }

    // ---- content actions ----

    public void edit(Player player, EditorEntry entry, EditorMenu parent) {
        EditorSession session = openSession(player);
        if (session.isDirty() && session.document() != null) {
            message(player, "&eUnsaved changes to &f" + session.document().id()
                    + " &ewere discarded because you opened another entry.");
        }
        try {
            EditorDocument document = EditorDocument.load(entry.file());
            session.setDocument(document, entry.type());
            session.setOriginal(entry);
            open(player, new ContentEditorMenu(this, session, parent, 0));
        } catch (RuntimeException e) {
            message(player, "&cCould not read " + entry.file().getName() + ": " + e.getMessage());
        }
    }

    public void createNew(Player player, CustomItemType type, EditorMenu parent) {
        openSession(player);
        prompt(player, new TextInputRequest("id", "new content id", List.of(
                "&eType the id for the new " + EditorSchema.singular(type) + ".",
                "&7Lowercase letters, numbers, '_', '-' and '.',",
                "&7e.g. &flighting_sword",
                "&7Type &fcancel&7 to go back."),
                input -> idProblem(input.trim(), null),
                (p, input) -> {
                    EditorSession session = openSession(p);
                    String id = input.trim();
                    File file = repository.fileFor(type, id);
                    EditorDocument document = EditorDocument.create(file, type, id);
                    session.setDocument(document, type);
                    session.setOriginal(null);
                    open(p, new ContentEditorMenu(this, session, null, 0));
                },
                parent, System.currentTimeMillis()));
    }

    public void duplicate(Player player, EditorEntry entry, EditorMenu parent) {
        openSession(player);
        prompt(player, new TextInputRequest("id", "copy id", List.of(
                "&eType the id for the copy of &f" + entry.id() + "&e.",
                "&7Type &fcancel&7 to go back."),
                input -> idProblem(input.trim(), entry.id()),
                (p, input) -> {
                    EditorSession session = openSession(p);
                    EditorDocument copy = repository.duplicate(entry, input.trim());
                    if (copy == null) {
                        message(p, "&cCould not copy the file (does it already exist?).");
                        return;
                    }
                    session.setDocument(copy, entry.type());
                    session.setOriginal(null);
                    message(p, "&7Copied. Adjust the values and press Save to create the file.");
                    open(p, new ContentEditorMenu(this, session, null, 0));
                },
                parent, System.currentTimeMillis()));
    }

    /** Duplicate the entry currently being edited (used by the editor's own toolbar). */
    public void duplicate(Player player, EditorSession session) {
        if (session.document() == null) {
            return;
        }
        EditorEntry entry = session.original() != null
                ? session.original()
                : repository.find(session.document().id());
        if (entry == null) {
            error(player, "save the entry first, then duplicate it");
            return;
        }
        duplicate(player, entry, session.currentMenu());
    }

    public void delete(Player player, EditorEntry entry, EditorMenu parent) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (repository.delete(entry)) {
            message(player, "&aDeleted &f" + entry.id() + "&a (a copy is kept in &fbackups/editor/deleted&a).");
            if (session != null && session.document() != null
                    && entry.file().getAbsolutePath().equals(session.document().file().getAbsolutePath())) {
                session.setDocument(null, session.type());
                session.setOriginal(null);
            }
            plugin.reloadAll();
            open(player, new ContentListMenu(this, openSession(player), parent, entry.type(), 0, ""));
        } else {
            error(player, "could not delete " + entry.file().getName() + ", see the console");
        }
    }

    public void inspect(Player player, EditorEntry entry) {
        message(player, "&b" + entry.id() + " &7(" + entry.type().name().toLowerCase(Locale.ROOT) + ")");
        message(player, " &8- &7file: &f" + entry.file().getName());
        message(player, " &8- &7material: &f" + (entry.material() == null
                ? "missing" : entry.material().name().toLowerCase(Locale.ROOT)));
        if (entry.baseBlock() != null) {
            message(player, " &8- &7base block: &f" + entry.baseBlock().name().toLowerCase(Locale.ROOT));
        }
        if (entry.displayName() != null) {
            message(player, " &8- &7display name: &r" + entry.displayName());
        }
        CustomItem loaded = plugin.getContentRegistry().getItem(entry.id());
        message(player, " &8- &7loaded by the plugin: "
                + (loaded == null ? "&cnot currently loaded" : "&ayes"));
        message(player, "&7Use &f/aitem give " + entry.id() + "&7 to check it in game.");
    }

    /** Validate the whole content folder off the main thread. */
    public void validateAll(Player player, EditorMenu parent) {
        message(player, "&7Validating every content file...");
        PluginConfig config = plugin.getConfigValues();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<EditorIssue> issues = collectAllIssues(config);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                open(player, ValidationMenu.of(this, openSession(player), parent,
                        "&bWhole content set", issues, null));
            });
        });
    }

    private List<EditorIssue> collectAllIssues(PluginConfig config) {
        List<EditorIssue> issues = new ArrayList<>();
        for (EditorEntry entry : repository.entries()) {
            try {
                EditorDocument document = EditorDocument.load(entry.file());
                EditorValidator.Context context = EditorValidator.context(plugin, repository, document);
                for (EditorIssue issue : EditorValidator.validate(document, entry.type(), context)) {
                    issues.add(new EditorIssue(issue.severity(),
                            entry.id() + "." + issue.field(), issue.message()));
                }
                for (String error : EditorValidator.authoritative(plugin, repository, config, document, entry.type())) {
                    issues.add(EditorIssue.error(entry.id(), error));
                }
            } catch (RuntimeException e) {
                issues.add(EditorIssue.error(entry.id(), "could not read the file: " + e.getMessage()));
            }
        }
        return issues;
    }

    public void validateAndShow(Player player, EditorSession session, EditorMenu parent) {
        if (session.document() == null) {
            error(player, "there is nothing to validate");
            return;
        }
        List<EditorIssue> issues = validate(session);
        open(player, ValidationMenu.of(this, session, parent, "&b" + session.document().id(), issues, null));
    }

    /**
     * Throw away every unsaved change by reloading the entry from disk. The file itself was never
     * touched, so this is always safe; a brand-new entry has nothing to reload and is discarded.
     */
    public void revert(Player player, EditorSession session, EditorMenu returnTo) {
        EditorDocument document = session.document();
        if (document == null) {
            return;
        }
        if (!document.isDirty()) {
            message(player, "&7There are no unsaved changes.");
            return;
        }
        if (document.isNew()) {
            session.setDocument(null, session.type());
            message(player, "&eDiscarded the new entry (nothing had been written).");
            openMain(player);
            return;
        }
        // Reloaded in place (not by replacing the document) so the undo history survives and the
        // revert itself can be undone.
        if (!document.reloadFromDisk()) {
            error(player, "the file could not be read from disk");
            return;
        }
        message(player, "&aReverted to the file on disk &7(you can undo this).");
        if (returnTo != null) {
            returnTo.refresh();
        } else {
            open(player, new ContentEditorMenu(this, session, null, 0));
        }
    }

    public List<EditorIssue> validate(EditorSession session) {
        if (session.document() == null) {
            return List.of();
        }
        EditorValidator.Context context = EditorValidator.context(plugin, repository, session.document());
        return EditorValidator.validate(session.document(), session.type(), context);
    }

    /** Build the item from the working copy and hand it to the player. */
    public void givePreview(Player player, EditorSession session) {
        EditorDocument document = session.document();
        if (document == null) {
            return;
        }
        File scratch = repository.newScratchFile(document.id());
        if (scratch == null) {
            error(player, "could not create the preview workspace");
            return;
        }
        try {
            Files.writeString(scratch.toPath(), document.toYaml(), StandardCharsets.UTF_8);
            ContentLoader.LoadResult result = new ContentLoader(plugin, plugin.getConfigValues(), true)
                    .loadFile(scratch, session.type());
            if (result.items.isEmpty()) {
                for (String error : result.errors) {
                    message(player, "&c" + error);
                }
                error(player, "the loader rejects the current values, so there is nothing to preview");
                return;
            }
            CustomItem item = result.items.get(0);
            player.getInventory().addItem(plugin.getItemFactory().build(item, 1));
            message(player, "&aPreview of &f" + item.getId() + "&a added to your inventory.");
            if (!result.errors.isEmpty()) {
                message(player, "&eNote: the loader reported " + result.errors.size()
                        + " problem(s) with these values.");
            }
        } catch (IOException e) {
            error(player, "could not write the preview file: " + e.getMessage());
        } finally {
            if (!scratch.delete()) {
                scratch.deleteOnExit();
            }
        }
    }

    // ---- save ----

    /**
     * Validate and write the working copy.
     *
     * <p>Nothing is written unless the entry passes the editor's field checks, and (unless forced)
     * unless the real {@link ContentLoader} accepts it too. A previous version of the file is kept
     * in {@code backups/editor}.
     */
    public void save(Player player, EditorSession session, boolean force, EditorMenu returnTo) {
        EditorDocument document = session.document();
        if (document == null) {
            error(player, "nothing to save");
            return;
        }
        if (!document.isDirty() && !force) {
            message(player, "&7No changes to save.");
            player.closeInventory();
            return;
        }
        List<EditorIssue> issues = validate(session);
        if (EditorValidator.hasErrors(issues) && !force) {
            error(player, "this entry has errors; fix them before saving");
            open(player, ValidationMenu.of(this, session, returnTo,
                    "&cCannot save yet", issues, () -> save(player, session, true, returnTo)));
            return;
        }
        List<String> loaderErrors = EditorValidator.authoritative(plugin, repository,
                plugin.getConfigValues(), document, session.type());
        if (!loaderErrors.isEmpty() && !force) {
            error(player, "the content loader rejects this file; nothing was written");
            List<EditorIssue> converted = new ArrayList<>();
            for (String problem : loaderErrors) {
                converted.add(EditorIssue.error("", problem));
            }
            open(player, ValidationMenu.of(this, session, returnTo,
                    "&cLoader rejects the file", converted, () -> save(player, session, true, returnTo)));
            return;
        }

        String id = document.id();
        File target = repository.fileFor(session.type(), id);
        File previous = document.file();
        document.retarget(target);
        if (!repository.write(document, session.type())) {
            error(player, "could not write " + target.getName() + ", see the console");
            return;
        }
        document.markSaved();

        // Renaming an entry must not leave the old file behind: it would keep serving the old id.
        if (session.original() != null
                && !previous.getAbsolutePath().equals(target.getAbsolutePath())) {
            repository.delete(session.original());
            message(player, "&7The previous file &f" + previous.getName()
                    + "&7 was removed (backup kept in backups/editor/deleted).");
        }
        session.setOriginal(repository.find(id));
        repository.invalidate();
        message(player, "&aSaved &f" + target.getName()
                + "&a. Reloading content and the pack in the background.");
        if (!loaderErrors.isEmpty()) {
            message(player, "&eForced save: the loader still reports " + loaderErrors.size()
                    + " problem(s) — check the console.");
        }
        plugin.reloadAll();
        session.setCurrentMenu(null);
        player.closeInventory();
    }

    // ---- helpers ----

    /** The owner of a base block, ignoring the entry being edited, or null when it is free. */
    public String baseBlockOwner(Material material, EditorSession session) {
        String ownPath = session.document() == null ? null : session.document().file().getAbsolutePath();
        for (EditorEntry entry : repository.entries()) {
            if (entry.baseBlock() == material
                    && (ownPath == null || !entry.file().getAbsolutePath().equals(ownPath))) {
                return entry.id();
            }
        }
        return null;
    }

    public List<String> knownAttributes() {
        return ItemFactory.knownAttributeNames();
    }

    public List<String> knownEnchantments() {
        Set<String> keys = new TreeSet<>();
        for (Enchantment enchantment : Enchantment.values()) {
            NamespacedKey key = enchantment.getKey();
            if (key != null) {
                keys.add(key.getKey());
            }
        }
        return List.copyOf(keys);
    }

    private String idProblem(String id, String ignoreId) {
        if (!ContentLoader.isValidId(id)) {
            return "invalid id (use lowercase letters, numbers, '_', '-' or '.')";
        }
        if (repository.idTaken(id, ignoreId)) {
            return "the id '" + id + "' is already used by another file";
        }
        return null;
    }

    private static String numberProblem(String value, boolean integer, Object current) {
        if (value.isEmpty() || value.equalsIgnoreCase("clear")) {
            return null;
        }
        try {
            double number = Double.parseDouble(value);
            if (integer && number != Math.floor(number)) {
                return "must be a whole number";
            }
            return null;
        } catch (NumberFormatException e) {
            return "must be a number";
        }
    }

    private static Object parseNumber(String value, boolean integer) {
        double number = Double.parseDouble(value);
        return integer ? (Object) (int) number : (Object) number;
    }

    private static String nodeProblem(String value, Object current) {
        if (value.isEmpty() || value.equalsIgnoreCase("clear")) {
            return null;
        }
        if (current instanceof Boolean && !value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            return "expected true or false";
        }
        if (current instanceof Number) {
            try {
                Double.parseDouble(value);
                return null;
            } catch (NumberFormatException e) {
                return "expected a number";
            }
        }
        return null;
    }

    /** Value inference for a free-form input, keeping the type of the value it replaces. */
    private static Object parseLike(String value, Object current) {
        if (current instanceof Boolean) {
            return Boolean.parseBoolean(value);
        }
        if (current instanceof Integer) {
            try {
                return (int) Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return value;
            }
        }
        if (current instanceof Number) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return value;
            }
        }
        return value;
    }

    /** Type inference for a brand-new parameter (key=value style). */
    private static Object parseFree(String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(value);
        }
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private void error(Player player, String message) {
        message(player, "&c" + message);
    }
}
