package com.andreaitemmaker.api.event;

import com.andreaitemmaker.api.CustomItem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** Fired when a player attacks an entity with a custom item (before damage is applied). */
public class CustomItemHitEvent extends CustomItemEvent {

    private final Entity target;
    private double damage;

    public CustomItemHitEvent(Player player, CustomItem item, Entity target, double damage) {
        super(player, item);
        this.target = target;
        this.damage = damage;
    }

    public Entity getTarget() {
        return target;
    }

    public double getDamage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = damage;
    }
}
