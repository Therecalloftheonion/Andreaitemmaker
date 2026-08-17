package com.andreaitemmaker.util;

import org.bukkit.Sound;

import java.util.Locale;

/** Parses sound names from config (e.g. "block.wood.place", "minecraft:block.wood.break", "ENTITY_PLAYER_BURP"). */
public final class Sounds {

    private Sounds() {
    }

    /** Normalize a sound name to a Bukkit {@link Sound}, or null when unknown/empty. */
    public static Sound parse(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String n = name.trim();
        if (n.startsWith("minecraft:")) {
            n = n.substring("minecraft:".length());
        }
        n = n.replace('/', '_').replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return Sound.valueOf(n);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
