package com.andreaitemmaker.editor.menu;

import com.andreaitemmaker.api.CustomItemType;
import com.andreaitemmaker.config.ContentLoader;
import com.andreaitemmaker.editor.EditorEntry;
import com.andreaitemmaker.editor.EditorField;
import com.andreaitemmaker.editor.EditorGui;
import com.andreaitemmaker.editor.EditorManager;
import com.andreaitemmaker.editor.EditorSession;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Picks a vanilla material. What is offered depends on the field: any item material, an armor
 * piece, or a valid custom-block base — the base-block filter is the exact same rule the loader
 * applies, so the picker cannot offer something that would be rejected.
 */
public final class MaterialPickerMenu extends EditorMenu {

    public enum Mode {
        ITEM,
        ARMOR,
        BLOCK_BASE
    }

    private final EditorField field;
    private final Mode mode;
    private final int page;
    private final String filter;

    public MaterialPickerMenu(EditorManager manager, EditorSession session, EditorMenu parent,
                              EditorField field, Mode mode, int page, String filter) {
        super(manager, session, parent);
        this.field = field;
        this.mode = mode;
        this.page = Math.max(0, page);
        this.filter = filter == null ? "" : filter;
    }

    private List<Material> candidates() {
        String needle = filter.toLowerCase(Locale.ROOT);
        List<Material> out = new ArrayList<>();
        for (Material material : Material.values()) {
            if (!allowed(material)) {
                continue;
            }
            if (!needle.isEmpty() && !material.name().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            out.add(material);
        }
        out.sort((a, b) -> a.name().compareTo(b.name()));
        return out;
    }

    private boolean allowed(Material material) {
        if (material.isAir() || !material.isItem()) {
            return false;
        }
        return switch (mode) {
            case ITEM -> true;
            case ARMOR -> isArmorPiece(material);
            case BLOCK_BASE -> ContentLoader.isValidBlockBase(material);
        };
    }

    @Override
    protected String title() {
        String label = switch (mode) {
            case ITEM -> "Pick a material";
            case ARMOR -> "Pick an armor material";
            case BLOCK_BASE -> "Pick a base block";
        };
        return "&8| &b" + label;
    }

    @Override
    protected void render(Inventory inventory) {
        background(inventory);
        List<Material> all = candidates();
        List<Material> shown = pageOf(all, page);
        int pages = EditorGui.pageCount(all.size(), EditorGui.CONTENT_SIZE);
        String current = session.document() == null ? null : session.document().getString(field.path());
        // Computed once per render rather than once per icon: the used-base scan is not free.
        Map<Material, String> usedBases = mode == Mode.BLOCK_BASE ? usedBases() : Map.of();

        set(inventory, 4, EditorGui.icon(Material.CHEST, "&b" + field.label(),
                List.of("&7Field: &f" + field.path(),
                        "&7Current: " + EditorGui.display(current),
                        "&7Matching materials: &f" + all.size(),
                        "&7Page &f" + (page + 1) + "&7/&f" + pages,
                        filter.isEmpty() ? "" : "&7Filter: &f" + filter,
                        "",
                        "&7Left click &8- &7use this material",
                        "&7Right click &8- &7type the name in chat")));

        for (int i = 0; i < shown.size(); i++) {
            Material material = shown.get(i);
            String owner = usedBases.get(material);
            boolean used = owner != null;
            List<String> lore = new ArrayList<>();
            lore.add("&7Material: &f" + material.name().toLowerCase(Locale.ROOT));
            if (used) {
                lore.add("&cAlready used by '&f" + owner + "&c'");
                lore.add("&7One base block backs only one custom block.");
            } else {
                lore.add("&eLeft click &7to use it");
            }
            lore.add("&eRight click &7to type an exact name");
            inventory.setItem(EditorGui.CONTENT_FIRST + i, EditorGui.icon(used
                    ? Material.BARRIER : (material.isItem() ? material : Material.PAPER),
                    (used ? "&c" : "&b") + material.name().toLowerCase(Locale.ROOT), lore));
        }

        set(inventory, EditorGui.SLOT_BACK, EditorGui.icon(Material.ARROW, "&eBack",
                "&7Return without changing the value."));
        set(inventory, EditorGui.SLOT_SEARCH, EditorGui.icon(Material.COMPASS, "&eFilter",
                List.of("&7Only show materials containing some text.",
                        "&7Current filter: " + (filter.isEmpty() ? "&8(none)" : "&f" + filter),
                        "",
                        "&eClick to type a filter")));
        set(inventory, 47, EditorGui.icon(Material.BUCKET, "&eRemove this value",
                "&7Clears the field (when it is optional)."));
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
        if (slot == EditorGui.SLOT_SEARCH) {
            manager.promptMaterialFilter(player, this, field, mode, page);
            return;
        }
        if (slot == 47) {
            manager.applyFieldValue(session, field, null);
            manager.message(player, "&eCleared &f" + field.label() + "&e.");
            back(player);
            return;
        }
        if (slot == EditorGui.SLOT_PAGE_PREV) {
            new MaterialPickerMenu(manager, session, parent, field, mode, page - 1, filter).open(player);
            return;
        }
        if (slot == EditorGui.SLOT_PAGE_NEXT) {
            new MaterialPickerMenu(manager, session, parent, field, mode, page + 1, filter).open(player);
            return;
        }
        if (!inContent(slot)) {
            return;
        }
        List<Material> shown = pageOf(candidates(), page);
        int index = contentIndex(slot);
        if (index >= shown.size()) {
            return;
        }
        Material material = shown.get(index);
        if (click == ClickType.RIGHT) {
            manager.promptMaterialName(player, this, field);
            return;
        }
        String owner = mode == Mode.BLOCK_BASE ? manager.baseBlockOwner(material, session) : null;
        if (owner != null) {
            error(player, "base block " + material.name().toLowerCase(Locale.ROOT)
                    + " is already used by '" + owner + "'");
            return;
        }
        manager.applyFieldValue(session, field, material.name().toLowerCase(Locale.ROOT));
        manager.message(player, "&a" + field.label() + " &7set to &f"
                + material.name().toLowerCase(Locale.ROOT));
        back(player);
    }

    /** Base block to owning entry id, for every other custom block. */
    private Map<Material, String> usedBases() {
        List<EditorEntry> blocks = manager.repository().entries(CustomItemType.BLOCK);
        Map<Material, String> out = new java.util.HashMap<>();
        for (EditorEntry entry : blocks) {
            if (entry.baseBlock() != null) {
                out.putIfAbsent(entry.baseBlock(), entry.id());
            }
        }
        return out;
    }

    static boolean isArmorPiece(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

}
