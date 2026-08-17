package com.andreaitemmaker.api.event;

import com.andreaitemmaker.api.CustomItem;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;

/** Fired when a player right-clicks (air or block) while holding a custom item. */
public class CustomItemUseEvent extends CustomItemEvent {

    private final EquipmentSlot hand;
    private final Action action;

    public CustomItemUseEvent(Player player, CustomItem item, EquipmentSlot hand, Action action) {
        super(player, item);
        this.hand = hand;
        this.action = action;
    }

    public EquipmentSlot getHand() {
        return hand;
    }

    public Action getAction() {
        return action;
    }
}
