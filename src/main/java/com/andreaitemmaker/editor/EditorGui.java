package com.andreaitemmaker.editor;

import com.andreaitemmaker.util.Chat;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Small, dependency-free helpers for building the editor's inventory GUIs. */
public final class EditorGui {

    public static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;

    /** Slots usable for content in every editor menu (a 6 row inventory). */
    public static final int CONTENT_FIRST = 9;
    public static final int CONTENT_LAST = 44;
    public static final int CONTENT_SIZE = CONTENT_LAST - CONTENT_FIRST + 1;
    public static final int SLOT_BACK = 45;
    public static final int SLOT_CONFIRM = 48;
    public static final int SLOT_SEARCH = 49;
    public static final int SLOT_PAGE_PREV = 46;
    public static final int SLOT_PAGE_NEXT = 52;
    public static final int SLOT_CLOSE = 53;

    private EditorGui() {
    }

    public static ItemStack filler() {
        return icon(FILLER, " ", List.of());
    }

    public static ItemStack icon(Material material, String name, String... lore) {
        return icon(material, name, Arrays.asList(lore));
    }

    public static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Chat.color(name));
            if (!lore.isEmpty()) {
                meta.setLore(lore.stream().map(Chat::color).toList());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Fill every empty slot with the background pane. */
    public static void fillEmpty(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler());
            }
        }
    }

    /** A boolean rendered as a clickable toggle icon. */
    public static ItemStack toggle(String label, boolean value, String... lore) {
        List<String> lines = new ArrayList<>(Arrays.asList(lore));
        lines.add("");
        lines.add("&7Current: " + (value ? "&atrue" : "&cfalse"));
        lines.add("&eClick to toggle");
        return icon(value ? Material.LIME_DYE : Material.GRAY_DYE, label, lines);
    }

    /** Every field value is rendered through this so it is never null or confusing. */
    public static String display(Object value) {
        if (value == null) {
            return "&8(not set)";
        }
        if (value instanceof java.util.Map<?, ?>) {
            return "&7<section>";
        }
        if (value instanceof List<?> list) {
            return "&7" + list.size() + " entr" + (list.size() == 1 ? "y" : "ies");
        }
        String text = String.valueOf(value);
        return text.isEmpty() ? "&8(empty)" : "&7" + text;
    }

    public static int pageCount(int total, int perPage) {
        if (total <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(total / (double) perPage));
    }

    /** Word-wrap a help text into lore lines. */
    public static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add("&7" + line);
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add("&7" + line);
        }
        return lines;
    }
}
