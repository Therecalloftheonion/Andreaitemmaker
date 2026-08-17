package com.andreaitemmaker.api;

/** The kind of custom content an entry represents. */
public enum CustomItemType {
    ITEM,
    WEAPON,
    ARMOR,
    FOOD,
    BLOCK,
    FURNITURE;

    public boolean isArmor() {
        return this == ARMOR;
    }

    public boolean isBlock() {
        return this == BLOCK;
    }

    public boolean isFurniture() {
        return this == FURNITURE;
    }
}
