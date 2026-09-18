package com.andreaitemmaker.editor.menu;

import com.andreaitemmaker.editor.EditorGui;
import com.andreaitemmaker.editor.EditorIssue;
import com.andreaitemmaker.editor.EditorManager;
import com.andreaitemmaker.editor.EditorSession;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the validation result: every problem with its field and cause. Errors block saving, so a
 * "force save" button is offered for the cases where the admin knows better.
 */
public final class ValidationMenu extends EditorMenu {

    private final String heading;
    private final List<EditorIssue> issues;
    private final Runnable onForceSave;
    private final int page;

    public ValidationMenu(EditorManager manager, EditorSession session, EditorMenu parent,
                          String heading, List<EditorIssue> issues, Runnable onForceSave) {
        this(manager, session, parent, heading, issues, onForceSave, 0);
    }

    public ValidationMenu(EditorManager manager, EditorSession session, EditorMenu parent,
                          String heading, List<EditorIssue> issues, Runnable onForceSave, int page) {
        super(manager, session, parent);
        this.heading = heading;
        this.issues = issues;
        this.onForceSave = onForceSave;
        this.page = Math.max(0, page);
    }

    /** Validation of any list of issues, from a save attempt or from the whole content folder. */
    public static ValidationMenu of(EditorManager manager, EditorSession session, EditorMenu parent,
                                    String heading, List<EditorIssue> issues, Runnable onForceSave) {
        return new ValidationMenu(manager, session, parent, heading, issues, onForceSave, 0);
    }

    @Override
    protected String title() {
        return "&8| Validation";
    }

    @Override
    protected void render(Inventory inventory) {
        background(inventory);
        long errors = issues.stream().filter(EditorIssue::isError).count();
        long warnings = issues.size() - errors;
        List<EditorIssue> shown = pageOf(issues, page);
        int pages = EditorGui.pageCount(issues.size(), EditorGui.CONTENT_SIZE);

        set(inventory, 4, EditorGui.icon(issues.isEmpty() ? Material.LIME_DYE : Material.WRITABLE_BOOK,
                "&b" + heading,
                List.of("&7Errors: &c" + errors,
                        "&7Warnings: &e" + warnings,
                        "&7Page &f" + (page + 1) + "&7/&f" + pages,
                        "",
                        "&7Errors must be fixed before saving",
                        "&7(unless you force the save).",
                        "&7Click an entry to print the detail in chat.")));

        if (issues.isEmpty()) {
            set(inventory, 22, EditorGui.icon(Material.LIME_CONCRETE, "&aNo problems found",
                    "&7The entry is valid and savable."));
        }
        for (int i = 0; i < shown.size(); i++) {
            EditorIssue issue = shown.get(i);
            set(inventory, EditorGui.CONTENT_FIRST + i, issueIcon(issue));
        }

        set(inventory, EditorGui.SLOT_BACK, EditorGui.icon(Material.ARROW, "&eBack",
                "&7Return to the editor."));
        set(inventory, EditorGui.SLOT_CLOSE, EditorGui.icon(Material.BARRIER, "&cClose",
                "&7Close the editor."));
        if (onForceSave != null && errors > 0) {
            set(inventory, EditorGui.SLOT_CONFIRM, EditorGui.icon(Material.TNT, "&cForce save",
                    List.of("&7Writes the file even though it has errors.",
                            "&7The loader may skip it — the previous",
                            "&7version is kept in backups/editor.")));
        }
        pageButtons(inventory, page, pages);
    }

    private org.bukkit.inventory.ItemStack issueIcon(EditorIssue issue) {
        List<String> lore = new ArrayList<>(EditorGui.wrap(issue.message(), 38));
        lore.add("");
        lore.add("&7Field: &f" + (issue.field().isEmpty() ? "(whole file)" : issue.field()));
        lore.add("&7Severity: " + (issue.isError() ? "&cerror" : "&ewarning"));
        return EditorGui.icon(issue.isError() ? Material.RED_DYE : Material.YELLOW_DYE,
                (issue.isError() ? "&c" : "&e") + (issue.field().isEmpty() ? "file" : issue.field()), lore);
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        if (slot == EditorGui.SLOT_BACK) {
            back(player);
            return;
        }
        if (slot == EditorGui.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == EditorGui.SLOT_CONFIRM && onForceSave != null) {
            onForceSave.run();
            return;
        }
        if (slot == EditorGui.SLOT_PAGE_PREV) {
            new ValidationMenu(manager, session, parent, heading, issues, onForceSave, page - 1).open(player);
            return;
        }
        if (slot == EditorGui.SLOT_PAGE_NEXT) {
            new ValidationMenu(manager, session, parent, heading, issues, onForceSave, page + 1).open(player);
            return;
        }
        if (!inContent(slot)) {
            return;
        }
        List<EditorIssue> shown = pageOf(issues, page);
        int index = contentIndex(slot);
        if (index < shown.size()) {
            EditorIssue issue = shown.get(index);
            manager.message(player, (issue.isError() ? "&c" : "&e") + "[" + issue.field() + "] &f"
                    + issue.message());
        }
    }
}
