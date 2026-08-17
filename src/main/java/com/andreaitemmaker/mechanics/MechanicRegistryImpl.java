package com.andreaitemmaker.mechanics;

import com.andreaitemmaker.api.ItemMechanic;
import com.andreaitemmaker.api.MechanicRegistry;
import com.andreaitemmaker.mechanics.builtin.ArmorEffectsMechanic;
import com.andreaitemmaker.mechanics.builtin.EffectMechanic;
import com.andreaitemmaker.mechanics.builtin.FeedMechanic;
import com.andreaitemmaker.mechanics.builtin.HealMechanic;
import com.andreaitemmaker.mechanics.builtin.IgniteMechanic;
import com.andreaitemmaker.mechanics.builtin.KnockbackMechanic;
import com.andreaitemmaker.mechanics.builtin.LaunchMechanic;
import com.andreaitemmaker.mechanics.builtin.LightningMechanic;
import com.andreaitemmaker.mechanics.builtin.SoundMechanic;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Default {@link MechanicRegistry} with the built-in mechanics pre-registered. */
public final class MechanicRegistryImpl implements MechanicRegistry {

    private final Map<String, ItemMechanic> mechanics = new ConcurrentHashMap<>();

    public MechanicRegistryImpl() {
        register(new HealMechanic());
        register(new FeedMechanic());
        register(new EffectMechanic());
        register(new LaunchMechanic());
        register(new LightningMechanic());
        register(new IgniteMechanic());
        register(new KnockbackMechanic());
        register(new ArmorEffectsMechanic());
        register(new SoundMechanic());
    }

    @Override
    public void register(ItemMechanic mechanic) {
        mechanics.put(mechanic.getId(), mechanic);
    }

    @Override
    public void unregister(String id) {
        mechanics.remove(id);
    }

    @Override
    public ItemMechanic get(String id) {
        return mechanics.get(id);
    }

    @Override
    public Collection<ItemMechanic> getAll() {
        return Collections.unmodifiableCollection(mechanics.values());
    }
}
