package com.andreaitemmaker.mechanics.builtin;

import com.andreaitemmaker.api.ItemMechanic;
import com.andreaitemmaker.api.MechanicContext;
import com.andreaitemmaker.mechanics.Cfg;
import com.andreaitemmaker.util.Chat;
import org.bukkit.entity.Player;

import java.util.Map;

/** Right-click to restore {@code amount} hunger points. Config: {@code amount}, {@code saturation}, {@code cooldown}. */
public final class FeedMechanic implements ItemMechanic {

    @Override
    public String getId() {
        return "feed";
    }

    @Override
    public boolean onUse(MechanicContext context) {
        Map<String, Object> cfg = context.getItem().getMechanics().get(getId());
        int amount = Cfg.i(cfg, "amount", 4);
        float saturation = (float) Cfg.d(cfg, "saturation", 0);
        int cooldown = Cfg.i(cfg, "cooldown", 5);
        if (!context.tryCooldown(getId(), cooldown)) {
            return false;
        }
        Player player = context.getPlayer();
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + amount));
        player.setSaturation(Math.min(20f, player.getSaturation() + saturation));
        player.sendMessage(Chat.color("&aYou feel full (+" + amount + " hunger)."));
        return true;
    }
}
