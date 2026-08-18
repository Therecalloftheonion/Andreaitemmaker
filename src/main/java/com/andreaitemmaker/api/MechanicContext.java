package com.andreaitemmaker.api;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Passed to {@link ItemMechanic} hooks. Carries the triggering player, item and stack,
 * plus a small per-player cooldown helper shared across all mechanics.
 */
public final class MechanicContext {

    private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();

    private final Player player;
    private final PlayerEvent event;
    private final CustomItem item;
    private final ItemStack stack;
    private final EquipmentSlot hand;

    public MechanicContext(Player player, PlayerEvent event, CustomItem item, ItemStack stack, EquipmentSlot hand) {
        this.player = player;
        this.event = event;
        this.item = item;
        this.stack = stack;
        this.hand = hand;
    }

    public Player getPlayer() {
        return player;
    }

    /** The triggering event, may be null (e.g. worn-tick hooks). */
    public PlayerEvent getEvent() {
        return event;
    }

    public CustomItem getItem() {
        return item;
    }

    public ItemStack getStack() {
        return stack;
    }

    public EquipmentSlot getHand() {
        return hand;
    }

    /**
     * Start a cooldown for {@code mechanic} on this player+item.
     *
     * @return false when the cooldown is still active (and nothing was changed)
     */
    public boolean tryCooldown(String mechanic, int seconds) {
        if (hasCooldown(mechanic)) {
            return false;
        }
        COOLDOWNS.put(key(player, item.getId(), mechanic), System.currentTimeMillis() + seconds * 1000L);
        return true;
    }

    /** Remaining cooldown for {@code mechanic} in seconds, 0 when not active. */
    public long cooldownRemaining(String mechanic) {
        return cooldownRemaining(player, item.getId(), mechanic);
    }

    /**
     * Remaining cooldown in seconds for an arbitrary player/item/mechanic combination
     * (e.g. from PlaceholderAPI), 0 when inactive or unknown.
     */
    public static long cooldownRemaining(Player player, String itemId, String mechanic) {
        if (player == null) {
            return 0;
        }
        String key = key(player, itemId, mechanic);
        Long end = COOLDOWNS.get(key);
        if (end == null) {
            return 0;
        }
        long remaining = (end - System.currentTimeMillis()) / 1000;
        if (remaining <= 0) {
            COOLDOWNS.remove(key);
            return 0;
        }
        return remaining;
    }

    public boolean hasCooldown(String mechanic) {
        return cooldownRemaining(mechanic) > 0;
    }

    /** Remove one item from the player's hand, updating their inventory. */
    public boolean consumeItem() {
        if (stack == null || stack.getAmount() <= 0) {
            return false;
        }
        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
        } else {
            stack.setAmount(0);
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        }
        return true;
    }

    private static String key(Player player, String itemId, String mechanic) {
        return player.getUniqueId() + "|" + itemId + "|" + mechanic;
    }
}
