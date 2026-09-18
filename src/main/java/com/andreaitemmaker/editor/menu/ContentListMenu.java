package com.andreaitemmaker.editor.menu;

import com.andreaitemmaker.api.CustomItemType;
import com.andreaitemmaker.editor.EditorEntry;
import com.andreaitemmaker.editor.EditorGui;
import com.andreaitemmaker.editor.EditorManager;
import com.andreaitemmaker.editor.EditorSchema;
import com.andreaitemmaker.editor.EditorSession;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists content entries for one category (or the search results across every category) with
 * pagination and per-entry actions: edit, inspect, duplicate, delete.
 */
public final class ContentListMenu extends EditorMenu {

    private final CustomItemType type;
    private final int page;
    private final String query;

    public ContentListMenu(EditorManager manager, EditorSession session, EditorMenu parent,
                           CustomItemType type, int page, String query) {
        super(manager, session, parent);
        this.type = type;
        this.page = Math.max(0, page);
        this.query = query == null ? "" : query;
    }

    private List<EditorEntry> entries() {
        List<EditorEntry> all = new ArrayList<>();
        for (EditorEntry entry : manager.repository().entries()) {
            if (type != null && entry.type() != type) {
                continue;
            }
            if (entry.matches(query)) {
                all.add(entry);
            }
        }
        return all;
    }

    @Override
    protected String title() {
        String name = type == null ? "Search results" : EditorSchema.label(type);
        String more = query.isEmpty() ? "" : " '&f" + query + "&8'";
        return "&8| &b" + name + more + " &8(" + page + ")";
    }

    @Override
    protected void render(Inventory inventory) {
        background(inventory);
        List<EditorEntry> all = entries();
        List<EditorEntry> shown = pageOf(all, page);
        int pages = EditorGui.pageCount(all.size(), EditorGui.CONTENT_SIZE);

        set(inventory, 4, EditorGui.icon(Material.CHEST, "&b"
                        + (type == null ? "All content" : EditorSchema.label(type)),
                List.of("&7" + all.size() + " entr" + (all.size() == 1 ? "y" : "ies")
                                + " shown" + (query.isEmpty() ? "" : " for '&f" + query + "&7'"),
                        "&7Page &f" + (page + 1) + "&7/&f" + pages,
                        "",
                        "&7Left click  &8- &7edit",
                        "&7Right click &8- &7inspect",
                        "&7Shift+left  &8- &7duplicate",
                        "&7Shift+right &8- &7delete")));

        if (shown.isEmpty()) {
            set(inventory, 22, EditorGui.icon(Material.BARRIER, "&cNothing here",
                    List.of("&7No content matches.",
                            "&7Use &fCreate new&7 in the main menu,",
                            "&7or clear the search.")));
        }
        for (int i = 0; i < shown.size(); i++) {
            set(inventory, EditorGui.CONTENT_FIRST + i, entryIcon(shown.get(i)));
        }

        set(inventory, EditorGui.SLOT_BACK, EditorGui.icon(Material.ARROW, "&eBack",
                "&7Return to the main menu."));
        set(inventory, EditorGui.SLOT_CLOSE, EditorGui.icon(Material.BARRIER, "&cClose",
                "&7Close the editor."));
        set(inventory, EditorGui.SLOT_SEARCH, EditorGui.icon(Material.COMPASS, "&eSearch",
                List.of("&7Filter by id or display name.",
                        "&7Current query: " + (query.isEmpty() ? "&8(none)" : "&f" + query),
                        "",
                        "&eClick to type a new query")));
        set(inventory, EditorGui.SLOT_CONFIRM, EditorGui.icon(Material.EMERALD,
                "&aCreate new " + EditorSchema.singular(type == null ? CustomItemType.ITEM : type),
                "&7Create a new entry of this type."));
        if (!query.isEmpty()) {
            set(inventory, 47, EditorGui.icon(Material.BUCKET, "&eClear search",
                    "&7Show everything again."));
        }
        pageButtons(inventory, page, pages);
    }

    private org.bukkit.inventory.ItemStack entryIcon(EditorEntry entry) {
        Material icon = entry.material() != null && entry.material().isItem()
                ? entry.material() : Material.PAPER;
        List<String> lore = new ArrayList<>();
        lore.add("&7File: &f" + entry.file().getName());
        lore.add("&7Type: &f" + entry.type().name());
        lore.add("&7Material: &f" + (entry.material() == null ? "missing" : entry.material().name().toLowerCase()));
        if (entry.baseBlock() != null) {
            lore.add("&7Base block: &f" + entry.baseBlock().name().toLowerCase());
        }
        if (entry.displayName() != null) {
            lore.add("&7Display name: &r" + entry.displayName());
        }
        lore.add("");
        lore.add("&eLeft click &7to edit");
        lore.add("&eRight click &7to inspect");
        lore.add("&eShift+left &7to duplicate");
        lore.add("&eShift+right &7to delete");
        return EditorGui.icon(icon, "&b" + entry.id(), lore);
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        if (slot == EditorGui.SLOT_SEARCH) {
            manager.promptSearch(player, this);
            return;
        }
        if (slot == 47 && !query.isEmpty()) {
            new ContentListMenu(manager, session, parent, type, 0, "").open(player);
            return;
        }
        if (slot == EditorGui.SLOT_CONFIRM) {
            manager.createNew(player, type == null ? CustomItemType.ITEM : type, this);
            return;
        }
        if (slot == EditorGui.SLOT_PAGE_PREV) {
            new ContentListMenu(manager, session, parent, type, page - 1, query).open(player);
            return;
        }
        if (slot == EditorGui.SLOT_PAGE_NEXT) {
            new ContentListMenu(manager, session, parent, type, page + 1, query).open(player);
            return;
        }
        if (slot == EditorGui.SLOT_BACK || slot == EditorGui.SLOT_CLOSE) {
            if (slot == EditorGui.SLOT_BACK) {
                back(player);
            } else {
                player.closeInventory();
            }
            return;
        }
        if (!inContent(slot)) {
            return;
        }
        List<EditorEntry> shown = pageOf(entries(), page);
        int index = contentIndex(slot);
        if (index >= shown.size()) {
            return;
        }
        EditorEntry entry = shown.get(index);
        if (click == ClickType.RIGHT) {
            manager.inspect(player, entry);
        } else if (click == ClickType.SHIFT_RIGHT) {
            new ConfirmMenu(manager, session, this, "&cDelete " + entry.id() + "?",
                    List.of("&7The file &f" + entry.file().getName() + "&7 is removed.",
                            "&7A copy is kept in &fbackups/editor/deleted&7."),
                    () -> manager.delete(player, entry, this)).open(player);
        } else if (click == ClickType.SHIFT_LEFT) {
            manager.duplicate(player, entry, this);
        } else {
            manager.edit(player, entry, this);
        }
    }
}
