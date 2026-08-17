package com.andreaitemmaker.mechanics.builtin;

import com.andreaitemmaker.api.ItemMechanic;
import com.andreaitemmaker.api.MechanicContext;
import com.andreaitemmaker.mechanics.Cfg;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.Map;

/** Knock the hit entity away from the player. Config: {@code power} (default 1.5). */
public final class KnockbackMechanic implements ItemMechanic {

    @Override
    public String getId() {
        return "knockback";
    }

    @Override
    public void onHitEntity(MechanicContext context, Entity target) {
        Map<String, Object> cfg = context.getItem().getMechanics().get(getId());
        double power = Cfg.d(cfg, "power", 1.5);
        Vector direction = target.getLocation().toVector()
                .subtract(context.getPlayer().getLocation().toVector())
                .setY(0)
                .normalize()
                .multiply(power);
        target.setVelocity(target.getVelocity().add(direction).setY(0.4 * power));
    }
}
