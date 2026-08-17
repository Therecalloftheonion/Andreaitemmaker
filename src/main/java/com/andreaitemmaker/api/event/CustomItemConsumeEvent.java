package com.andreaitemmaker.api.event;

import com.andreaitemmaker.api.CustomItem;
import org.bukkit.entity.Player;

/** Fired when a player consumes a custom FOOD item. */
public class CustomItemConsumeEvent extends CustomItemEvent {

    private final int hunger;
    private final float saturation;

    public CustomItemConsumeEvent(Player player, CustomItem item, int hunger, float saturation) {
        super(player, item);
        this.hunger = hunger;
        this.saturation = saturation;
    }

    /** Hunger points restored. */
    public int getHunger() {
        return hunger;
    }

    /** Saturation restored. */
    public float getSaturation() {
        return saturation;
    }
}
