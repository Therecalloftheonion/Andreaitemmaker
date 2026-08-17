package com.andreaitemmaker;

import com.andreaitemmaker.util.ServerVersion;
import com.andreaitemmaker.util.ServerVersion.Mode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerVersionTest {

    @Test
    void parsesBukkitVersionStrings() {
        assertEquals(new ServerVersion.Version(1, 21, 4), ServerVersion.parse("1.21.4-R0.1-SNAPSHOT"));
        assertEquals(new ServerVersion.Version(26, 2, 0), ServerVersion.parse("26.2-R0.1-SNAPSHOT"));
        assertEquals(new ServerVersion.Version(1, 21, 0), ServerVersion.parse("1.21-R0.1-SNAPSHOT"));
        assertEquals(new ServerVersion.Version(1, 20, 5), ServerVersion.parse("1.20.5-R0.1-SNAPSHOT"));
        assertNull(ServerVersion.parse("garbage"));
        assertNull(ServerVersion.parse(null));
    }

    @Test
    void mapsFormats() {
        assertEquals(32, ServerVersion.targetFor(ServerVersion.parse("1.20.5-R0.1-SNAPSHOT")).format());
        assertEquals(34, ServerVersion.targetFor(ServerVersion.parse("1.21.1-R0.1-SNAPSHOT")).format());
        assertEquals(42, ServerVersion.targetFor(ServerVersion.parse("1.21.2-R0.1-SNAPSHOT")).format());
        assertEquals(46, ServerVersion.targetFor(ServerVersion.parse("1.21.4-R0.1-SNAPSHOT")).format());
        assertEquals(55, ServerVersion.targetFor(ServerVersion.parse("1.21.5-R0.1-SNAPSHOT")).format());
        assertEquals(64, ServerVersion.targetFor(ServerVersion.parse("1.21.8-R0.1-SNAPSHOT")).format());
        assertEquals(69, ServerVersion.targetFor(ServerVersion.parse("1.21.9-R0.1-SNAPSHOT")).format());
        assertEquals(75, ServerVersion.targetFor(ServerVersion.parse("1.21.11-R0.1-SNAPSHOT")).format());
        assertEquals(84, ServerVersion.targetFor(ServerVersion.parse("26.1-R0.1-SNAPSHOT")).format());
        assertEquals(88, ServerVersion.targetFor(ServerVersion.parse("26.2-R0.1-SNAPSHOT")).format());
    }

    @Test
    void selectsGenerationMode() {
        assertEquals(Mode.LEGACY, ServerVersion.targetFor(ServerVersion.parse("1.20.5-R0.1-SNAPSHOT")).mode());
        assertEquals(Mode.LEGACY, ServerVersion.targetFor(ServerVersion.parse("1.21.1-R0.1-SNAPSHOT")).mode());
        assertEquals(Mode.MODERN, ServerVersion.targetFor(ServerVersion.parse("1.21.2-R0.1-SNAPSHOT")).mode());
        assertEquals(Mode.MODERN, ServerVersion.targetFor(ServerVersion.parse("26.2-R0.1-SNAPSHOT")).mode());
    }

    @Test
    void rangeFormatOnlyFrom1219() {
        assertFalse(ServerVersion.usesRangeFormat(ServerVersion.parse("1.21.8-R0.1-SNAPSHOT")));
        assertTrue(ServerVersion.usesRangeFormat(ServerVersion.parse("1.21.9-R0.1-SNAPSHOT")));
        assertTrue(ServerVersion.usesRangeFormat(ServerVersion.parse("26.1-R0.1-SNAPSHOT")));
    }

    @Test
    void comparison() {
        assertTrue(ServerVersion.parse("1.21.4-R0.1-SNAPSHOT").isAtLeast(1, 21, 2));
        assertFalse(ServerVersion.parse("1.21.1-R0.1-SNAPSHOT").isAtLeast(1, 21, 2));
        assertTrue(ServerVersion.parse("26.2-R0.1-SNAPSHOT").isAtLeast(1, 21, 4));
    }
}
