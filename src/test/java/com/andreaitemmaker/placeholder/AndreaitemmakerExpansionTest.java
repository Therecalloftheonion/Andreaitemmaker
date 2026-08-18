package com.andreaitemmaker.placeholder;

import com.andreaitemmaker.placeholder.AndreaitemmakerExpansion.Action;
import com.andreaitemmaker.placeholder.AndreaitemmakerExpansion.Parsed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AndreaitemmakerExpansionTest {

    @Test
    void parsesCountPlaceholders() {
        assertEquals(Action.CONTENT_COUNT, AndreaitemmakerExpansion.parse("content_count").action());
        assertEquals(Action.ITEM_COUNT, AndreaitemmakerExpansion.parse("item_count").action());
        assertEquals(Action.WEAPON_COUNT, AndreaitemmakerExpansion.parse("weapon_count").action());
        assertEquals(Action.ARMOR_COUNT, AndreaitemmakerExpansion.parse("armor_count").action());
        assertEquals(Action.FOOD_COUNT, AndreaitemmakerExpansion.parse("food_count").action());
        assertEquals(Action.BLOCK_COUNT, AndreaitemmakerExpansion.parse("block_count").action());
        assertEquals(Action.FURNITURE_COUNT, AndreaitemmakerExpansion.parse("furniture_count").action());
    }

    @Test
    void parsesItemPlaceholders() {
        Parsed has = AndreaitemmakerExpansion.parse("has_item_storm_blade");
        assertEquals(Action.HAS_ITEM, has.action());
        assertEquals("storm_blade", has.id());

        Parsed amount = AndreaitemmakerExpansion.parse("amount_spadafulmini");
        assertEquals(Action.AMOUNT, amount.action());
        assertEquals("spadafulmini", amount.id());

        // Item ids may contain underscores.
        Parsed multi = AndreaitemmakerExpansion.parse("amount_my_epic_sword");
        assertEquals("my_epic_sword", multi.id());

        Parsed holding = AndreaitemmakerExpansion.parse("holding_storm_blade");
        assertEquals(Action.HOLDING, holding.action());
        assertEquals("storm_blade", holding.id());
    }

    @Test
    void parsesCooldownWithTrailingMechanic() {
        Parsed cooldown = AndreaitemmakerExpansion.parse("cooldown_storm_blade_lightning");
        assertEquals(Action.COOLDOWN, cooldown.action());
        assertEquals("storm_blade", cooldown.id());
        assertEquals("lightning", cooldown.mechanic());

        // The mechanic is always the trailing part, even when the id has underscores.
        Parsed multi = AndreaitemmakerExpansion.parse("cooldown_my_item_armor-effects");
        assertEquals("my_item", multi.id());
        assertEquals("armor-effects", multi.mechanic());
    }

    @Test
    void rejectsUnknownOrMalformedParams() {
        assertNull(AndreaitemmakerExpansion.parse(null));
        assertNull(AndreaitemmakerExpansion.parse(""));
        assertNull(AndreaitemmakerExpansion.parse("bogus"));
        assertNull(AndreaitemmakerExpansion.parse("has_item_"));
        assertNull(AndreaitemmakerExpansion.parse("amount_"));
        assertNull(AndreaitemmakerExpansion.parse("holding_"));
        assertNull(AndreaitemmakerExpansion.parse("cooldown_"));
        assertNull(AndreaitemmakerExpansion.parse("cooldown_onlyid"));
        assertNull(AndreaitemmakerExpansion.parse("cooldown_id_")); // no mechanic
    }

    @Test
    void parsingIsCaseInsensitive() {
        assertEquals(Action.CONTENT_COUNT, AndreaitemmakerExpansion.parse("CONTENT_COUNT").action());
        Parsed has = AndreaitemmakerExpansion.parse("HAS_ITEM_Storm_Blade");
        assertEquals("storm_blade", has.id());
    }
}
