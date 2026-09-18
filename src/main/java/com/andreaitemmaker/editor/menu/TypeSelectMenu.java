package com.andreaitemmaker.editor.menu;

import com.andreaitemmaker.api.CustomItemType;
import com.andreaitemmaker.editor.EditorGui;
import com.andreaitemmaker.editor.EditorManager;
import com.andreaitemmaker.editor.EditorSchema;
import com.andreaitemmaker.editor.EditorSession;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Choose which kind of content to create. */
public final class TypeSelectMenu extends EditorMenu {

    private static final Material[] ICONS = {
            Material.DIAMOND_SWORD, Material.BOW, Material.DIAMOND_CHESTPLATE,
            Material.COOKED_BEEF, Material.STONE, Material.ARMOR_STAND};

    private final List<CustomItemType> types = new ArrayList<>(EditorSchema.CREATABLE);

    public TypeSelectMenu(EditorManager manager, EditorSession session, EditorMenu parent) {
        super(manager, session, parent);
    }

    @Override
    protected String title() {
        return "&8| &aCreate new content";
    }

    @Override
    protected void render(Inventory inventory) {
        background(inventory);
        set(inventory, 4, EditorGui.icon(Material.EMERALD, "&aChoose a content type",
                "&7Each type is a normal YAML file in its",
                "&7own folder (items/, blocks/, furniture/).",
                "",
                "&7Every type starts with a small template",
                "&7that you can fully edit in the GUI."));

        for (int i = 0; i < types.size(); i++) {
            CustomItemType type = types.get(i);
            set(inventory, 10 + i, EditorGui.icon(ICONS[i], "&b" + EditorSchema.label(type),
                    List.of("&7Stored in &f" + EditorSchema.folderFor(type) + "/",
                            "&7Type: &f" + type.name(),
                            "",
                            "&eClick to choose an id")));
        }
        navBar(inventory);
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        int index = slot - 10;
        if (index >= 0 && index < types.size()) {
            manager.createNew(player, types.get(index), this);
            return;
        }
        if (slot == EditorGui.SLOT_BACK || slot == EditorGui.SLOT_CLOSE) {
            if (slot == EditorGui.SLOT_BACK) {
                back(player);
            } else {
                player.closeInventory();
            }
        }
    }
}
