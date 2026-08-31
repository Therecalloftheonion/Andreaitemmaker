# Armor

`type: ARMOR` items are wearable. The material decides the slot (e.g.
`diamond_helmet` → head, `diamond_chestplate` → chest, `diamond_leggings` → legs,
`diamond_boots` → feet).

On 1.21.2+ there are two **native** worn-rendering paths, chosen automatically per item.

## 3D helmets (head slot)

A `HEAD`-slot armor piece whose `model:` file actually exists renders as its real 3D
model on the player's head — the same vanilla mechanism the carved pumpkin uses: the
client draws the equipped item's own model in `head` display context. The visual and the
gameplay state are literally the same stack, so they can't desync: what you equip is
exactly what renders, in survival, for every player. No per-tick work, no invisible
entities, no ModelEngine, no ProtocolLib.

```yaml
# items/my_helmet.yml
type: ARMOR
material: diamond_helmet
model: "assets/models/my_helmet.json"     # rendered 3D on the head when equipped
```

- Build the model in 16-unit "head space" — a 16×16×16 box from `0,0,0` to `16,16,16`
  covers the head exactly, like the pumpkin — or add a `display.head` entry to fine-tune
  the pose.
- `armor-texture:` is ignored for a 3D helmet: the model *is* the worn look.

## 2D layers (chest / legs / feet, and flat helmets)

The client has **no** native 3D worn path for body slots — those pieces render from a
flat 2D layer, never from the 3D model. Set the worn texture explicitly with a dedicated
64×32 humanoid armor texture:

```yaml
# items/my_chestplate.yml
type: ARMOR
material: diamond_chestplate
armor-texture: "assets/textures/my_layer_1.png"   # 64x32 look when worn
```

- `texture:` is the **item icon**; `armor-texture:` is the **worn layer** — two different
  images.
- Without `armor-texture:`, the plugin auto-detects by convention:
  `assets/textures/<id>_layer_1.png` / `_layer_2.png`, `<id>_armor_layer_1/2.png`, or a
  shared set file like `eternal_armor_layer_1.png` / `_2.png` for ids like
  `eternal_helmet` (layer 1 = helmet/chestplate/boots, layer 2 = leggings).
- A square UV atlas (16/32/64) is **never** squashed into the worn layer — only flat
  64×32 textures are used, so the worn piece can't look like garbled atlas regions.

## Worn mechanics

`mechanics.armor-effects` applies effects while the piece is worn. Only players who
actually wear relevant custom armor are tracked — no per-tick scan of every player.

## Version notes

| Server | Worn rendering |
| --- | --- |
| 1.21.2+ | equipment asset (2D layers) for body slots; 3D model on the head when a model exists |
| 1.20.5 – 1.21.1 | wearable, but shows the vanilla worn texture |

On 1.20.5 – 1.21.1 the 3D model still shows in hand/inventory/on the ground, but the worn
piece uses the vanilla layer texture (the client has no 3D-on-body path on those versions).
