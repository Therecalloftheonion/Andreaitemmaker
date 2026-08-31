package com.andreaitemmaker.pack;

/**
 * Serializes asynchronous pack generation. At most one generation runs at a time; a request
 * made while one is running is coalesced into a single follow-up run that uses the latest
 * snapshot, so a burst of reloads/generations always ends with the newest content and never
 * runs two heavy generations concurrently.
 *
 * <p>Usage (single worker):
 * <pre>{@code
 * if (!coordinator.claim(snapshot)) return;       // someone else will run it
 * while (true) {
 *     PackSnapshot snap = coordinator.next();      // latest pending snapshot
 *     // ... do the generation, publish the result ...
 *     if (!coordinator.finish()) break;            // loop again when coalesced requests arrived
 * }
 * }</pre>
 */
public final class GenerationCoordinator<T> {

    private final Object lock = new Object();
    private T pending;
    private boolean running;

    /**
     * Offer a snapshot to generate.
     *
     * @return true when the caller is now the worker and must run the generation loop;
     *         false when a worker is already running and will pick this snapshot up
     */
    public boolean claim(T snapshot) {
        synchronized (lock) {
            pending = snapshot;
            if (running) {
                return false;
            }
            running = true;
            return true;
        }
    }

    /** Take the latest pending snapshot and clear it, or null when there is none. */
    public T next() {
        synchronized (lock) {
            T snapshot = pending;
            pending = null;
            return snapshot;
        }
    }

    /**
     * Mark the current run finished.
     *
     * @return true when more snapshots were offered meanwhile and the worker must loop again
     */
    public boolean finish() {
        synchronized (lock) {
            if (pending == null) {
                running = false;
                return false;
            }
            return true;
        }
    }

    /** Whether a snapshot is pending or a worker is currently running. */
    public boolean isPending() {
        synchronized (lock) {
            return running;
        }
    }
}
