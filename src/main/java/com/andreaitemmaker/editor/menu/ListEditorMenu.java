package com.andreaitemmaker.editor.menu;

import com.andreaitemmaker.editor.EditorGui;
import com.andreaitemmaker.editor.EditorManager;
import com.andreaitemmaker.editor.EditorNode;
import com.andreaitemmaker.editor.EditorSession;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Edits a YAML list owned by an {@link EditorNode} — both the scalar lists ({@code lore}) and the
 * lists of maps the mechanics use ({@code effects}).
 *
 * <p>The list is always re-read from the node, so an entry added or removed is immediately
 * visible and there is never a stale snapshot to write back.
 */
public final class ListEditorMenu extends EditorMenu {

    private final EditorNode owner;
    private final String key;
    private final String heading;
    private final int page;

    public ListEditorMenu(EditorManager manager, EditorSession session, EditorMenu parent,
                          EditorNode owner, String key, String heading, int page) {
        super(manager, session, parent);
        this.owner = owner;
        this.key = key;
        this.heading = heading;
        this.page = Math.max(0, page);
    }

    private List<Object> values() {
        Object value = owner.entries().get(key);
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    private boolean listOfMaps() {
        for (Object value : values()) {
            if (!(value instanceof Map<?, ?>)) {
                return false;
            }
        }
        return true;
    }

    private void persist(List<Object> list) {
        owner.put(key, list);
    }

    @Override
    protected String title() {
        return "&8| &b" + heading;
    }

    @Override
    protected void render(Inventory inventory) {
        background(inventory);
        List<Object> all = values();
        List<Object> shown = pageOf(all, page);
        int pages = EditorGui.pageCount(all.size(), EditorGui.CONTENT_SIZE);

        set(inventory, 4, EditorGui.icon(Material.BOOK, "&b" + heading,
                List.of("&7Entries: &f" + all.size(),
                        "&7Page &f" + (page + 1) + "&7/&f" + pages,
                        "",
                        listOfMaps() ? "&7Each entry is a map of options."
                                : "&7Each entry is a single value.",
                        "",
                        "&7Left click  &8- &7edit the entry",
                        "&7Right click &8- &7remove the entry")));

        if (all.isEmpty()) {
            set(inventory, 22, EditorGui.icon(Material.BARRIER, "&7The list is empty",
                    "&7Use &fAdd entry&7 to create one."));
        }
        for (int i = 0; i < shown.size(); i++) {
            set(inventory, EditorGui.CONTENT_FIRST + i, iconFor(i, shown.get(i)));
        }

        set(inventory, EditorGui.SLOT_BACK, EditorGui.icon(Material.ARROW, "&eBack",
                "&7Return to the previous menu."));
        set(inventory, EditorGui.SLOT_SEARCH, EditorGui.icon(Material.EMERALD, "&aAdd entry",
                listOfMaps() ? "&7Adds an empty option block."
                        : "&7Type the new value in chat."));
        set(inventory, 47, EditorGui.icon(Material.BUCKET, "&eClear the list",
                "&7Removes every entry."));
        set(inventory, EditorGui.SLOT_CLOSE, EditorGui.icon(Material.BARRIER, "&cClose",
                "&7Close the editor."));
        pageButtons(inventory, page, pages);
    }

    private org.bukkit.inventory.ItemStack iconFor(int indexInPage, Object value) {
        if (value instanceof Map<?, ?> map) {
            List<String> lore = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                lore.add("&7" + entry.getKey() + ": " + EditorGui.display(entry.getValue()));
            }
            if (lore.isEmpty()) {
                lore.add("&8(no options set)");
            }
            lore.add("");
            lore.add("&eLeft click &7to edit the options");
            lore.add("&eRight click &7to remove this entry");
            return EditorGui.icon(Material.CHEST, "&bEntry #" + (page * EditorGui.CONTENT_SIZE + indexInPage + 1),
                    lore);
        }
        return EditorGui.icon(Material.NAME_TAG,
                "&bEntry #" + (page * EditorGui.CONTENT_SIZE + indexInPage + 1),
                List.of("&7Value: " + EditorGui.display(value),
                        "",
                        "&eLeft click &7to change it",
                        "&eRight click &7to remove it"));
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        if (slot == EditorGui.SLOT_BACK || slot == EditorGui.SLOT_CLOSE) {
            if (slot == EditorGui.SLOT_BACK) {
                back(player);
            } else {
                player.closeInventory();
            }
            return;
        }
        if (slot == 47) {
            persist(new ArrayList<>());
            manager.message(player, "&eCleared &f" + heading + "&e.");
            refresh();
            return;
        }
        if (slot == EditorGui.SLOT_SEARCH) {
            List<Object> list = values();
            if (listOfMaps() || list.isEmpty()) {
                list.add(new LinkedHashMap<String, Object>());
                persist(list);
                refresh();
            } else {
                manager.promptListEntry(player, this, owner, key, -1, null);
            }
            return;
        }
        if (slot == EditorGui.SLOT_PAGE_PREV) {
            new ListEditorMenu(manager, session, parent, owner, key, heading, page - 1).open(player);
            return;
        }
        if (slot == EditorGui.SLOT_PAGE_NEXT) {
            new ListEditorMenu(manager, session, parent, owner, key, heading, page + 1).open(player);
            return;
        }
        if (!inContent(slot)) {
            return;
        }
        int index = page * EditorGui.CONTENT_SIZE + contentIndex(slot);
        List<Object> list = values();
        if (index >= list.size()) {
            return;
        }
        Object value = list.get(index);

        if (click == ClickType.RIGHT) {
            list.remove(index);
            persist(list);
            manager.message(player, "&eRemoved entry #" + (index + 1) + "&e.");
            refresh();
            return;
        }
        if (value instanceof Map<?, ?> rawMap) {
            // Edit a snapshot map and write it back into the list, so the element identity used by
            // the nested menu is unambiguous.
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                map.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            list.set(index, map);
            persist(list);
            EditorNode node = new EditorNode.MapNode(map, m -> persist(values()));
            new MapEditorMenu(manager, session, this, node,
                    heading + " #" + (index + 1), 0).open(player);
            return;
        }
        manager.promptListEntry(player, this, owner, key, index, value);
    }
}
