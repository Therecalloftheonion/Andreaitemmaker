package com.andreaitemmaker.editor.menu;

import com.andreaitemmaker.editor.EditorGui;
import com.andreaitemmaker.editor.EditorManager;
import com.andreaitemmaker.editor.EditorNode;
import com.andreaitemmaker.editor.EditorSession;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Edits a {@code texture:} (or {@code armor-texture:}) node in both of the forms the loader
 * accepts: a pattern section (pattern/color/color2/outline), a plain hex colour, or a .png path.
 *
 * <p>Section values are written through {@link EditorNode}, which creates real YAML sections, so
 * the in-memory document always has the same shape as the file that gets saved.
 */
public final class TextureMenu extends EditorMenu {

    private static final List<String> PATTERNS = List.of("solid", "gradient", "diagonal", "checker");

    private final String path;
    private final EditorNode node;

    public TextureMenu(EditorManager manager, EditorSession session, EditorMenu parent, String path) {
        super(manager, session, parent);
        this.path = path;
        this.node = new EditorNode.SectionNode(session.document(), path);
    }

    private Map<String, Object> section() {
        return node.entries();
    }

    private boolean isPlainValue() {
        return session.document().get(path) instanceof String;
    }

    private boolean outline() {
        Object value = section().get("outline");
        return value == null || Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    /** Makes sure the node is a section with a pattern and a colour, then returns it. */
    private Map<String, Object> ensureSection() {
        Map<String, Object> section = section();
        if (!(section.get("pattern") instanceof String)) {
            node.put("pattern", "gradient");
        }
        if (!(section.get("color") instanceof String)) {
            node.put("color", "#ffffff");
        }
        if (!section.containsKey("outline")) {
            node.put("outline", true);
        }
        return section();
    }

    @Override
    protected String title() {
        return "&8| &b" + (path.equals("armor-texture") ? "Worn armor texture" : "Texture");
    }

    @Override
    protected void render(Inventory inventory) {
        background(inventory);
        Object raw = session.document().get(path);
        Map<String, Object> section = section();
        String pattern = String.valueOf(section.getOrDefault("pattern", "gradient"));

        set(inventory, 4, EditorGui.icon(Material.PAINTING, "&b" + path,
                List.of("&7Field: &f" + path,
                        "&7Current: " + EditorGui.display(raw),
                        "&7Form: &f" + (raw == null ? "not set" : isPlainValue() ? "plain value" : "pattern section"),
                        "",
                        "&7A pattern section generates a texture.",
                        "&7A hex colour fills the whole item.",
                        "&7A .png path uses your own texture.")));

        set(inventory, 10, EditorGui.icon(Material.COMPARATOR, "&bPattern &8- &f" + pattern,
                List.of("&7Values: " + String.join(", ", PATTERNS),
                        "&7Only used when the texture is a section.",
                        "",
                        "&eClick to cycle")));
        set(inventory, 11, EditorGui.icon(Material.RED_DYE, "&bBase color",
                List.of("&7Current: " + EditorGui.display(section.get("color")),
                        "&7Hex colour, e.g. &f#4f7cff",
                        "",
                        "&eClick to type it")));
        set(inventory, 12, EditorGui.icon(Material.LIME_DYE, "&bSecond color",
                List.of("&7Current: " + EditorGui.display(section.get("color2")),
                        "&7Used by the gradient/diagonal patterns.",
                        "",
                        "&eClick to type it (or clear it)")));
        set(inventory, 13, EditorGui.toggle("&bOutline &8- &f" + outline(), outline(),
                "&7Draws a darker outline around the texture."));
        set(inventory, 15, EditorGui.icon(Material.ITEM_FRAME, "&bUse a .png texture",
                List.of("&7Path inside &fassets/textures/&7,",
                        "&7e.g. assets/textures/my_item.png",
                        "",
                        "&eClick to type the path")));
        set(inventory, 16, EditorGui.icon(Material.BUCKET, "&cRemove the texture",
                "&7Falls back to a plain generated texture."));

        set(inventory, EditorGui.SLOT_BACK, EditorGui.icon(Material.ARROW, "&eBack",
                "&7Return to the fields."));
        set(inventory, EditorGui.SLOT_CLOSE, EditorGui.icon(Material.BARRIER, "&cClose",
                "&7Close the editor."));
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        switch (slot) {
            case 10 -> {
                Map<String, Object> section = ensureSection();
                String current = String.valueOf(section.getOrDefault("pattern", "gradient"));
                int index = PATTERNS.indexOf(current.toLowerCase(Locale.ROOT));
                node.put("pattern", PATTERNS.get((index + 1 + PATTERNS.size()) % PATTERNS.size()));
                refresh();
            }
            case 11 -> manager.promptTextureColor(player, this, path, "color");
            case 12 -> manager.promptTextureColor(player, this, path, "color2");
            case 13 -> {
                ensureSection();
                node.put("outline", !outline());
                refresh();
            }
            case 15 -> manager.promptTextureFile(player, this, path);
            case 16 -> {
                session.document().remove(path);
                manager.message(player, "&eRemoved &f" + path + "&e.");
                refresh();
            }
            case EditorGui.SLOT_BACK -> back(player);
            case EditorGui.SLOT_CLOSE -> player.closeInventory();
            default -> {
                // ignore
            }
        }
    }
}
