package com.andreaitemmaker.protection;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Centralized protection/placement gate for custom blocks and furniture.
 *
 * <p>Placement never bypasses server protection: before the world is modified (and before any
 * item is consumed) the service consults, in order:
 * <ol>
 *   <li>Every registered {@link ProtectionProvider} (external plugins integrate here), and</li>
 *   <li>an optional reflective <b>WorldGuard</b> region check when WorldGuard is present.</li>
 * </ol>
 *
 * <p>WorldGuard is <b>soft</b>: the core never compiles against WorldGuard classes and no
 * hard dependency is added. The reflective call is guarded so the plugin works identically
 * with or without WorldGuard. Other protection plugins should register a
 * {@link ProtectionProvider} so their rules also apply.
 */
public final class ProtectionService {

    private final List<ProtectionProvider> providers = new CopyOnWriteArrayList<>();
    private volatile Object worldGuardQuery;
    private volatile Method testBuildMethod;
    private volatile boolean resolutionAttempted;

    /**
     * Check whether {@code player} may place a custom block at (or overlapping) {@code target}.
     * Respects normal build permissions and every registered protection provider.
     *
     * @return {@code true} when placement is allowed
     */
    public boolean canPlaceBlock(Player player, Block target) {
        if (!player.hasPermission("andreaitemmaker.build")) {
            return false;
        }
        for (ProtectionProvider provider : providers) {
            if (!provider.canPlace(player, target)) {
                return false;
            }
        }
        return testWorldGuard(player, target == null ? null : target.getLocation());
    }

    /**
     * Check whether {@code player} may place furniture centered at {@code location}. Equivalent
     * to {@link #canPlaceBlock} but keyed on an exact spawn location (furniture is an entity,
     * not a block).
     */
    public boolean canPlaceFurniture(Player player, Location location) {
        if (!player.hasPermission("andreaitemmaker.build")) {
            return false;
        }
        for (ProtectionProvider provider : providers) {
            if (!provider.canPlace(player, location == null ? null
                    : location.getBlock())) {
                return false;
            }
        }
        return testWorldGuard(player, location);
    }

    /** Register an external protection integration. */
    public void registerProvider(ProtectionProvider provider) {
        if (provider != null) {
            providers.add(provider);
        }
    }

    /** Remove a previously registered protection integration. */
    public void unregisterProvider(ProtectionProvider provider) {
        providers.remove(provider);
    }

    /** Number of registered protection providers (for diagnostics). */
    public int providerCount() {
        return providers.size();
    }

    /** Whether WorldGuard is present and reachable (for diagnostics). */
    public boolean isWorldGuardActive() {
        resolveWorldGuard();
        return worldGuardQuery != null && testBuildMethod != null;
    }

    /** Respects WorldGuard build regions reflectively. A reflective failure is treated as allowed. */
    private boolean testWorldGuard(Player player, Location location) {
        if (location == null) {
            return true;
        }
        resolveWorldGuard();
        if (worldGuardQuery == null || testBuildMethod == null) {
            return true;
        }
        try {
            return (boolean) testBuildMethod.invoke(worldGuardQuery, location, player);
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }

    private void resolveWorldGuard() {
        if (resolutionAttempted) {
            return;
        }
        resolutionAttempted = true;
        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object instance = wgClass.getMethod("getInstance").invoke(null);
            Object platform = instance.getClass().getMethod("getPlatform").invoke(instance);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = container.getClass().getMethod("createQuery").invoke(container);
            Method m = query.getClass().getMethod("testBuild", Location.class, Player.class);
            worldGuardQuery = query;
            testBuildMethod = m;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            // WorldGuard absent, older version, or API changed: no region check applies.
            worldGuardQuery = null;
            testBuildMethod = null;
        }
    }
}