package com.andreaitemmaker.editor.menu;

import com.andreaitemmaker.api.ItemMechanic;
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

/**
 * Lists every registered mechanic (built-in and from other plugins) and turns them on or off for
 * this entry. Clicking an enabled mechanic opens its parameters in the generic map editor, so a
 * mechanic added by another plugin is fully editable without any editor change.
 */
public final class MechanicsMenu extends EditorMenu {

    private final int page;

    public MechanicsMenu(EditorManager manager, EditorSession session, EditorMenu parent, int page) {
        super(manager, session, parent);
        this.page = Math.max(0, page);
    }

    private List<ItemMechanic> mechanics() {
        List<ItemMechanic> out = new ArrayList<>(manager.plugin().getMechanicRegistry().getAll());
        out.sort((a, b) -> a.getId().compareTo(b.getId()));
        return out;
    }

    private boolean enabled(String id) {
        return session.document().contains("mechanics." + id);
    }

    private int parameterCount(String id) {
        List<String> keys = session.document().keys("mechanics." + id);
        return keys.size();
    }

    @Override
    protected String title() {
        return "&8| &bMechanics";
    }

    @Override
    protected void render(Inventory inventory) {
        background(inventory);
        List<ItemMechanic> all = mechanics();
        List<ItemMechanic> shown = pageOf(all, page);
        int pages = EditorGui.pageCount(all.size(), EditorGui.CONTENT_SIZE);
        int active = 0;
        for (ItemMechanic mechanic : all) {
            if (enabled(mechanic.getId())) {
                active++;
            }
        }

        set(inventory, 4, EditorGui.icon(Material.COMMAND_BLOCK, "&bMechanics",
                List.of("&7Active on this entry: &f" + active + "&7/&f" + all.size(),
                        "&7Page &f" + (page + 1) + "&7/&f" + pages,
                        "",
                        "&7Left click  &8- &7enable / disable",
                        "&7Right click &8- &7edit the parameters",
                        "",
                        "&7Parameters are normal YAML and are",
                        "&7validated against the loader before saving.")));

        for (int i = 0; i < shown.size(); i++) {
            ItemMechanic mechanic = shown.get(i);
            String id = mechanic.getId();
            boolean on = enabled(id);
            List<String> lore = new ArrayList<>();
            lore.add("&7Mechanic id: &f" + id);
            lore.add("&7Status: " + (on ? "&aenabled" : "&cdisabled"));
            if (on) {
                lore.add("&7Parameters set: &f" + parameterCount(id));
            }
            lore.add("");
            lore.add("&eLeft click &7to " + (on ? "disable" : "enable"));
            if (on) {
                lore.add("&eRight click &7to edit parameters");
            }
            set(inventory, EditorGui.CONTENT_FIRST + i, EditorGui.icon(
                    on ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                    (on ? "&a" : "&7") + id, lore));
        }

        set(inventory, EditorGui.SLOT_BACK, EditorGui.icon(Material.ARROW, "&eBack",
                "&7Return to the fields."));
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
        if (slot == EditorGui.SLOT_PAGE_PREV) {
            new MechanicsMenu(manager, session, parent, page - 1).open(player);
            return;
        }
        if (slot == EditorGui.SLOT_PAGE_NEXT) {
            new MechanicsMenu(manager, session, parent, page + 1).open(player);
            return;
        }
        if (!inContent(slot)) {
            return;
        }
        List<ItemMechanic> shown = pageOf(mechanics(), page);
        int index = contentIndex(slot);
        if (index >= shown.size()) {
            return;
        }
        String id = shown.get(index).getId();
        if (click == ClickType.RIGHT) {
            if (!enabled(id)) {
                error(player, "enable " + id + " first");
                return;
            }
            EditorNode node = new EditorNode.SectionNode(session.document(), "mechanics." + id);
            new MapEditorMenu(manager, session, this, node, id + " parameters", 0).open(player);
            return;
        }
        if (enabled(id)) {
            session.document().remove("mechanics." + id);
            manager.message(player, "&eDisabled &f" + id + "&e.");
        } else {
            // An explicit empty section (not a bare map value) so parameters can be added later.
            session.document().yaml().createSection("mechanics." + id);
            manager.message(player, "&aEnabled &f" + id + "&a (uses its defaults until you set parameters).");
        }
        refresh();
    }
}
