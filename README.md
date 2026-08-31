<div align="center">

# ⚡ Andreaitemmaker

**Custom items, weapons, armor, blocks and furniture from plain YAML — with the resource pack built, hosted and sent automatically.**

![Minecraft](https://img.shields.io/badge/Minecraft-1.20.5%20→%2026.2+-blue)
![Spigot](https://img.shields.io/badge/Spigot%20%2F%20Paper-supported-brightgreen)
![Java](https://img.shields.io/badge/Java-17%2B-orange)
![Version](https://img.shields.io/badge/version-2.0.0--beta-informational)
![Tests](https://img.shields.io/badge/tests-56%20passing-brightgreen)
![Vibe](https://img.shields.io/badge/vibe-coded-ff69b4)

*No manual resource pack work. No texture editor required. Just YAML.*

</div>

---

## ✨ What it does

Server owners define custom content as YAML files. On every reload, Andreaitemmaker:

1. **Parses** all content files — bad entries are reported and skipped, never break the rest.
2. **Generates** textures (procedural PNGs — no image files needed), model JSONs, item definitions, block states and armor assets.
3. **Wires** everything for the running server version (1.20.5 → 26.2+, pack formats 32 → 88).
4. **Zips** it into `pack.zip` + an unzipped `pack/` folder, computes the SHA-1.
5. **Distributes** it — built-in HTTP server, fixed public URL, or upload to a CDN.
6. **Sends** it to joining players with hash verification and retry messaging.

**No resource pack experience required.** The plugin does the whole pipeline for you.

## 🚀 Quick start

```bash
# Build (Maven wrapper downloads Maven itself — no local install needed)
./mvnw package        # Windows: mvnw.cmd package
```

1. Drop `target/Andreaitemmaker-2.0.0-beta.jar` into your server's `plugins/` folder.
2. Restart. The plugin creates `plugins/Andreaitemmaker/` with `config.yml` and example content.
3. Give yourself something:
   ```
   /aitem give example_sword
   ```
4. Players get the pack automatically on join (`pack.send-on-join` is on by default).

> **Port 8163** is opened by default for the built-in pack server. Firewalled? Host the
> generated `pack/` folder anywhere and set `pack.public-url` — see [Hosting the pack](#hosting-the-pack).

## 📦 Requirements

| Requirement | Minimum |
| --- | --- |
| Java | 17+ (servers on 1.20.5+ already run Java 21) |
| Server | Spigot or Paper 1.20.5 or newer (tested through with 1.21.5) |

## 🧩 Content

Content lives in `plugins/Andreaitemmaker/` — one YAML file per item, block or furniture piece:

```
items/       → type: ITEM | WEAPON | ARMOR | FOOD
blocks/      → type: BLOCK (base-block required)
furniture/   → type: FURNITURE
assets/textures/   → your own .png files (optional, .png.mcmeta animations supported)
assets/models/     → your own model .json files (optional, e.g. Blockbench exports)
```

### A weapon in 10 lines

```yaml
# items/my_sword.yml
type: WEAPON
material: diamond_sword
display-name: "&bStorm Blade"
attributes:
  attack_damage: 9.0
texture:
  pattern: gradient            # solid | gradient | diagonal | checker
  color: "#3f9bff"
  color2: "#0f2a6b"
mechanics:
  lightning:
    damage: 4.0
```

### Furniture

```yaml
# furniture/my_lamp.yml
material: stick
small: true
consumable: true
```

Right-click places an invisible armor stand rendering the item model; right-click again to pick it up.

### Blocks

```yaml
# blocks/my_block.yml
material: stick
base-block: white_wool        # vanilla block used as the hitbox (one base = one custom block)
drops-item: true
```

### Custom models & textures

Drop a Blockbench export into `assets/models/` and reference it:

```yaml
model: "assets/models/my_statue.json"
```

**Drag-and-drop:** Blockbench **Bedrock Edition** exports work as-is. The plugin detects them
(`format_version`, `groups`, numeric texture keys) and converts them to the Java format
automatically — ordering inverted `from`/`to` boxes, renaming `0`/`1` → `layer0`/`layer1`,
rounding rotation angles to 22.5° steps and stripping Bedrock-only fields. Java exports pass
through untouched.

Any PNG in `assets/textures/` is copied into the pack (with its `.mcmeta` if it's animated).
The model's texture path should point at `<namespace>:item/<name>` (default namespace: `itemmaker`).

### Armor: the worn look (`armor-texture:`)

On 1.21.2+ the **worn** piece renders from a flat 2D layer, never from the 3D model. Set the
worn texture explicitly with a dedicated 64×32 humanoid armor texture:

```yaml
# items/my_helmet.yml
type: ARMOR
material: diamond_helmet
model: "assets/models/my_helmet.json"     # 3D look in hand / inventory
armor-texture: "assets/textures/my_layer_1.png"   # 64x32 look when worn
```

- `texture:` is the **item icon**; `armor-texture:` is the **worn layer** — two different images.
- Without `armor-texture:`, the plugin auto-detects by convention: `assets/textures/<id>_layer_1.png`
  / `_layer_2.png`, `<id>_armor_layer_1/2.png`, or a shared set file like
  `eternal_armor_layer_1.png` / `_2.png` for ids like `eternal_helmet` (layer 1 = helmet/
  chestplate/boots, layer 2 = leggings).
- A square UV atlas (16/32/64) is **never** squashed into the worn layer — only flat 64×32
  textures are used, so the worn piece can't look like garbled atlas regions.

## ⚙️ Mechanics

Attach behaviors to any item — all config-driven:

| Mechanic | When | Options |
| --- | --- | --- |
| `heal` | right-click | `amount`, `cooldown` |
| `feed` | right-click | `amount`, `saturation`, `cooldown` |
| `effect` | right-click | `effects: [{type, duration, amplifier, ambient, particles}]`, `cooldown` |
| `launch` | right-click | `power`, `cooldown` |
| `sound` | right-click | `sound`, `volume`, `pitch` |
| `lightning` | hit entity | `damage` (0 = visual only) |
| `ignite` | hit entity | `seconds` |
| `knockback` | hit entity | `power` |
| `armor-effects` | while worn (armor) | `effects:` same format as `effect` |

Unknown mechanics are reported at load so typos are caught immediately. Other plugins can
**register their own mechanics** through the API.

## 🎮 Commands & permissions

```
/aitem give <id> [amount] [player]
/aitem list [items|weapons|armor|food|blocks|furniture]
/aitem info <id>
/aitem pack send [player|all] | url | regenerate
/aitem reload
```

Aliases: `andreaitemmaker`, `aitem`, `itemmaker`.

| Permission | Default | Purpose |
| --- | --- | --- |
| `andreaitemmaker.admin` | op | All commands |
| `andreaitemmaker.give` | op | Give items |
| `andreaitemmaker.bypass` | false | Never receive the pack prompt |

## 🌐 Hosting the pack

Every generation writes **both** `pack.zip` and an unzipped `pack/` folder, so you always
have a way to distribute the pack — even when a firewall blocks the built-in server.

| Strategy | Setup |
| --- | --- |
| **Built-in server** (default) | Port 8163 must be reachable from players' clients |
| **Fixed URL** | Host `pack/` or `pack.zip` anywhere → set `pack.public-url` |
| **Upload to a file host** | `pack.upload.enabled: true` + URL/method/headers → players download from your CDN |

`/aitem pack url` shows the current download URL and folder path.

## 🔌 Developer API

Other plugins can use Andreaitemmaker through `AndreaitemmakerAPI`:

```java
import com.andreaitemmaker.api.AndreaitemmakerAPI;
import com.andreaitemmaker.api.CustomItem;
import org.bukkit.inventory.ItemStack;

AndreaitemmakerAPI api = AndreaitemmakerAPI.get();

CustomItem sword = api.getCustomItem("my_sword");      // by id
CustomItem held  = api.getCustomItem(playerStack);     // by ItemStack (PDC tag)
ItemStack stack  = api.createItemStack("my_sword", 1); // build a stack

api.getCustomBlocks();       // all blocks
api.getCustomFurnitures();   // all furniture
api.getResourcePack();       // generate(), getUrl(), getSha1(), sendTo(player), ...
api.getMechanicRegistry();   // register(new MyMechanic()); — add your own mechanics
api.reload();
```

Cancellable events: `CustomItemUseEvent`, `CustomItemHitEvent`, `CustomItemConsumeEvent`,
`CustomBlockPlaceEvent`, `CustomBlockBreakEvent`, `CustomFurniturePlaceEvent`,
`CustomFurnitureBreakEvent`.

## 🔌 PlaceholderAPI (optional)

With [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) installed, the
plugin registers placeholders under the `andreaitemmaker` identifier — without PAPI the
plugin works exactly the same, the placeholders are simply unavailable.

| Placeholder | Returns |
| --- | --- |
| `%andreaitemmaker_has_item_<id>%` | `yes`/`no` — the player has the item in their inventory |
| `%andreaitemmaker_amount_<id>%` | total count of that item in the player's inventory |
| `%andreaitemmaker_holding_<id>%` | `yes`/`no` — the item is in the player's main hand |
| `%andreaitemmaker_cooldown_<id>_<mechanic>%` | remaining cooldown seconds for that mechanic (`0` = ready) |
| `%andreaitemmaker_content_count%` | total content entries |
| `%andreaitemmaker_item_count%` / `weapon_count` / `armor_count` / `food_count` / `block_count` / `furniture_count` | per-type counts |

Example line for a scoreboard/TAB plugin:

```
Cooldown: %andreaitemmaker_cooldown_storm_blade_lightning%s
```

## 🧪 Tests

56 unit tests cover PNG encode/decode round-trips, JSON validity, texture generation,
version → pack-format mapping, full modern/legacy pack generation, plus:

- path-traversal protection (absolute paths, `../`, Windows drives, backslashes, symlink escapes)
- registry consistency (replacement removes stale indexes, base-block conflicts, immutability)
- the embedded HTTP server over a real socket (headers, ETag/304, path security, method rejection)
- async generation coordinator (overlapping requests coalesce to the latest snapshot)
- imported model/texture collisions and invalid-model rejection

## 🗺️ Version support

| Server | Pack format | Item wiring |
| --- | --- | --- |
| 1.20.5 – 1.20.6 | 32 | CustomModelData + patched vanilla models |
| 1.21 – 1.21.1 | 34 | CustomModelData + patched vanilla models |
| 1.21.2 – 1.21.3 | 42 | `item_model` component + item definitions |
| 1.21.4 – 1.21.8 | 46 / 55 / 63 / 64 | `item_model` + equipment assets for armor |
| 1.21.9 – 1.21.10 | 69 | min/max format in `pack.mcmeta` |
| 1.21.11 | 75 | equipment textures under `textures/entity/equipment/` |
| 26.1 / 26.2+ | 84 / 88 | latest |

`pack.format` in `config.yml` overrides detection if you ever need to pin it.

### How it works under the hood

- **Custom blocks have a persistent identity.** Placing a custom block stores its id in the
  *chunk's* persistent data, keyed by block coordinates — not by material. A normal vanilla
  STONE or WOOL block is never treated as a custom block, and the identity survives server
  restarts, plugin reloads and chunk unload/reload in every world. The tag is removed when
  the block is broken or replaced.
- **Pack generation never blocks the server.** On reload the plugin snapshots the content
  and builds textures, models, the zip and the SHA-1 on a background thread; the finished
  pack is swapped in atomically, so the previously generated pack keeps being served until
  the new one is fully ready. Overlapping reloads/generations coalesce into a single run.
- **Reload is transactional.** Config and content are loaded and validated first; the old
  state stays active until the new one is complete, so a broken config never leaves the
  plugin half-loaded.
- **Config asset paths are validated.** `model:` and `texture:` entries must be relative
  paths inside `assets/` — traversal attempts (`../`, absolute paths, Windows drives,
  symlink escapes) are rejected at load time with a clear error.
- **The built-in pack server is minimal and safe.** It serves only `/pack.zip` (never maps
  request paths to files), streams the pack in chunks, uses a bounded thread pool, and
  answers with `Content-Length`, `Content-Type`, `Cache-Control` and `ETag`/304 so clients
  don't re-download an unchanged pack.
- **Armor mechanics only scan players who actually wear custom armor.** The tracked set is
  kept fresh by inventory/interact/death events plus a slow reconciliation, instead of
  checking every player's four armor slots on every tick.

### Known limitations

- **Custom blocks**: one base block per custom block; the base block's vanilla appearance is replaced for everyone with the pack.
- **Worn armor** renders from the equipment asset on 1.21.2+ (the `equippable` component with
  `asset_id`/model wired by Paper). On 1.20.5 – 1.21.1 the piece is wearable but shows the
  vanilla worn texture. Worn rendering is always the 2D layer texture — the client renderer
  has no 3D-on-body path, so a 3D model shows in hand/inventory/on the ground while the worn
  piece uses the layer texture. Point `armor-texture:` at a 64×32 armor texture to control
  the worn look (see above); without it the plugin auto-detects layer files or uses the
  model's own texture when it is already a flat 64×32 layer.
- Items/blocks placed through the API bypass protection plugins (WorldGuard, etc.) — add protections in your own listeners if needed.
- **Async generation**: `/aitem reload` and `/aitem pack regenerate` report that generation *started* — the pack swap happens in the background and players receive the new pack automatically once it's ready.
- **External world editors**: replacing a custom block with a *different* material is detected and the stale tag cleaned up; replacing it with the *same* base material is indistinguishable and breaking it will drop the custom item.
- **Armor tracking** covers all vanilla ways to change armor (equip clicks, right-click equip, death/respawn, join/quit) plus a 30-second safety recheck; direct `setItem` calls from other plugins are picked up within that window.

## 🛠️ Configuration

All options live in `config.yml` with comments. Highlights:

```yaml
namespace: itemmaker
pack:
  format: AUTO            # or pin e.g. 46
  texture-size: 16        # 16 | 32 | 64
  send-on-join: true
  required: false
  serve: { enabled: true, port: 8163 }
  public-url: ""
  upload: { enabled: false, method: PUT, url: "", headers: {} }
```

`config-version` is managed automatically — plugin updates merge new options into your
`config.yml` while preserving every value you changed.

## 📄 License

Licensed under the **Apache License 2.0**. See the [LICENSE](LICENSE) file.

---

<div align="center">

**Made for Minecraft server owners who just want to add content.**

*This project was vibe coded — built with an AI pair-programmer. Bugs may vibe too; issues and pull requests welcome!*

</div>
