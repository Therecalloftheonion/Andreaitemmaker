# Custom blocks

Each file in `blocks/` defines one custom block. The block uses a vanilla block as its
physical hitbox and replaces its appearance with your model.

```yaml
# blocks/my_block.yml
material: stick                # the item you hold to place it
base-block: white_wool         # vanilla block used as the hitbox (one base = one custom block)
drops-item: true               # breaking drops the custom item
model: "assets/models/my_block.json"   # optional model (defaults to a generated box)
```

| Field | Meaning |
| --- | --- |
| `base-block` | vanilla material used as the world hitbox. **One base block = one custom block.** Two custom blocks on the same base conflict and are rejected at load. |
| `drops-item` | whether breaking drops the custom item |
| `model` | model in `assets/models/`; without one, a generated cube with the item texture is used |
| `mechanics` | item mechanics (see [mechanics.md](mechanics.md)) |

## Placing and breaking

- Right-click the block item on a surface to place it. Placement validates the target,
  checks the `andreaitemmaker.build` permission, protection plugins, collisions and block
  state — if any step fails, the world is unchanged and the item is not consumed.
- Break with any tool (or your hand). The persistent id is removed and the custom item
  drops when `drops-item: true`.

## Persistent identity

Custom blocks are **never** identified by material. A normal vanilla STONE or WOOL block
is never treated as a custom block.

Placing a custom block stores its id in the **chunk's** persistent data, keyed by block
coordinates. The identity:

- survives server restarts, plugin reloads and chunk unload/reload
- works in every world
- is removed when the block is broken or replaced
- is cleaned up when a custom block is destroyed by explosions (when
  `content.explosion-protected: false`)
- is cleaned up if an external editor replaces the block with a *different* material

**Pistons:** moving a custom block with a piston is blocked — the plugin refuses to move
a block without its persistent identity.

**Explosions:** with `content.explosion-protected: true` (default) custom blocks are
removed from explosion block lists and survive. Set it to `false` to let explosions
destroy them (the id is then cleaned up).

## Protection

Block placement goes through the plugin's `ProtectionService`:

1. vanilla Bukkit placement restrictions
2. the `andreaitemmaker.build` permission (default `true`)
3. any registered protection providers (an optional WorldGuard hook is detected
   reflectively when WorldGuard is installed — the core never depends on WorldGuard)
4. plugin API callers can register their own `ProtectionProvider` for other protection
   plugins

If placement is denied, the block is not changed and the item is not consumed. Note that
placements made **through the API** (`api.placeBlock(...)`) follow the same protection
contract — unless the API caller explicitly passes a bypassing context.
