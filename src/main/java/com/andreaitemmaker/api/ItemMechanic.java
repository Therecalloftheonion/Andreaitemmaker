package com.andreaitemmaker.api;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A modular item behavior. Built-in mechanics ship with the plugin; other plugins can
 * register their own through {@link AndreaitemmakerAPI#getMechanicRegistry()}.
 *
 * <p>Mechanics are referenced from item YAMLs by {@link #getId()}:
 * <pre>{@code
 * mechanics:
 *   my_mechanic:
 *     some_option: 5
 * }</pre>
 * The config map is available at runtime through {@link CustomItem#getMechanics()}.
 */
public interface ItemMechanic {

    /** Unique id used in config files, e.g. "heal". */
    String getId();

    /** Called when a player right-clicks with an item that has this mechanic. */
    default boolean onUse(MechanicContext context) {
        return false;
    }

    /** Called when a player attacks an entity with an item that has this mechanic. */
    default void onHitEntity(MechanicContext context, Entity target) {
    }

    /** Called periodically while a player wears an armor piece that has this mechanic. */
    default void onWornTick(Player player, ItemStack stack, CustomItem item) {
    }
}
