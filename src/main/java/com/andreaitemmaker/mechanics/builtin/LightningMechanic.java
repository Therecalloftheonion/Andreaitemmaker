package com.andreaitemmaker.mechanics.builtin;

import com.andreaitemmaker.api.ItemMechanic;
import com.andreaitemmaker.api.MechanicContext;
import org.bukkit.entity.Entity;

/** Strike the hit entity with visual lightning (no damage by default; add {@code damage} to hurt). */
public final class LightningMechanic implements ItemMechanic {

    @Override
    public String getId() {
        return "lightning";
    }

    @Override
    public void onHitEntity(MechanicContext context, Entity target) {
        var cfg = context.getItem().getMechanics().get(getId());
        double damage = cfg == null ? 0 : ((Number) cfg.getOrDefault("damage", 0)).doubleValue();
        target.getWorld().strikeLightningEffect(target.getLocation());
        if (damage > 0 && target instanceof org.bukkit.entity.Damageable damageable) {
            damageable.damage(damage, context.getPlayer());
        }
    }
}
