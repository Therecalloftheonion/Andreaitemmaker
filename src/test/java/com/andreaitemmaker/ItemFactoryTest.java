package com.andreaitemmaker;

import com.andreaitemmaker.content.ItemFactory;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the native 3D helmet decision (client-side carved-pumpkin mechanism):
 * only a HEAD-slot armor item whose 3D model file actually exists switches to the
 * native 3D worn path (equippable with no model). Everything else keeps the 2D
 * equipment-asset path.
 */
class ItemFactoryTest {

    @Test
    void helmetWithRealModelUsesNative3dHead() {
        assertTrue(ItemFactory.useNative3dHead(EquipmentSlot.HEAD, "assets/models/helmet.json", true));
    }

    @Test
    void helmetWithoutModelFileKeeps2dPath() {
        // No model declared -> generated flat fallback -> 2D equipment asset.
        assertFalse(ItemFactory.useNative3dHead(EquipmentSlot.HEAD, null, true));
    }

    @Test
    void helmetWithMissingModelFileKeeps2dPath() {
        // Model declared but the file is gone -> pack falls back to a generated
        // model, so the 3D path must not be taken (a flat sprite on the head).
        assertFalse(ItemFactory.useNative3dHead(EquipmentSlot.HEAD, "assets/models/helmet.json", false));
    }

    @Test
    void bodySlotsAlwaysKeep2dPath() {
        // The client has no native 3D worn rendering for body slots.
        assertFalse(ItemFactory.useNative3dHead(EquipmentSlot.CHEST, "assets/models/chest.json", true));
        assertFalse(ItemFactory.useNative3dHead(EquipmentSlot.LEGS, "assets/models/legs.json", true));
        assertFalse(ItemFactory.useNative3dHead(EquipmentSlot.FEET, "assets/models/boots.json", true));
    }

    @Test
    void nonArmorSlotIsNotTreatedAsHead() {
        assertFalse(ItemFactory.useNative3dHead(EquipmentSlot.HAND, "assets/models/x.json", true));
    }
}
