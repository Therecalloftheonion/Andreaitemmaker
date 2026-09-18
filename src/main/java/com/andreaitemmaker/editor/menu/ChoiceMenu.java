package com.andreaitemmaker.editor.menu;

import com.andreaitemmaker.editor.EditorField;
import com.andreaitemmaker.editor.EditorGui;
import com.andreaitemmaker.editor.EditorManager;
import com.andreaitemmaker.editor.EditorSession;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Picks one of a field's allowed values.
 *
 * <p>This is a dialog (three rows), so its navigation buttons live in the third row rather than in
 * the bottom bar the six row menus use.
 */
public final class ChoiceMenu extends EditorMenu {

    private static final int BACK_SLOT = 22;
    private static final int CLOSE_SLOT = 26;

    private final EditorField field;

    public ChoiceMenu(EditorManager manager, EditorSession session, EditorMenu parent, EditorField field) {
        super(manager, session, parent);
        this.field = field;
    }

    @Override
    protected String title() {
        return "&8| &b" + field.label();
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void render(Inventory inventory) {
        background(inventory);
        String current = session.document() == null ? null : session.document().getString(field.path());
        set(inventory, 4, EditorGui.icon(Material.COMPARATOR, "&b" + field.label(),
                List.of("&7Current: " + EditorGui.display(current),
                        "",
                        "&7" + field.description())));

        List<String> values = field.values();
        for (int i = 0; i < values.size() && i < 9; i++) {
            String value = values.get(i);
            boolean selected = value.equalsIgnoreCase(current == null ? "" : current);
            set(inventory, 9 + i, EditorGui.icon(
                    selected ? Material.LIME_CONCRETE : Material.LIGHT_GRAY_CONCRETE,
                    (selected ? "&a" : "&b") + value,
                    List.of("&7Field: &f" + field.path(),
                            selected ? "&7This value is active" : "&eClick to use it")));
        }

        set(inventory, BACK_SLOT, EditorGui.icon(Material.ARROW, "&eBack",
                "&7Return without changing the value."));
        set(inventory, CLOSE_SLOT, EditorGui.icon(Material.BARRIER, "&cClose",
                "&7Close the editor."));
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        if (slot == BACK_SLOT) {
            back(player);
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        int index = slot - 9;
        if (index >= 0 && index < field.values().size()) {
            String value = field.values().get(index);
            manager.applyFieldValue(session, field, value);
            manager.message(player, "&a" + field.label() + " &7set to &f" + value);
            back(player);
        }
    }
}
