package com.andreaitemmaker.content;

import com.andreaitemmaker.api.CustomBlock;
import com.andreaitemmaker.api.CustomFurniture;
import com.andreaitemmaker.api.CustomItem;
import com.andreaitemmaker.api.CustomItemType;
import com.andreaitemmaker.util.Keys;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry of all loaded custom content. */
public final class ContentRegistry {

    private final Map<String, CustomItem> byId = new LinkedHashMap<>();
    private final Map<Material, CustomBlock> blocksByBase = new LinkedHashMap<>();

    public void clear() {
        byId.clear();
        blocksByBase.clear();
    }

    /** Register content (replaces anything with the same id). */
    public void add(CustomItem item) {
        byId.put(item.getId(), item);
        if (item instanceof CustomBlock block) {
            blocksByBase.put(block.getBaseBlock(), block);
        }
    }

    /** All content, in load order. */
    public Collection<CustomItem> getAll() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public CustomItem getItem(String id) {
        return byId.get(id);
    }

    /** Resolve a stack's custom item by its persistent data tag, or null. */
    public CustomItem getItemByStack(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || stack.getItemMeta() == null) {
            return null;
        }
        String id = stack.getItemMeta().getPersistentDataContainer()
                .get(Keys.ITEM_ID, PersistentDataType.STRING);
        return id == null ? null : byId.get(id);
    }

    public List<CustomItem> getItems() {
        return filter(CustomItemType.ITEM);
    }

    public List<CustomItem> getWeapons() {
        return filter(CustomItemType.WEAPON);
    }

    public List<CustomItem> getArmor() {
        return filter(CustomItemType.ARMOR);
    }

    public List<CustomItem> getFood() {
        return filter(CustomItemType.FOOD);
    }

    public CustomBlock getBlock(String id) {
        CustomItem item = byId.get(id);
        return item instanceof CustomBlock block ? block : null;
    }

    /** The custom block placed as the given vanilla base block, or null. */
    public CustomBlock getBlockByBase(Material base) {
        return blocksByBase.get(base);
    }

    public List<CustomBlock> getBlocks() {
        List<CustomBlock> out = new ArrayList<>();
        for (CustomItem item : byId.values()) {
            if (item instanceof CustomBlock block) {
                out.add(block);
            }
        }
        return out;
    }

    public CustomFurniture getFurniture(String id) {
        CustomItem item = byId.get(id);
        return item instanceof CustomFurniture furniture ? furniture : null;
    }

    public List<CustomFurniture> getFurnitures() {
        List<CustomFurniture> out = new ArrayList<>();
        for (CustomItem item : byId.values()) {
            if (item instanceof CustomFurniture furniture) {
                out.add(furniture);
            }
        }
        return out;
    }

    private List<CustomItem> filter(CustomItemType type) {
        List<CustomItem> out = new ArrayList<>();
        for (CustomItem item : byId.values()) {
            if (item.getType() == type) {
                out.add(item);
            }
        }
        return out;
    }
}
