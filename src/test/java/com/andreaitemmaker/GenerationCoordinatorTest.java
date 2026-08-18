package com.andreaitemmaker;

import com.andreaitemmaker.pack.GenerationCoordinator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationCoordinatorTest {

    private final GenerationCoordinator<String> coordinator = new GenerationCoordinator<>();

    @Test
    void firstClaimBecomesWorker() {
        assertTrue(coordinator.claim("v1"));
        assertEquals("v1", coordinator.next());
        assertFalse(coordinator.finish());
        // After the run, a new claim becomes a worker again.
        assertTrue(coordinator.claim("v2"));
    }

    @Test
    void requestWhileRunningIsCoalesced() {
        assertTrue(coordinator.claim("v1"));
        // Worker is running; more requests must not spawn more workers.
        assertFalse(coordinator.claim("v2"));
        assertFalse(coordinator.claim("v3"));
        // The worker picks up the LATEST snapshot only (v2 and v3 coalesce into v3).
        assertEquals("v3", coordinator.next());
        // A claim arriving before finish() queues one follow-up run.
        assertFalse(coordinator.claim("v4"));
        assertTrue(coordinator.finish());
        assertEquals("v4", coordinator.next());
        assertFalse(coordinator.finish()); // nothing pending -> worker stops
    }

    @Test
    void finishWithoutPendingStopsWorker() {
        assertTrue(coordinator.claim("v1"));
        assertEquals("v1", coordinator.next());
        assertFalse(coordinator.finish());
        assertTrue(coordinator.claim("v2"));
    }

    @Test
    void nextClearsPending() {
        assertTrue(coordinator.claim("v1"));
        assertEquals("v1", coordinator.next());
        assertNull(coordinator.next());
    }

    @Test
    void fullCycleWithCoalescing() {
        assertTrue(coordinator.claim("a"));
        assertFalse(coordinator.claim("b"));
        assertFalse(coordinator.claim("c"));
        assertEquals("c", coordinator.next());
        assertFalse(coordinator.claim("d")); // still running
        assertTrue(coordinator.finish());
        assertEquals("d", coordinator.next());
        assertFalse(coordinator.finish());
        assertTrue(coordinator.claim("e"));
        assertEquals("e", coordinator.next());
        assertFalse(coordinator.finish());
    }
}
