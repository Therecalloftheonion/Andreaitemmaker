package com.andreaitemmaker.protection;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Integration point for protection plugins. A {@link ProtectionProvider} allows an external
 * plugin (or an optional WorldGuard bridge) to veto a custom block or furniture placement that
 * Andreaitemmaker is about to make.
 *
 * <p>Providers are consulted <b>before</b> the world is modified and <b>before</b> any item is
 * consumed, so a denied placement leaves the world unchanged and the player's inventory intact.
 * A provider may be evaluated on any thread depending on how the integration is wired; keep
 * implementations cheap and free of side effects.
 */
@FunctionalInterface
public interface ProtectionProvider {

    /**
     * Whether {@code player} may place a custom block at {@code target}.
     *
     * @return {@code true} to allow the placement, {@code false} to veto it
     */
    boolean canPlace(Player player, Block target);
}