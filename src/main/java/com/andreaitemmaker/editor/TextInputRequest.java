package com.andreaitemmaker.editor;

import com.andreaitemmaker.editor.menu.EditorMenu;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A pending chat input for one editor field.
 *
 * <p>Minecraft inventories cannot take free text, so the editor closes the GUI, asks in chat and
 * captures the next message the player sends. The request remembers exactly which field it
 * belongs to and where to return, expires after two minutes, and is cleared as soon as the
 * player navigates anywhere else — a stray chat message can never be swallowed.
 *
 * @param path      the YAML field being edited (for messages and validation)
 * @param label     human readable field name
 * @param prompt    chat lines to send
 * @param validator returns null when the input is acceptable, or the error to show
 * @param onAccept  applies the accepted value
 * @param returnTo  the menu re-opened once the input is handled
 * @param createdAt when the request was created (for expiry)
 */
public record TextInputRequest(String path, String label, List<String> prompt,
                               Function<String, String> validator,
                               BiConsumer<Player, String> onAccept,
                               EditorMenu returnTo, long createdAt) {

    /** How long an unanswered prompt stays armed. */
    public static final long TIMEOUT_MILLIS = 120_000L;

    public boolean expired(long now) {
        return now - createdAt > TIMEOUT_MILLIS;
    }
}
