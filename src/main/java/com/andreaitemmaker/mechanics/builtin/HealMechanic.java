package com.andreaitemmaker.mechanics.builtin;

import com.andreaitemmaker.api.ItemMechanic;
import com.andreaitemmaker.api.MechanicContext;
import com.andreaitemmaker.mechanics.Cfg;
import com.andreaitemmaker.util.Chat;
import org.bukkit.entity.Player;

import java.util.Map;

/** Right-click to restore {@code amount} hearts. Config: {@code amount}, {@code cooldown}. */
public final class HealMechanic implements ItemMechanic {

    @Override
    public String getId() {
        return "heal";
    }

    @Override
    public boolean onUse(MechanicContext context) {
        Map<String, Object> cfg = context.getItem().getMechanics().get(getId());
        int amount = Cfg.i(cfg, "amount", 4);
        int cooldown = Cfg.i(cfg, "cooldown", 5);
        if (!context.tryCooldown(getId(), cooldown)) {
            context.getPlayer().sendMessage(Chat.color("&cThis item is on cooldown ("
                    + context.cooldownRemaining(getId()) + "s)."));
            return false;
        }
        Player player = context.getPlayer();
        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + amount));
        player.sendMessage(Chat.color("&aYou feel healed (+" + amount + " HP)."));
        return true;
    }
}
