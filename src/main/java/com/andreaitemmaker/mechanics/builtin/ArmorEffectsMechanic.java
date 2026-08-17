package com.andreaitemmaker.mechanics.builtin;

import com.andreaitemmaker.api.CustomItem;
import com.andreaitemmaker.api.ItemMechanic;
import com.andreaitemmaker.mechanics.Cfg;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;

/**
 * While the armor piece is worn, keep the configured potion effects active.
 * Same effect config as {@code effect}, plus {@code duration} defaults to 5 seconds
 * (longer than the tick interval so effects never lapse).
 */
public final class ArmorEffectsMechanic implements ItemMechanic {

    @Override
    public String getId() {
        return "armor-effects";
    }

    @Override
    public void onWornTick(Player player, ItemStack stack, CustomItem item) {
        Map<String, Object> cfg = item.getMechanics().get(getId());
        if (cfg == null) {
            return;
        }
        for (Map<String, Object> effect : Cfg.listOfMaps(cfg, "effects")) {
            PotionEffectType type = EffectMechanic.parseType(Cfg.s(effect, "type", null));
            if (type == null) {
                continue;
            }
            int duration = Cfg.i(effect, "duration", 5) * 20;
            int amplifier = Math.max(0, Cfg.i(effect, "amplifier", 0));
            boolean ambient = Cfg.b(effect, "ambient", true);
            boolean particles = Cfg.b(effect, "particles", false);
            player.addPotionEffect(new PotionEffect(type, duration, amplifier, ambient, particles, true));
        }
    }
}
