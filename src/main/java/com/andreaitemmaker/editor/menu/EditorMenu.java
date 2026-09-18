package com.andreaitemmaker.editor.menu;

import com.andreaitemmaker.editor.EditorGui;
import com.andreaitemmaker.editor.EditorManager;
import com.andreaitemmaker.editor.EditorSession;
import com.andreaitemmaker.util.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Base class for every editor GUI: a native Bukkit inventory that is also the click router.
 *
 * <p>Because the holder is the menu itself, a click can be routed to the exact menu that produced
 * the inventory — no per-player static maps, no guessing. Navigation always creates a new menu
 * instance, while {@link #refresh()} re-renders the current one in place (used after a value
 * changed), so no menu ever shows stale data.
 */
public abstract class EditorMenu implements InventoryHolder {

    protected final EditorManager manager;
    protected final EditorSession session;
    protected final EditorMenu parent;
    private Inventory inventory;

    protected EditorMenu(EditorManager manager, EditorSession session, EditorMenu parent) {
        this.manager = manager;
        this.session = session;
        this.parent = parent;
    }

    protected abstract String title();

    /** Inventory rows; every editor menu uses a fixed size so the inventory never has to resize. */
    protected int rows() {
        return 6;
    }

    protected abstract void render(Inventory inventory);

    public abstract void onClick(Player player, int slot, ClickType click);

    /** Called when the player closes this menu without navigating (see EditorManager#handleClose). */
    public void onClose(Player player) {
    }

    @Override
    public final Inventory getInventory() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, rows() * 9, Chat.color(title()));
        }
        return inventory;
    }

    /** Re-render in place (the player keeps the same window). */
    public final void refresh() {
        Inventory inv = getInventory();
        inv.clear();
        render(inv);
    }

    /** Open this menu for a player (replacing whatever is open). */
    public final void open(Player player) {
        manager.open(player, this);
    }

    public final EditorMenu parent() {
        return parent;
    }

    /** Go back one level (or to the main menu from the top). */
    public final void back(Player player) {
        if (parent != null) {
            manager.open(player, parent);
        } else {
            manager.openMain(player);
        }
    }

    protected final void set(Inventory inventory, int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    /** Fill the background of an inventory with the standard pane. */
    protected final void background(Inventory inventory) {
        EditorGui.fillEmpty(inventory);
    }

    /** Standard bottom bar: back arrow, search (optional), close. */
    protected final void navBar(Inventory inventory) {
        set(inventory, EditorGui.SLOT_BACK, EditorGui.icon(Material.ARROW, "&eBack",
                "&7Return to the previous menu."));
        set(inventory, EditorGui.SLOT_CLOSE, EditorGui.icon(Material.BARRIER, "&cClose",
                "&7Close the editor."));
    }

    protected final void pageButtons(Inventory inventory, int page, int pages) {
        if (page > 0) {
            set(inventory, EditorGui.SLOT_PAGE_PREV, EditorGui.icon(Material.PAPER,
                    "&ePrevious page", "&7Page " + page + " of " + pages));
        }
        if (page < pages - 1) {
            set(inventory, EditorGui.SLOT_PAGE_NEXT, EditorGui.icon(Material.PAPER,
                    "&eNext page", "&7Page " + (page + 2) + " of " + pages));
        }
    }

    /** The slice of a list shown on one page. */
    protected static <T> List<T> pageOf(List<T> all, int page) {
        if (all.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, page) * EditorGui.CONTENT_SIZE;
        if (from >= all.size()) {
            return List.of();
        }
        return all.subList(from, Math.min(all.size(), from + EditorGui.CONTENT_SIZE));
    }

    /** True when the slot is inside the content area. */
    protected static boolean inContent(int slot) {
        return slot >= EditorGui.CONTENT_FIRST && slot <= EditorGui.CONTENT_LAST;
    }

    /** Index of a content slot within the current page. */
    protected static int contentIndex(int slot) {
        return slot - EditorGui.CONTENT_FIRST;
    }

    protected final void error(Player player, String message) {
        manager.message(player, "&c" + message);
    }

    protected final void info(Player player, String message) {
        manager.message(player, message);
    }
}
