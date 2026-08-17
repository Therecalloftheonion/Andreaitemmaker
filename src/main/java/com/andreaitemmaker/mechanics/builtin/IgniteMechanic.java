package com.andreaitemmaker.mechanics.builtin;

import com.andreaitemmaker.api.ItemMechanic;
import com.andreaitemmaker.api.MechanicContext;
import com.andreaitemmaker.mechanics.Cfg;
import org.bukkit.entity.Entity;

import java.util.Map;

/** Set the hit entity on fire. Config: {@code seconds} (default 3). */
public final class IgniteMechanic implements ItemMechanic {

    @Override
    public String getId() {
        return "ignite";
    }

    @Override
    public void onHitEntity(MechanicContext context, Entity target) {
        Map<String, Object> cfg = context.getItem().getMechanics().get(getId());
        int seconds = Cfg.i(cfg, "seconds", 3);
        target.setFireTicks(Math.max(target.getFireTicks(), seconds * 20));
    }
}
