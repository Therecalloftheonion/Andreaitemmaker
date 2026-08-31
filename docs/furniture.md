# Furniture

Each file in `furniture/` defines one furniture piece. Furniture is rendered as an
invisible armor stand wearing the item's model, placed by right-clicking the item.

```yaml
# furniture/my_lamp.yml
material: stick
small: true          # small armor stand
consumable: true     # the item is consumed when placed
model: "assets/models/my_lamp.json"
```

| Field | Meaning |
| --- | --- |
| `small` | use a small (baby) armor stand |
| `consumable` | consume the item on placement |
| `model` | model in `assets/models/` (the stand holds the item) |
| `mechanics` | item mechanics (see [mechanics.md](mechanics.md)) |

## Placing and picking up

- Right-click on a surface to place it. Placement validates the target, checks the
  `andreaitemmaker.build` permission and protection plugins (same `ProtectionService` as
  custom blocks) — a protected region never gets a furniture armor stand spawned.
- Right-click the placed piece to pick it back up (when the definition allows it).

## Identity & lifecycle

- Every furniture entity carries a unique plugin persistent-data identity, so normal
  armor stands are never touched.
- Orphaned entities (owner offline, definition removed) are cleaned up.
- Chunk unload/load, server restart and plugin reload are safe: entities are re-identified
  from their persistent data, never from position heuristics.
- Removing a furniture definition from config cleans up its invisible entities on reload.
- No constant per-tick processing: furniture is event-driven, and there is no scan of all
  entities — identification is a direct persistent-data lookup.
