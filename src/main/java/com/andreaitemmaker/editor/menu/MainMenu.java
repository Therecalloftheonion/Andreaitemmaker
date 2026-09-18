package com.andreaitemmaker.editor.menu;

import com.andreaitemmaker.api.CustomItemType;
import com.andreaitemmaker.editor.EditorGui;
import com.andreaitemmaker.editor.EditorManager;
import com.andreaitemmaker.editor.EditorSession;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.List;

/** The editor hub: pick a content category, search, create, validate or open settings. */
public final class MainMenu extends EditorMenu {

    public MainMenu(EditorManager manager, EditorSession session) {
        super(manager, session, null);
    }

    @Override
    protected String title() {
        return "&8| &bAndreaitemmaker editor";
    }

    @Override
    protected void render(Inventory inventory) {
        background(inventory);

        int total = manager.repository().entries().size();
        set(inventory, 4, EditorGui.icon(Material.BOOK, "&bAndreaitemmaker editor",
                List.of("&7Create, edit and validate custom content",
                        "&7without touching the YAML by hand.",
                        "",
                        "&7Entries on disk: &f" + total,
                        "&7Reload running: &f" + manager.plugin().isReloading())));

        set(inventory, 10, category(Material.DIAMOND_SWORD, CustomItemType.ITEM));
        set(inventory, 11, category(Material.BOW, CustomItemType.WEAPON));
        set(inventory, 12, category(Material.DIAMOND_CHESTPLATE, CustomItemType.ARMOR));
        set(inventory, 13, category(Material.COOKED_BEEF, CustomItemType.FOOD));
        set(inventory, 14, category(Material.STONE, CustomItemType.BLOCK));
        set(inventory, 15, category(Material.ARMOR_STAND, CustomItemType.FURNITURE));

        set(inventory, 29, EditorGui.icon(Material.COMPASS, "&eSearch",
                "&7Find content by id or display name",
                "&7across every category.",
                "",
                "&eClick to type a query in chat"));
        set(inventory, 31, EditorGui.icon(Material.EMERALD, "&aCreate new content",
                "&7Pick a type, choose an id, then",
                "&7edit every field in the GUI.",
                "",
                "&eClick to start"));
        set(inventory, 33, EditorGui.icon(Material.WRITABLE_BOOK, "&eValidate all content",
                "&7Re-checks every file on disk and",
                "&7lists problems with the exact field.",
                "",
                "&eClick to run"));
        set(inventory, 39, EditorGui.icon(Material.HOPPER, "&eSettings & diagnostics",
                "&7Pack status, pack controls and the",
                "&7safe plugin options.",
                "",
                "&eClick to open"));
        set(inventory, 41, EditorGui.icon(Material.PISTON, "&eReload content",
                "&7Reload config + content and regenerate",
                "&7the pack (runs in the background).",
                "",
                "&eClick to reload"));

        set(inventory, EditorGui.SLOT_CLOSE, EditorGui.icon(Material.BARRIER, "&cClose",
                "&7Close the editor."));
    }

    private org.bukkit.inventory.ItemStack category(Material icon, CustomItemType type) {
        int count = manager.repository().entries(type).size();
        return EditorGui.icon(icon, "&b" + com.andreaitemmaker.editor.EditorSchema.label(type),
                List.of("&7" + count + " entr" + (count == 1 ? "y" : "ies") + " on disk",
                        "",
                        "&7Left click  &8- &7list and edit",
                        "&7Right click &8- &7create a new one"));
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        switch (slot) {
            case 10 -> manager.openList(player, CustomItemType.ITEM, this);
            case 11 -> manager.openList(player, CustomItemType.WEAPON, this);
            case 12 -> manager.openList(player, CustomItemType.ARMOR, this);
            case 13 -> manager.openList(player, CustomItemType.FOOD, this);
            case 14 -> manager.openList(player, CustomItemType.BLOCK, this);
            case 15 -> manager.openList(player, CustomItemType.FURNITURE, this);
            case 29 -> manager.promptSearch(player, this);
            case 31 -> new TypeSelectMenu(manager, session, this).open(player);
            case 33 -> manager.validateAll(player, this);
            case 39 -> new SettingsMenu(manager, session, this).open(player);
            case 41 -> {
                manager.message(player, "&eReload requested; content and the pack reload in the background.");
                manager.plugin().reloadAll();
            }
            case EditorGui.SLOT_CLOSE -> player.closeInventory();
            default -> {
                // nothing bound to this slot
            }
        }
    }
}
