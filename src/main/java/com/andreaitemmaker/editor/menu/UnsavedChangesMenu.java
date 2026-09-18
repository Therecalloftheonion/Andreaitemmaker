package com.andreaitemmaker.editor.menu;

import com.andreaitemmaker.editor.EditorGui;
import com.andreaitemmaker.editor.EditorManager;
import com.andreaitemmaker.editor.EditorSession;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Shown when a player closes the editor while the working copy has unsaved changes. The changes
 * are never silently dropped: the player chooses to save, to discard, or to keep editing.
 */
public final class UnsavedChangesMenu extends EditorMenu {

    private final EditorMenu returnTo;

    public UnsavedChangesMenu(EditorManager manager, EditorSession session, EditorMenu returnTo) {
        super(manager, session, returnTo == null ? null : returnTo.parent());
        this.returnTo = returnTo;
    }

    @Override
    protected String title() {
        return "&8| Unsaved changes";
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void render(Inventory inventory) {
        background(inventory);
        String id = session.document() == null ? "this entry" : session.document().id();
        set(inventory, 4, EditorGui.icon(Material.PAPER, "&eYou have unsaved changes",
                List.of("&7Entry: &f" + id,
                        "",
                        "&7Nothing has been written to disk yet.")));
        set(inventory, 11, EditorGui.icon(Material.EMERALD_BLOCK, "&aSave",
                "&7Validate, write the file and reload."));
        set(inventory, 13, EditorGui.icon(Material.RED_CONCRETE, "&cDiscard",
                "&7Throw away the changes (the file",
                        "&7on disk is left untouched)."));
        set(inventory, 15, EditorGui.icon(Material.BOOK, "&eContinue editing",
                "&7Go back to the editor."));
    }

    /** Closing this dialog keeps you editing rather than trapping you in a prompt. */
    @Override
    public void onClose(Player player) {
        if (returnTo != null && session.document() != null) {
            manager.plugin().getServer().getScheduler().runTask(manager.plugin(),
                    () -> manager.open(player, returnTo));
        }
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        switch (slot) {
            case 11 -> manager.save(player, session, false, returnTo);
            case 13 -> {
                session.setDocument(null, session.type());
                session.clearPendingInput();
                player.closeInventory();
                manager.message(player, "&eChanges discarded; the file on disk is unchanged.");
            }
            case 15 -> {
                if (returnTo != null) {
                    manager.open(player, returnTo);
                } else {
                    manager.openMain(player);
                }
            }
            default -> {
                // ignore
            }
        }
    }
}
