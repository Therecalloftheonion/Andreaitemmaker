package com.andreaitemmaker.util;

import org.bukkit.NamespacedKey;

/** PersistentDataContainer keys used to tag custom items and furniture entities. */
public final class Keys {

    private Keys() {
    }

    /** Tag on custom item stacks: the content id. */
    public static final NamespacedKey ITEM_ID = new NamespacedKey("andreaitemmaker", "id");

    /** Tag on custom item stacks: the {@code CustomItemType} name. */
    public static final NamespacedKey ITEM_TYPE = new NamespacedKey("andreaitemmaker", "type");

    /** Tag on furniture armor stands: the furniture content id. */
    public static final NamespacedKey FURNITURE_ID = new NamespacedKey("andreaitemmaker", "furniture");
}
