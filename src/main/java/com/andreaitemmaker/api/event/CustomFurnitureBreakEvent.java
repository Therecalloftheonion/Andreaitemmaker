package com.andreaitemmaker.api.event;

import com.andreaitemmaker.api.CustomFurniture;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

/** Fired when a furniture piece is picked up or destroyed. */
public class CustomFurnitureBreakEvent extends CustomItemEvent {

    private final ArmorStand armorStand;

    public CustomFurnitureBreakEvent(Player player, CustomFurniture item, ArmorStand armorStand) {
        super(player, item);
        this.armorStand = armorStand;
    }

    public ArmorStand getArmorStand() {
        return armorStand;
    }

    @Override
    public CustomFurniture getItem() {
        return (CustomFurniture) super.getItem();
    }
}
