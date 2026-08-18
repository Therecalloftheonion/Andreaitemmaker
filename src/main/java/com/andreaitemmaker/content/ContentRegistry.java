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
import java.util.Objects;

/**
 * Registry of all loaded custom content.
 *
 * <p>{@code byId} is the single source of truth; every other index is derived from it and
 * kept in sync on {@link #add}/{@link #remove}. Replacing or removing an entry always
 * removes every stale index that belonged to the old object (e.g. the base-block mapping
 * of a custom block that got redefined as a plain item).
 */
public final class ContentRegistry {

    private final Map<String, CustomItem> byId = new LinkedHashMap<>();
    private final Map<Material, CustomBlock> blocksByBase = new LinkedHashMap<>();

    /** Build a fully-populated registry from a load result. Throws on conflicting bases. */
    public static ContentRegistry build(Collection<CustomItem> items) {
        ContentRegistry registry = new ContentRegistry();
        for (CustomItem item : items) {
            registry.add(item);
        }
        return registry;
    }

    public void clear() {
        byId.clear();
        blocksByBase.clear();
    }

    /**
     * Register content, replacing anything with the same id. Stale indexes of the replaced
     * entry are removed first, and two different custom blocks are never allowed to share a
     * base block (they would be indistinguishable in the world).
     *
     * @throws IllegalStateException when a different custom block already uses the same base block
     */
    public void add(CustomItem item) {
        Objects.requireNonNull(item, "item");
        CustomItem previous = byId.get(item.getId());
        if (previous != null && previous != item) {
            removeIndexes(previous);
        }
        if (item instanceof CustomBlock block) {
            CustomBlock existing = blocksByBase.get(block.getBaseBlock());
            if (existing != null && existing != item && !existing.getId().equals(item.getId())) {
                throw new IllegalStateException("Custom block '" + item.getId() + "' conflicts with '"
                        + existing.getId() + "': both use base block " + block.getBaseBlock()
                        + " (each base block can back only one custom block)");
            }
            blocksByBase.put(block.getBaseBlock(), block);
        }
        byId.put(item.getId(), item);
    }

    /** Remove content by id, including every index pointing at it. */
    public void remove(String id) {
        CustomItem item = byId.remove(id);
        if (item != null) {
            removeIndexes(item);
        }
    }

    private void removeIndexes(CustomItem item) {
        if (item instanceof CustomBlock block) {
            // Only drop the mapping when it still points at this exact instance, so a newer
            // block with the same base (reload) is never removed by the stale cleanup.
            blocksByBase.remove(block.getBaseBlock(), block);
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
        return Collections.unmodifiableList(out);
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
        return Collections.unmodifiableList(out);
    }

    private List<CustomItem> filter(CustomItemType type) {
        List<CustomItem> out = new ArrayList<>();
        for (CustomItem item : byId.values()) {
            if (item.getType() == type) {
                out.add(item);
            }
        }
        return Collections.unmodifiableList(out);
    }
}
