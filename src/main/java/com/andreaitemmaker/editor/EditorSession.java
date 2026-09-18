package com.andreaitemmaker.editor;

import com.andreaitemmaker.api.CustomItemType;
import com.andreaitemmaker.editor.menu.EditorMenu;

import java.util.UUID;

/**
 * One player's editor state: the document being edited, its working copy, the pending chat
 * prompt and the currently open menu.
 *
 * <p>Sessions are per player and never share mutable state, so two admins can edit two entries
 * (or even the same entry) at the same time without interfering.
 */
public final class EditorSession {

    private final UUID playerId;
    private EditorDocument document;
    private CustomItemType type = CustomItemType.ITEM;
    private EditorEntry original;
    private TextInputRequest pendingInput;
    private EditorMenu currentMenu;
    private long lastActivity = System.currentTimeMillis();

    public EditorSession(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }

    public EditorDocument document() {
        return document;
    }

    /** Start editing a document (a fresh working copy, or null to leave editing). */
    public void setDocument(EditorDocument document, CustomItemType type) {
        this.document = document;
        this.type = type == null ? CustomItemType.ITEM : type;
        touch();
    }

    /** The entry this session started from (null for brand-new content). */
    public EditorEntry original() {
        return original;
    }

    public void setOriginal(EditorEntry original) {
        this.original = original;
    }

    public CustomItemType type() {
        return type;
    }

    public void setType(CustomItemType type) {
        this.type = type;
    }

    public TextInputRequest pendingInput() {
        return pendingInput;
    }

    public void setPendingInput(TextInputRequest request) {
        this.pendingInput = request;
    }

    public void clearPendingInput() {
        this.pendingInput = null;
    }

    public EditorMenu currentMenu() {
        return currentMenu;
    }

    public void setCurrentMenu(EditorMenu menu) {
        this.currentMenu = menu;
    }

    public boolean isEditing() {
        return document != null;
    }

    public boolean isDirty() {
        return document != null && document.isDirty();
    }

    public long lastActivity() {
        return lastActivity;
    }

    public void touch() {
        this.lastActivity = System.currentTimeMillis();
    }
}
