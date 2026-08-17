package com.andreaitemmaker.listener;

import com.andreaitemmaker.AndreaitemmakerPlugin;
import com.andreaitemmaker.api.CustomFurniture;
import com.andreaitemmaker.api.event.CustomFurnitureBreakEvent;
import com.andreaitemmaker.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Pick-up and damage handling for furniture (armor stand markers). */
public final class FurnitureListener implements Listener {

    private final AndreaitemmakerPlugin plugin;

    public FurnitureListener(AndreaitemmakerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) {
            return;
        }
        CustomFurniture furniture = furnitureOf(stand);
        if (furniture == null) {
            return;
        }
        event.setCancelled(true);
        breakFurniture(event.getPlayer(), stand, furniture);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) {
            return;
        }
        CustomFurniture furniture = furnitureOf(stand);
        if (furniture == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player) {
            breakFurniture(player, stand, furniture);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) {
            return;
        }
        CustomFurniture furniture = furnitureOf(stand);
        if (furniture == null) {
            return;
        }
        event.setCancelled(true);
        // Non-entity damage (explosions, fire, ...) breaks the piece without a player.
        if (!(event instanceof EntityDamageByEntityEvent)) {
            breakFurniture(null, stand, furniture);
        }
    }

    private CustomFurniture furnitureOf(ArmorStand stand) {
        String id = stand.getPersistentDataContainer().get(Keys.FURNITURE_ID, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        return plugin.getContentRegistry().getFurniture(id);
    }

    private void breakFurniture(Player player, ArmorStand stand, CustomFurniture furniture) {
        CustomFurnitureBreakEvent breakEvent = new CustomFurnitureBreakEvent(player, furniture, stand);
        Bukkit.getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) {
            return;
        }
        Location location = stand.getLocation();
        Sound breakSound = furniture.getBreakSound() != null ? furniture.getBreakSound() : Sound.BLOCK_WOOD_BREAK;
        stand.getWorld().playSound(location, breakSound, 1f, 1f);
        if (furniture.dropsItem() && player == null) {
            stand.getWorld().dropItemNaturally(location, plugin.getItemFactory().build(furniture, 1));
        } else if (furniture.dropsItem() && player != null && player.getGameMode() != GameMode.CREATIVE) {
            ItemStack item = plugin.getItemFactory().build(furniture, 1);
            if (player.getInventory().firstEmpty() >= 0) {
                player.getInventory().addItem(item);
            } else {
                stand.getWorld().dropItemNaturally(location, item);
            }
        }
        stand.remove();
    }
}
