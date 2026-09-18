package com.andreaitemmaker.editor.menu;

import com.andreaitemmaker.editor.EditorGui;
import com.andreaitemmaker.editor.EditorManager;
import com.andreaitemmaker.editor.EditorSession;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Edits a map of known keys — {@code attributes} (vanilla attribute names) and
 * {@code enchantments} (vanilla enchantment names). Only valid keys are offered, so a typo cannot
 * be introduced from the GUI; the value is a number taken from chat.
 */
public final class KnownKeyMapMenu extends EditorMenu {

    private final String path;
    private final boolean enchantments;
    private final int page;

    public KnownKeyMapMenu(EditorManager manager, EditorSession session, EditorMenu parent,
                           String path, boolean enchantments, int page) {
        super(manager, session, parent);
        this.path = path;
        this.enchantments = enchantments;
        this.page = Math.max(0, page);
    }

    private List<String> keys() {
        return enchantments ? manager.knownEnchantments() : manager.knownAttributes();
    }

    private Object valueOf(String key) {
        return session.document().get(path + "." + key);
    }

    @Override
    protected String title() {
        return "&8| &b" + (enchantments ? "Enchantments" : "Attributes");
    }

    @Override
    protected void render(Inventory inventory) {
        background(inventory);
        List<String> all = keys();
        List<String> shown = pageOf(all, page);
        int pages = EditorGui.pageCount(all.size(), EditorGui.CONTENT_SIZE);
        List<String> set = new ArrayList<>();
        for (String key : all) {
            if (valueOf(key) != null) {
                set.add(key);
            }
        }

        set(inventory, 4, EditorGui.icon(enchantments ? Material.ENCHANTED_BOOK : Material.IRON_CHESTPLATE,
                "&b" + (enchantments ? "Enchantments" : "Attributes"),
                List.of("&7Field: &f" + path,
                        "&7Applied: &f" + set.size() + "&7/&f" + all.size(),
                        "&7Page &f" + (page + 1) + "&7/&f" + pages,
                        "",
                        "&7Left click  &8- &7set / change the value",
                        "&7Right click &8- &7remove the entry",
                        "",
                        enchantments ? "&7Values are enchantment levels."
                                : "&7Values are numbers (attack_damage: 9.0).")));

        for (int i = 0; i < shown.size(); i++) {
            String key = shown.get(i);
            Object value = valueOf(key);
            set(inventory, EditorGui.CONTENT_FIRST + i, EditorGui.icon(
                    value == null ? Material.LIGHT_GRAY_DYE : Material.LIME_DYE,
                    (value == null ? "&7" : "&a") + key,
                    List.of("&7Value: " + EditorGui.display(value),
                            "",
                            value == null ? "&eClick to set a value" : "&eClick to change it",
                            "&eRight click &7to remove")));
        }

        set(inventory, EditorGui.SLOT_BACK, EditorGui.icon(Material.ARROW, "&eBack",
                "&7Return to the fields."));
        set(inventory, 47, EditorGui.icon(Material.BUCKET, "&eRemove everything",
                "&7Clears this whole section."));
        set(inventory, EditorGui.SLOT_CLOSE, EditorGui.icon(Material.BARRIER, "&cClose",
                "&7Close the editor."));
        pageButtons(inventory, page, pages);
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
            session.document().remove(path);
            manager.message(player, "&eCleared &f" + path + "&e.");
            refresh();
            return;
        }
        if (slot == EditorGui.SLOT_PAGE_PREV) {
            new KnownKeyMapMenu(manager, session, parent, path, enchantments, page - 1).open(player);
            return;
        }
        if (slot == EditorGui.SLOT_PAGE_NEXT) {
            new KnownKeyMapMenu(manager, session, parent, path, enchantments, page + 1).open(player);
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
        if (click == ClickType.RIGHT) {
            session.document().remove(path + "." + key);
            manager.message(player, "&eRemoved &f" + key + "&e.");
            refresh();
            return;
        }
        manager.promptKeyedNumber(player, this, path, key, enchantments);
    }
}
