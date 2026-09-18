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
 * Shows the keys of the file that the editor does not manage. They are never modified or removed
 * — this page exists so the admin can confirm that their custom fields are noticed and preserved.
 */
public final class UnknownFieldsMenu extends EditorMenu {

    public UnknownFieldsMenu(EditorManager manager, EditorSession session, EditorMenu parent) {
        super(manager, session, parent);
    }

    @Override
    protected String title() {
        return "&8| &bPreserved fields";
    }

    @Override
    protected void render(Inventory inventory) {
        background(inventory);
        List<String> unknown = new ArrayList<>(session.document().unknownTopLevelKeys(session.type()));
        set(inventory, 4, EditorGui.icon(Material.CHEST, "&bPreserved fields",
                List.of("&7Keys this editor does not manage: &f" + unknown.size(),
                        "",
                        "&7They are kept exactly as they are when",
                        "&7the file is saved — the editor never",
                        "&7rewrites a file from a fixed schema.",
                        "",
                        "&7Nested values inside a managed section",
                        "&7are preserved in the same way.")));

        for (int i = 0; i < unknown.size() && i < EditorGui.CONTENT_SIZE; i++) {
            String key = unknown.get(i);
            Object value = session.document().get(key);
            List<String> lore = new ArrayList<>();
            lore.add("&7Value: " + EditorGui.display(value));
            lore.add("");
            lore.add("&7Kept as-is on save.");
            set(inventory, EditorGui.CONTENT_FIRST + i, EditorGui.icon(Material.PAPER, "&b" + key, lore));
        }
        if (unknown.isEmpty()) {
            set(inventory, 22, EditorGui.icon(Material.LIME_DYE, "&aNo extra keys",
                    "&7This file only uses managed fields."));
        }

        navBar(inventory);
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        if (slot == EditorGui.SLOT_BACK) {
            back(player);
        } else if (slot == EditorGui.SLOT_CLOSE) {
            player.closeInventory();
        }
    }
}
