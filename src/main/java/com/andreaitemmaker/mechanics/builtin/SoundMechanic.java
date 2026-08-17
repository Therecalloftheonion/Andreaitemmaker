package com.andreaitemmaker.mechanics.builtin;

import com.andreaitemmaker.api.ItemMechanic;
import com.andreaitemmaker.api.MechanicContext;
import com.andreaitemmaker.mechanics.Cfg;
import org.bukkit.Sound;

import java.util.Map;

/** Play a sound on use. Config: {@code sound}, {@code volume}, {@code pitch}. */
public final class SoundMechanic implements ItemMechanic {

    @Override
    public String getId() {
        return "sound";
    }

    @Override
    public boolean onUse(MechanicContext context) {
        Map<String, Object> cfg = context.getItem().getMechanics().get(getId());
        String name = Cfg.s(cfg, "sound", null);
        if (name == null) {
            return false;
        }
        Sound sound;
        try {
            sound = Sound.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return false;
        }
        float volume = (float) Cfg.d(cfg, "volume", 1.0);
        float pitch = (float) Cfg.d(cfg, "pitch", 1.0);
        context.getPlayer().playSound(context.getPlayer().getLocation(), sound, volume, pitch);
        return true;
    }
}
