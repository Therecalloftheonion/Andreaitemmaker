package com.andreaitemmaker.api;

import java.util.Collection;

/** Registry of available {@link ItemMechanic}s, keyed by their id. */
public interface MechanicRegistry {

    /** Register a mechanic. Replaces any existing mechanic with the same id. */
    void register(ItemMechanic mechanic);

    /** Remove a mechanic by id. */
    void unregister(String id);

    /** Get a mechanic by id, or null. */
    ItemMechanic get(String id);

    /** All registered mechanics. */
    Collection<ItemMechanic> getAll();
}
