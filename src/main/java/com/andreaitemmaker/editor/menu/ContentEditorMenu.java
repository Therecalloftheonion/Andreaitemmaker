package com.andreaitemmaker.editor.menu;

import com.andreaitemmaker.editor.EditorDocument;
import com.andreaitemmaker.editor.EditorField;
import com.andreaitemmaker.editor.EditorGui;
import com.andreaitemmaker.editor.EditorManager;
import com.andreaitemmaker.editor.EditorSchema;
import com.andreaitemmaker.editor.EditorSession;
import com.andreaitemmaker.editor.FieldKind;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Edits one content entry: every schema field of its type, plus save/validate/preview and the
 * entry level actions. Nothing is written until Save is pressed — edits live in the session's
 * working copy.
 */
public final class ContentEditorMenu extends EditorMenu {

    private final int page;

    public ContentEditorMenu(EditorManager manager, EditorSession session, EditorMenu parent, int page) {
        super(manager, session, parent);
        this.page = Math.max(0, page);
    }

    private EditorDocument document() {
        return session.document();
    }

    private List<EditorField> fields() {
        return EditorSchema.fields(session.type());
    }

    @Override
    protected String title() {
        EditorDocument doc = document();
        String id = doc == null ? "?" : doc.id();
        return "&8| &b" + id + (doc != null && doc.isDirty() ? " &c*" : "");
    }

    @Override
    protected void render(Inventory inventory) {
        background(inventory);
        EditorDocument doc = document();
        if (doc == null) {
            set(inventory, 22, EditorGui.icon(Material.BARRIER, "&cNothing to edit"));
            navBar(inventory);
            return;
        }
        List<EditorField> all = fields();
        List<EditorField> shown = pageOf(all, page);
        int pages = EditorGui.pageCount(all.size(), EditorGui.CONTENT_SIZE);
        var unknown = doc.unknownTopLevelKeys(session.type());

        set(inventory, 4, EditorGui.icon(Material.NAME_TAG, "&b" + doc.id(),
                List.of("&7Type: &f" + session.type().name(),
                        "&7File: &f" + doc.file().getName(),
                        "&7Status: " + (doc.isDirty() ? "&cunsaved changes" : "&asaved"),
                        "&7Page &f" + (page + 1) + "&7/&f" + pages,
                        "&7Undo steps available: &f" + doc.undoDepth(),
                        unknown.isEmpty() ? "" : "&7Preserved extra keys: &f" + unknown.size(),
                        "",
                        "&7Click a field to edit it.",
                        "&7Nothing is written until you press Save.")));

        for (int i = 0; i < shown.size(); i++) {
            set(inventory, EditorGui.CONTENT_FIRST + i, fieldIcon(doc, shown.get(i)));
        }

        set(inventory, 0, EditorGui.icon(Material.MILK_BUCKET, "&eRevert to the saved file",
                List.of("&7Throws away every unsaved change and",
                        "&7reloads the entry from disk.",
                        "&7The revert itself can be undone.")));
        set(inventory, 3, EditorGui.icon(doc.canUndo() ? Material.SPECTRAL_ARROW : Material.GRAY_DYE,
                (doc.canUndo() ? "&e" : "&7") + "Undo" + (doc.canUndo() ? " &8(" + doc.undoDepth() + ")" : ""),
                List.of("&7Steps back one edit.",
                        "&7One step = one click or one value typed",
                        "&7in chat, so a field change is never split up.",
                        "",
                        doc.canUndo() ? "&eClick to undo" : "&7Nothing to undo yet")));
        set(inventory, 5, EditorGui.icon(doc.canRedo() ? Material.ARROW : Material.GRAY_DYE,
                (doc.canRedo() ? "&e" : "&7") + "Redo" + (doc.canRedo() ? " &8(" + doc.redoDepth() + ")" : ""),
                List.of("&7Steps forward again after an undo.",
                        "&7Editing anything new clears the redo steps.",
                        "",
                        doc.canRedo() ? "&eClick to redo" : "&7Nothing to redo")));
        set(inventory, EditorGui.SLOT_BACK, EditorGui.icon(Material.ARROW, "&eBack",
                "&7Return to the list (unsaved changes are kept)."));
        set(inventory, 47, EditorGui.icon(Material.EMERALD, "&aDuplicate",
                "&7Copy this entry under a new id."));
        set(inventory, EditorGui.SLOT_CONFIRM, EditorGui.icon(
                        doc.isDirty() ? Material.EMERALD_BLOCK : Material.COAL_BLOCK,
                        doc.isDirty() ? "&aSave" : "&7Save (no changes)",
                        List.of("&7Validates the entry, writes the YAML",
                                "&7file and reloads the content.")));
        set(inventory, EditorGui.SLOT_SEARCH, EditorGui.icon(Material.WRITABLE_BOOK, "&eValidate",
                "&7Check this entry and list every problem",
                "&7with the exact field."));
        set(inventory, 50, EditorGui.icon(Material.ITEM_FRAME, "&eGive a preview item",
                "&7Builds the item from the current (unsaved)",
                "&7values and puts it in your inventory."));
        if (!unknown.isEmpty()) {
            set(inventory, 51, EditorGui.icon(Material.CHEST, "&ePreserved fields (" + unknown.size() + ")",
                    List.of("&7Keys this editor does not manage.",
                            "&7They are kept exactly as they are",
                            "&7when the file is saved.",
                            "",
                            "&eClick to review")));
        }
        set(inventory, EditorGui.SLOT_CLOSE, EditorGui.icon(Material.BARRIER, "&cClose",
                "&7Close without saving."));
        pageButtons(inventory, page, pages);
    }

    private org.bukkit.inventory.ItemStack fieldIcon(EditorDocument doc, EditorField field) {
        Object value = doc.get(field.path());
        List<String> lore = new ArrayList<>(EditorGui.wrap(field.description(), 38));
        lore.add("");
        if (field.kind() == FieldKind.BOOLEAN) {
            lore.add("&7Current: " + (doc.getBoolean(field.path(), false) ? "&atrue" : "&cfalse"));
            lore.add("&eClick to toggle");
        } else {
            lore.add("&7Current: " + EditorGui.display(value));
            lore.add("&eClick to edit");
        }
        if (field.required()) {
            lore.add("&7Required by the loader.");
        }
        return EditorGui.icon(iconFor(doc, field), "&b" + field.label(), lore);
    }

    private Material iconFor(EditorDocument doc, EditorField field) {
        return switch (field.kind()) {
            case STRING -> Material.NAME_TAG;
            case COLORED_STRING -> Material.PAPER;
            case TEXT_LIST -> Material.WRITABLE_BOOK;
            case INT, DOUBLE -> Material.REPEATER;
            case BOOLEAN -> doc.getBoolean(field.path(), false) ? Material.LIME_DYE : Material.GRAY_DYE;
            case ENUM -> Material.COMPARATOR;
            case MATERIAL, BASE_BLOCK -> {
                String raw = doc.getString(field.path());
                Material material = raw == null ? null : Material.matchMaterial(raw.trim());
                yield material != null && material.isItem() ? material : Material.STRUCTURE_VOID;
            }
            case SOUND -> Material.NOTE_BLOCK;
            case TEXTURE -> Material.PAINTING;
            case ASSET_PATH -> Material.ITEM_FRAME;
            case ATTRIBUTES -> Material.IRON_CHESTPLATE;
            case ENCHANTMENTS -> Material.ENCHANTED_BOOK;
            case MECHANICS -> Material.COMMAND_BLOCK;
        };
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
        if (slot == 0) {
            manager.revert(player, session, this);
            return;
        }
        if (slot == 3) {
            manager.undo(player, session, this);
            return;
        }
        if (slot == 5) {
            manager.redo(player, session, this);
            return;
        }
        if (slot == 47) {
            manager.duplicate(player, session);
            return;
        }
        if (slot == EditorGui.SLOT_CONFIRM) {
            manager.save(player, session, false, this);
            return;
        }
        if (slot == EditorGui.SLOT_SEARCH) {
            manager.validateAndShow(player, session, this);
            return;
        }
        if (slot == 50) {
            manager.givePreview(player, session);
            return;
        }
        if (slot == 51) {
            new UnknownFieldsMenu(manager, session, this).open(player);
            return;
        }
        if (slot == EditorGui.SLOT_PAGE_PREV) {
            new ContentEditorMenu(manager, session, parent, page - 1).open(player);
            return;
        }
        if (slot == EditorGui.SLOT_PAGE_NEXT) {
            new ContentEditorMenu(manager, session, parent, page + 1).open(player);
            return;
        }
        if (!inContent(slot)) {
            return;
        }
        List<EditorField> shown = pageOf(fields(), page);
        int index = contentIndex(slot);
        if (index >= shown.size()) {
            return;
        }
        manager.openField(player, this, shown.get(index));
    }
}
