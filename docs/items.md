# Items, weapons, armor, food

Each file in `items/` defines one custom item. The `type` decides which extra fields
apply:

| Type | Purpose | Extra fields |
| --- | --- | --- |
| `ITEM` | basic item | `mechanics`, `texture` |
| `WEAPON` | melee weapon | `attributes.attack_damage/attack_speed`, `mechanics` |
| `ARMOR` | wearable armor | `armor-texture`, `mechanics.armor-effects`, slot from material |
| `FOOD` | edible item | `food.hunger/saturation/cooldown`, `mechanics` |

## Common fields

```yaml
# items/my_sword.yml
type: WEAPON
material: diamond_sword          # vanilla base material
display-name: "&bStorm Blade"    # & color codes work
lore:
  - "&7A blade crackling with lightning."
attributes:
  attack_damage: 9.0
  attack_speed: 1.6
enchantments:
  sharpness: 3
unbreakable: true
glow: true
model: "assets/models/my_sword.json"     # optional 3D model (Blockbench export)
texture:
  pattern: gradient              # solid | gradient | diagonal | checker
  color: "#3f9bff"
  color2: "#0f2a6b"
  outline: true
mechanics:
  lightning:
    damage: 4.0
```

| Field | Meaning |
| --- | --- |
| `type` | `ITEM`, `WEAPON`, `ARMOR`, `FOOD` |
| `material` | vanilla material the item is based on |
| `display-name` | shown name (supports `&` color codes) |
| `lore` | list of lore lines |
| `attributes` | `attack_damage`, `attack_speed`, `armor`, `armor_toughness`, `knockback_resistance`, `luck`, `max_health`… |
| `enchantments` | map of enchantment name → level |
| `unbreakable` | true/false |
| `glow` | adds enchantment glint |
| `model` | path to a model in `assets/models/` (renders 3D in hand/inventory) |
| `texture` | procedural texture settings (no image file needed) |
| `mechanics` | behaviors — see [mechanics.md](mechanics.md) |

## Food

```yaml
type: FOOD
material: cookie
display-name: "&6Golden Cookie"
food:
  hunger: 4
  saturation: 6
  cooldown: 3
mechanics:
  heal:
    amount: 2
    cooldown: 3
```

`food.hunger` (0–20), `food.saturation`, `food.cooldown` (seconds between eats).

## Custom models & textures

Drop a Blockbench export into `assets/models/` and reference it with `model:`.

**Drag-and-drop:** Blockbench **Bedrock Edition** exports work as-is — the plugin detects
them (`format_version`, `groups`, numeric texture keys) and converts them to the Java
format automatically: ordering inverted `from`/`to` boxes, renaming `0`/`1` →
`layer0`/`layer1`, rounding rotation angles to 22.5° steps and stripping Bedrock-only
fields. Java exports pass through untouched.

Any PNG in `assets/textures/` is copied into the pack (with its `.mcmeta` if animated).
The model's texture path should point at `<namespace>:item/<name>` (default namespace:
`itemmaker`).

## Giving items

```
/aitem give <id> [amount] [player]
```

or through the API (`api.createItemStack("my_sword", 1)`).
