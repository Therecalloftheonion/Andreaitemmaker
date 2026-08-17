package com.andreaitemmaker.mechanics.builtin;

import com.andreaitemmaker.api.ItemMechanic;
import com.andreaitemmaker.api.MechanicContext;
import com.andreaitemmaker.mechanics.Cfg;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;

/**
 * Right-click to apply one or more potion effects.
 * Config:
 * <pre>{@code
 * effects:
 *   - type: SPEED
 *     duration: 30      # seconds
 *     amplifier: 1
 *     ambient: true
 *     particles: false
 * cooldown: 10
 * }</pre>
 */
public final class EffectMechanic implements ItemMechanic {

    @Override
    public String getId() {
        return "effect";
    }

    @Override
    public boolean onUse(MechanicContext context) {
        Map<String, Object> cfg = context.getItem().getMechanics().get(getId());
        int cooldown = Cfg.i(cfg, "cooldown", 10);
        if (!context.tryCooldown(getId(), cooldown)) {
            return false;
        }
        boolean any = false;
        for (Map<String, Object> effect : Cfg.listOfMaps(cfg, "effects")) {
            PotionEffectType type = parseType(Cfg.s(effect, "type", null));
            if (type == null) {
                continue;
            }
            int duration = Cfg.i(effect, "duration", 30) * 20;
            int amplifier = Math.max(0, Cfg.i(effect, "amplifier", 0));
            boolean ambient = Cfg.b(effect, "ambient", true);
            boolean particles = Cfg.b(effect, "particles", false);
            context.getPlayer().addPotionEffect(
                    new PotionEffect(type, duration, amplifier, ambient, particles, true));
            any = true;
        }
        return any;
    }

    static PotionEffectType parseType(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(name.toLowerCase()));
            if (type != null) {
                return type;
            }
        } catch (IllegalArgumentException ignored) {
        }
        try {
            return PotionEffectType.getByName(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
