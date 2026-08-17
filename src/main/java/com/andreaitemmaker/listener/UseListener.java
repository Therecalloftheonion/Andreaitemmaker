package com.andreaitemmaker.listener;

import com.andreaitemmaker.AndreaitemmakerPlugin;
import com.andreaitemmaker.api.CustomBlock;
import com.andreaitemmaker.api.CustomFood;
import com.andreaitemmaker.api.CustomFurniture;
import com.andreaitemmaker.api.CustomItem;
import com.andreaitemmaker.api.event.CustomBlockPlaceEvent;
import com.andreaitemmaker.api.event.CustomFurniturePlaceEvent;
import com.andreaitemmaker.api.event.CustomItemConsumeEvent;
import com.andreaitemmaker.api.event.CustomItemHitEvent;
import com.andreaitemmaker.api.event.CustomItemUseEvent;
import com.andreaitemmaker.util.Chat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Custom item interaction: use, eat, hit, place blocks and furniture. */
public final class UseListener implements Listener {

    private final AndreaitemmakerPlugin plugin;

    public UseListener(AndreaitemmakerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        ItemStack stack = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        CustomItem item = plugin.getContentRegistry().getItemByStack(stack);
        if (item == null) {
            return;
        }

        if (item instanceof CustomBlock block) {
            placeBlock(event, player, hand, stack, block);
            return;
        }
        if (item instanceof CustomFurniture furniture) {
            placeFurniture(event, player, hand, stack, furniture);
            return;
        }

        CustomItemUseEvent useEvent = new CustomItemUseEvent(player, item, hand, event.getAction());
        Bukkit.getPluginManager().callEvent(useEvent);
        if (useEvent.isCancelled()) {
            event.setCancelled(true);
            return;
        }
        if (item instanceof CustomFood food) {
            eat(event, player, hand, stack, food);
            plugin.runUseMechanics(player, item, stack, hand, event);
            return;
        }
        if (!item.getMechanics().isEmpty()) {
            // Suppress vanilla behavior (e.g. eating a custom apple base) and run mechanics.
            event.setCancelled(true);
        }
        plugin.runUseMechanics(player, item, stack, hand, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        ItemStack stack = player.getInventory().getItemInMainHand();
        CustomItem item = plugin.getContentRegistry().getItemByStack(stack);
        if (item == null) {
            return;
        }
        CustomItemHitEvent hitEvent = new CustomItemHitEvent(player, item, event.getEntity(), event.getDamage());
        Bukkit.getPluginManager().callEvent(hitEvent);
        if (hitEvent.isCancelled()) {
            event.setCancelled(true);
            return;
        }
        event.setDamage(hitEvent.getDamage());
        plugin.runHitMechanics(player, item, stack, event.getEntity());
    }

    // ---- food ----

    private void eat(PlayerInteractEvent event, Player player, EquipmentSlot hand, ItemStack stack, CustomFood food) {
        CustomItemConsumeEvent consumeEvent = new CustomItemConsumeEvent(player, food,
                food.getHunger(), food.getSaturation());
        Bukkit.getPluginManager().callEvent(consumeEvent);
        if (consumeEvent.isCancelled()) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + consumeEvent.getHunger()));
        player.setSaturation(Math.min(20f, player.getSaturation() + consumeEvent.getSaturation()));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1f, 1f);
        player.getWorld().spawnParticle(Particle.HEART,
                player.getLocation().add(0, 1.2, 0), 6, 0.3, 0.3, 0.3);
        if (player.getGameMode() != GameMode.CREATIVE) {
            consume(player, hand, stack);
        }
    }

    // ---- block placement ----

    private void placeBlock(PlayerInteractEvent event, Player player, EquipmentSlot hand,
                            ItemStack stack, CustomBlock block) {
        event.setCancelled(true);
        Block target = findPlaceTarget(event);
        if (target == null) {
            return;
        }
        if (player.getBoundingBox().overlaps(target.getBoundingBox())) {
            player.sendMessage(Chat.color("&cYou can't place that here."));
            return;
        }
        CustomBlockPlaceEvent placeEvent = new CustomBlockPlaceEvent(player, block, target, block.getBaseBlock());
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) {
            return;
        }
        target.setType(block.getBaseBlock(), false);
        player.playSound(target.getLocation(), Sound.BLOCK_STONE_PLACE, 1f, 1f);
        if (player.getGameMode() != GameMode.CREATIVE) {
            consume(player, hand, stack);
        }
    }

    // ---- furniture placement ----

    private void placeFurniture(PlayerInteractEvent event, Player player, EquipmentSlot hand,
                                ItemStack stack, CustomFurniture furniture) {
        event.setCancelled(true);
        Block target = findPlaceTarget(event);
        if (target == null) {
            return;
        }
        Location location = target.getLocation().add(0.5, furniture.getOffsetY(), 0.5);
        location.setYaw(player.getLocation().getYaw() + 180);
        CustomFurniturePlaceEvent placeEvent = new CustomFurniturePlaceEvent(player, furniture, location);
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) {
            return;
        }
        ArmorStand stand = player.getWorld().spawn(location, ArmorStand.class);
        stand.setVisible(false);
        stand.setMarker(true);
        stand.setSmall(furniture.isSmall());
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        // Prevent players from equipping items onto or removing items from the stand.
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            stand.addEquipmentLock(slot, ArmorStand.LockType.ADDING_OR_CHANGING);
            stand.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
        }
        stand.getEquipment().setItemInMainHand(plugin.getItemFactory().build(furniture, 1));
        stand.getPersistentDataContainer().set(com.andreaitemmaker.util.Keys.FURNITURE_ID,
                org.bukkit.persistence.PersistentDataType.STRING, furniture.getId());
        Sound placeSound = furniture.getPlaceSound() != null ? furniture.getPlaceSound() : Sound.BLOCK_WOOD_PLACE;
        player.playSound(location, placeSound, 1f, 1f);
        if (furniture.isConsumable() && player.getGameMode() != GameMode.CREATIVE) {
            consume(player, hand, stack);
        }
    }

    // ---- helpers ----

    private Block findPlaceTarget(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return null;
        }
        // Don't hijack interactions with chests, buttons, etc. unless sneaking.
        if (clicked.getType().isInteractable() && !event.getPlayer().isSneaking()) {
            return null;
        }
        Block target = isReplaceable(clicked) ? clicked : clicked.getRelative(event.getBlockFace());
        return isReplaceable(target) ? target : null;
    }

    private static boolean isReplaceable(Block block) {
        Material material = block.getType();
        return material.isAir() || (!material.isOccluding() && !material.isSolid());
    }

    private void consume(Player player, EquipmentSlot hand, ItemStack stack) {
        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
        } else if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(null);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }
}
