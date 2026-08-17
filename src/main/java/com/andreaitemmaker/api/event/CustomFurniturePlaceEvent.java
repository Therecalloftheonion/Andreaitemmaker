package com.andreaitemmaker.api.event;

import com.andreaitemmaker.api.CustomFurniture;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Fired when a player places furniture (before the armor stand spawns). */
public class CustomFurniturePlaceEvent extends CustomItemEvent {

    private final Location location;

    public CustomFurniturePlaceEvent(Player player, CustomFurniture item, Location location) {
        super(player, item);
        this.location = location;
    }

    /** Where the armor stand will spawn. */
    public Location getLocation() {
        return location;
    }

    @Override
    public CustomFurniture getItem() {
        return (CustomFurniture) super.getItem();
    }
}
