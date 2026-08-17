package com.andreaitemmaker.util;

import org.bukkit.ChatColor;

/** Message formatting helpers (legacy '&' color codes). */
public final class Chat {

    private Chat() {
    }

    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    public static String prefix() {
        return color("&8[&bAndreaitemmaker&8]&r ");
    }
}
