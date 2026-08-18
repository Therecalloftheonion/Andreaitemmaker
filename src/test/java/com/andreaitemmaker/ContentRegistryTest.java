package com.andreaitemmaker;

import com.andreaitemmaker.api.CustomBlock;
import com.andreaitemmaker.api.CustomFurniture;
import com.andreaitemmaker.api.CustomItem;
import com.andreaitemmaker.api.CustomItemType;
import com.andreaitemmaker.content.ContentRegistry;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentRegistryTest {

    private static CustomBlock block(String id, Material base) {
        return new CustomBlock(id, Material.STICK, id, List.of(), 1000, 1,
                Map.of(), Map.of(), false, false, null, null, Map.of(), base, true);
    }

    private static CustomItem item(String id) {
        return new CustomItem(id, CustomItemType.ITEM, Material.STICK, id, List.of(), 1000, 1,
                Map.of(), Map.of(), false, false, null, null, Map.of());
    }

    private static CustomFurniture furniture(String id) {
        return new CustomFurniture(id, Material.STICK, id, List.of(), 1000, 1,
                Map.of(), Map.of(), false, false, null, null, Map.of(),
                false, true, true, 0, null, null);
    }

    @Test
    void replacementRemovesStaleBaseMapping() {
        ContentRegistry registry = new ContentRegistry();
        registry.add(block("a", Material.STONE));
        assertSame(registry.getBlock("a"), registry.getBlockByBase(Material.STONE));

        // Replacing the block with a plain item must drop the STONE -> a mapping.
        registry.add(item("a"));
        assertNull(registry.getBlockByBase(Material.STONE));
        assertEquals(item("a").getType(), registry.getItem("a").getType());

        // Replacing with a block on a different base must not keep the old base.
        registry.add(block("a", Material.DIRT));
        assertNull(registry.getBlockByBase(Material.STONE));
        assertSame(registry.getBlock("a"), registry.getBlockByBase(Material.DIRT));
    }

    @Test
    void removalDropsAllIndexes() {
        ContentRegistry registry = new ContentRegistry();
        registry.add(block("a", Material.STONE));
        registry.add(item("b"));
        registry.add(furniture("c"));

        registry.remove("a");
        assertNull(registry.getItem("a"));
        assertNull(registry.getBlockByBase(Material.STONE));

        registry.remove("b");
        assertNull(registry.getItem("b"));

        registry.remove("c");
        assertNull(registry.getFurniture("c"));
        assertTrue(registry.getAll().isEmpty());
    }

    @Test
    void twoBlocksCannotShareABase() {
        ContentRegistry registry = new ContentRegistry();
        registry.add(block("a", Material.STONE));
        assertThrows(IllegalStateException.class, () -> registry.add(block("b", Material.STONE)));
        // The conflicting block must not have been registered.
        assertNull(registry.getItem("b"));
    }

    @Test
    void reloadSameBaseSameIdReplacesCleanly() {
        ContentRegistry registry = new ContentRegistry();
        registry.add(block("a", Material.STONE));
        // A reload builds a fresh registry with new instances; the old one is untouched.
        ContentRegistry fresh = ContentRegistry.build(List.of(block("a", Material.STONE), item("b")));
        assertSame(fresh.getBlock("a"), fresh.getBlockByBase(Material.STONE));
        assertNotNull(fresh.getItem("b"));
        // Old registry still sees its own (old) state.
        assertNotNull(registry.getBlockByBase(Material.STONE));
        assertNull(registry.getItem("b"));
    }

    @Test
    void collectionsAreImmutable() {
        ContentRegistry registry = new ContentRegistry();
        registry.add(item("a"));
        registry.add(block("b", Material.STONE));
        registry.add(furniture("c"));

        assertThrows(UnsupportedOperationException.class,
                () -> registry.getAll().add(item("x")));
        assertThrows(UnsupportedOperationException.class,
                () -> registry.getItems().add(item("x")));
        assertThrows(UnsupportedOperationException.class,
                () -> registry.getBlocks().add(block("x", Material.DIRT)));
        assertThrows(UnsupportedOperationException.class,
                () -> registry.getFurnitures().add(furniture("x")));
        assertThrows(UnsupportedOperationException.class,
                () -> registry.getWeapons().add(item("x")));
    }

    @Test
    void buildRejectsConflictingBases() {
        assertThrows(IllegalStateException.class,
                () -> ContentRegistry.build(List.of(block("a", Material.STONE), block("b", Material.STONE))));
    }

    @Test
    void lookupByType() {
        ContentRegistry registry = ContentRegistry.build(List.of(
                item("i"),
                new CustomItem("w", CustomItemType.WEAPON, Material.STICK, "w", List.of(), 1000, 1,
                        Map.of(), Map.of(), false, false, null, null, Map.of()),
                new CustomItem("a", CustomItemType.ARMOR, Material.STICK, "a", List.of(), 1000, 1,
                        Map.of(), Map.of(), false, false, null, null, Map.of()),
                block("b", Material.STONE)));
        assertEquals(1, registry.getItems().size());
        assertEquals(1, registry.getWeapons().size());
        assertEquals(1, registry.getArmor().size());
        assertEquals(1, registry.getBlocks().size());
        assertEquals(4, registry.getAll().size());
    }
}
