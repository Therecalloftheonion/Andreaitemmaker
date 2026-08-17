<div align="center">

# ⚡ Andreaitemmaker

**Custom items, weapons, armor, blocks and furniture from plain YAML — with the resource pack built, hosted and sent automatically.**

![Minecraft](https://img.shields.io/badge/Minecraft-1.20.5%20→%2026.2+-blue)
![Spigot](https://img.shields.io/badge/Spigot%20%2F%20Paper-supported-brightgreen)
![Java](https://img.shields.io/badge/Java-17%2B-orange)
![Version](https://img.shields.io/badge/version-1.0.0-informational)
![Tests](https://img.shields.io/badge/tests-22%20passing-brightgreen)
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

1. Drop `target/Andreaitemmaker-1.0.0.jar` into your server's `plugins/` folder.
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
| Server | Spigot or Paper 1.20.5 or newer (tested through 26.2) |

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

Any PNG in `assets/textures/` is copied into the pack (with its `.mcmeta` if it's animated).
The model's texture path should point at `<namespace>:item/<name>` (default namespace: `itemmaker`).

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

## 🧪 Tests

22 unit tests cover PNG encode/decode round-trips, JSON validity, texture generation,
version → pack-format mapping, and full modern/legacy pack generation.

## 🗺️ Version support

| Server | Pack format | Item wiring |
| --- | --- | --- |
| 1.20.5 – 1.20.6 | 32 | CustomModelData + patched vanilla models |
| 1.21 – 1.21.1 | 34 | CustomModelData + patched vanilla models |
| 1.21.2 – 1.21.3 | 42 | `item_model` component + item definitions |
| 1.21.4 – 1.21.8 | 46 / 55 / 63 / 64 | `item_model` + equipment assets for armor |
| 1.21.9 – 1.21.10 | 69 | min/max format in `pack.mcmeta` |
| 1.21.11 | 75 | new equipment texture paths |
| 26.1 / 26.2+ | 84 / 88 | latest |

`pack.format` in `config.yml` overrides detection if you ever need to pin it.

### Known limitations

- **Custom blocks**: one base block per custom block; the base block's vanilla appearance is replaced for everyone with the pack.
- **Worn armor textures** render on 1.21.2+ (equippable component). On 1.20.5 – 1.21.1 the armor piece is wearable but shows the vanilla worn texture.
- Items/blocks placed through the API bypass protection plugins (WorldGuard, etc.) — add protections in your own listeners if needed.

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

---

<div align="center">

**Made for Minecraft server owners who just want to add content.**

*This project was vibe coded — built with an AI pair-programmer. Bugs may vibe too; issues and pull requests welcome!*

</div>
