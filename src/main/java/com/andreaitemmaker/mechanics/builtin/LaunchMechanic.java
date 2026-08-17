package com.andreaitemmaker.mechanics.builtin;

import com.andreaitemmaker.api.ItemMechanic;
import com.andreaitemmaker.api.MechanicContext;
import com.andreaitemmaker.mechanics.Cfg;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;

/** Right-click to launch the player upward. Config: {@code power}. */
public final class LaunchMechanic implements ItemMechanic {

    @Override
    public String getId() {
        return "launch";
    }

    @Override
    public boolean onUse(MechanicContext context) {
        Map<String, Object> cfg = context.getItem().getMechanics().get(getId());
        double power = Cfg.d(cfg, "power", 1.0);
        int cooldown = Cfg.i(cfg, "cooldown", 10);
        if (!context.tryCooldown(getId(), cooldown)) {
            return false;
        }
        Player player = context.getPlayer();
        player.setVelocity(player.getVelocity().add(new Vector(0, power, 0)));
        player.setFallDistance(0);
        return true;
    }
}
