# Manual testing checklist

These tests must be performed **by the repository owner on a real server** before
claiming production readiness. None of them have been run by automation.The server owner didnt however test them like he should

## Setup

1. Fresh install: drop the jar in `plugins/`, start the server, confirm
   `plugins/Andreaitemmaker/` is created with `config.yml`, example content and `assets/`
   folders.
2. `/aitem reload` completes without errors; log shows concise counts
   (`Loaded N items, M blocks, K furniture definitions in Xms`).
3. `/aitem diagnose` shows the plugin version, server version, Java version, content
   counts, pack status/hash/size and generation duration.

## Items

4. `/aitem give <id>` gives the item with correct name, lore and texture.
5. Using the item triggers its mechanics (right-click and hit mechanics).
6. Food restores hunger/saturation and respects cooldown.
7. Weapons deal configured damage.

## Armor

8. Equipping a piece applies `armor-effects` while worn and removes them when unequipped.
9. A HEAD armor piece with a `model:` renders its 3D model on the head (1.21.2+).
10. Chest/legs/feet pieces render the 2D `armor-texture:` layer (1.21.2+).

## Custom blocks

11. Place a custom block; the correct appearance replaces the base block.
12. The block keeps its identity across server restart, plugin reload and chunk
    unload/reload, in multiple worlds.
13. Breaking it drops the custom item (when `drops-item: true`) and cleans the id.
14. Explosion: with `explosion-protected: true` the block survives; with `false` it is
    destroyed and the id cleaned up.
15. A piston cannot push/pull a custom block.
16. Fire does not leave a stale id behind.
17. Replacing a custom block with a different material via an external editor is detected
    and the stale tag cleaned up.

## Furniture

18. Place furniture; the model renders on an invisible armor stand.
19. Right-click to pick it back up.
20. Furniture survives restart/reload; normal armor stands are unaffected.
21. Removing a furniture definition cleans up its entities.

## Protection

22. With WorldGuard installed, placing blocks/furniture in a protected region is denied
    (world unchanged, item not consumed).
23. Denying the `andreaitemmaker.build` permission blocks placement.
24. Vanilla placement restrictions still apply.

## Resource pack

25. A joining player receives the pack prompt and the content is visible after accepting.
26. A player who fails to download is re-sent the pack on the next join.
27. A player who successfully loads is not re-prompted unnecessarily.
28. `/aitem pack regenerate` re-sends the new pack to online players; `pack.url` shows the
    right URL.
29. With the firewall blocking port 8163, hosting `pack/` (unzipped folder) at a fixed URL
    via `pack.public-url` works.
30. Unchanged config regenerates the same hash; changing any content changes the hash.
31. `/aitem reload` twice in quick succession runs one combined generation (no crash, no
    stale state).

## Regression

32. Vanilla blocks and items behave completely normally.
33. Large content sets (hundreds of items) load and generate without errors.
34. Multiplayer: two players see the same custom content after both load the pack.
