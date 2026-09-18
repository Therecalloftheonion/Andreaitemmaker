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
import java.util.List;
import java.util.Map;

/**
 * Edits any nested map of the document — mechanic parameters, their nested sections, or a map
 * inside a list — through {@link EditorNode}, so the same menu handles every depth.
 */
public final class MapEditorMenu extends EditorMenu {

    private final EditorNode node;
    private final String heading;
    private final int page;

    public MapEditorMenu(EditorManager manager, EditorSession session, EditorMenu parent,
                         EditorNode node, String heading, int page) {
        super(manager, session, parent);
        this.node = node;
        this.heading = heading;
        this.page = Math.max(0, page);
    }

    private List<String> keys() {
        List<String> keys = new ArrayList<>(node.entries().keySet());
        keys.sort(String::compareTo);
        return keys;
    }

    @Override
    protected String title() {
        return "&8| &b" + heading;
    }

    @Override
    protected void render(Inventory inventory) {
        background(inventory);
        Map<String, Object> entries = node.entries();
        List<String> keys = keys();
        List<String> shown = pageOf(keys, page);
        int pages = EditorGui.pageCount(keys.size(), EditorGui.CONTENT_SIZE);

        set(inventory, 4, EditorGui.icon(Material.COMMAND_BLOCK, "&b" + heading,
                List.of("&7Parameters: &f" + keys.size(),
                        "&7Page &f" + (page + 1) + "&7/&f" + pages,
                        "",
                        "&7Left click  &8- &7edit the value",
                        "&7Right click &8- &7remove the parameter",
                        "",
                        "&7Everything is written as normal YAML",
                        "&7and validated before saving.")));

        if (keys.isEmpty()) {
            set(inventory, 22, EditorGui.icon(Material.LIGHT_GRAY_DYE, "&7No parameters",
                    List.of("&7This mechanic runs with its defaults.",
                            "&7Use &fAdd parameter&7 to set one.")));
        }
        for (int i = 0; i < shown.size(); i++) {
            String key = shown.get(i);
            set(inventory, EditorGui.CONTENT_FIRST + i, iconFor(key, entries.get(key)));
        }

        set(inventory, EditorGui.SLOT_BACK, EditorGui.icon(Material.ARROW, "&eBack",
                "&7Return to the previous level."));
        set(inventory, EditorGui.SLOT_SEARCH, EditorGui.icon(Material.ANVIL, "&eAdd parameter",
                List.of("&7Type &fkey=value&7 in chat",
                        "&7e.g. &fpower=1.5 &7or &fparticles=false")));
        set(inventory, EditorGui.SLOT_CLOSE, EditorGui.icon(Material.BARRIER, "&cClose",
                "&7Close the editor."));
        pageButtons(inventory, page, pages);
    }

    private org.bukkit.inventory.ItemStack iconFor(String key, Object value) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Value: " + EditorGui.display(value));
        if (value instanceof Boolean) {
            lore.add("");
            lore.add("&eClick to toggle");
        } else if (value instanceof Map<?, ?>) {
            lore.add("");
            lore.add("&eClick to edit the nested section");
        } else if (value instanceof List<?> list) {
            lore.add("&7Entries: &f" + list.size());
            lore.add("");
            lore.add("&eClick to edit the list");
        } else {
            lore.add("");
            lore.add("&eClick to change (type it in chat)");
        }
        lore.add("&eRight click &7to remove");
        Material icon = value instanceof Boolean ? Material.LIME_DYE
                : value instanceof Map<?, ?> ? Material.CHEST
                : value instanceof List<?> ? Material.BOOK
                : value instanceof Number ? Material.REPEATER
                : Material.NAME_TAG;
        return EditorGui.icon(icon, "&b" + key, lore);
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
        if (slot == EditorGui.SLOT_SEARCH) {
            manager.promptNodeEntry(player, this, node, heading);
            return;
        }
        if (slot == EditorGui.SLOT_PAGE_PREV) {
            new MapEditorMenu(manager, session, parent, node, heading, page - 1).open(player);
            return;
        }
        if (slot == EditorGui.SLOT_PAGE_NEXT) {
            new MapEditorMenu(manager, session, parent, node, heading, page + 1).open(player);
            return;
        }
        if (!inContent(slot)) {
            return;
        }
        List<String> shown = pageOf(keys(), page);
        int index = contentIndex(slot);
        if (index >= shown.size()) {
            return;
        }
        String key = shown.get(index);
        Object value = node.entries().get(key);

        if (click == ClickType.RIGHT) {
            node.remove(key);
            manager.message(player, "&eRemoved &f" + key + "&e from &f" + heading + "&e.");
            refresh();
            return;
        }
        if (value instanceof Boolean bool) {
            node.put(key, !bool);
            refresh();
            return;
        }
        if (value instanceof Map<?, ?>) {
            new MapEditorMenu(manager, session, this, node.child(key), key, 0).open(player);
            return;
        }
        if (value instanceof List<?>) {
            new ListEditorMenu(manager, session, this, node, key, key, 0).open(player);
            return;
        }
        manager.promptNodeValue(player, this, node, key, value);
    }
}
