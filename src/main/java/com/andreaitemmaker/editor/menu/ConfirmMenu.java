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

/** Yes/no confirmation for destructive actions. */
public final class ConfirmMenu extends EditorMenu {

    private final String heading;
    private final List<String> description;
    private final Runnable onConfirm;

    public ConfirmMenu(EditorManager manager, EditorSession session, EditorMenu parent,
                       String heading, List<String> description, Runnable onConfirm) {
        super(manager, session, parent);
        this.heading = heading;
        this.description = description;
        this.onConfirm = onConfirm;
    }

    @Override
    protected String title() {
        return "&8| Confirm";
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void render(Inventory inventory) {
        background(inventory);
        List<String> lore = new ArrayList<>(description);
        set(inventory, 4, EditorGui.icon(Material.PAPER, heading, lore));
        set(inventory, 11, EditorGui.icon(Material.LIME_CONCRETE, "&aConfirm",
                "&7Yes, do it."));
        set(inventory, 15, EditorGui.icon(Material.RED_CONCRETE, "&cCancel",
                "&7Go back without changing anything."));
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        if (slot == 11) {
            onConfirm.run();
        } else {
            back(player);
        }
    }
}
