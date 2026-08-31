# Getting started

Andreaitemmaker turns plain YAML files into custom items, weapons, armor, blocks and
furniture — and builds, hosts and sends the resource pack automatically. No resource pack
experience needed.

## Requirements

| Requirement | Minimum |
| --- | --- |
| Java | 17+ (servers on 1.20.5+ already run Java 21) |
| Server | Spigot or Paper 1.20.5 or newer (tested through 1.21.5 / 26.x) |

## Install

1. Build the jar (from the project root):
   ```bash
   ./mvnw package        # Windows: mvnw.cmd package
   ```
   The jar lands in `target/Andreaitemmaker-<version>.jar`.
2. Drop the jar into the server's `plugins/` folder.
3. Restart the server. The plugin creates `plugins/Andreaitemmaker/` with:
   ```
   config.yml            # main configuration
   items/                # example item YAML files
   blocks/               # example block YAML files
   furniture/            # example furniture YAML files
   assets/models/        # your own model .json files go here
   assets/textures/      # your own texture .png files go here
   pack/                 # generated resource pack (unzipped)
   pack.zip              # generated resource pack (zipped)
   ```
4. Give yourself a test item:
   ```
   /aitem give example_sword
   ```
5. Players receive the pack automatically on join (`pack.send-on-join` is on by default).
   Port **8163** must be reachable from their clients, or host the generated `pack/`
   folder anywhere and set `pack.public-url` (see [resource-packs.md](resource-packs.md)).

## First reload

Every content change is picked up with:

```
/aitem reload
```

Reload is **transactional and asynchronous**: config is parsed and validated on a
background thread, the old content stays active until the new content is fully valid,
and the resource pack is regenerated in the background and swapped in atomically. A
malformed file never leaves the plugin half-loaded.

## Where content lives

- `items/` → one YAML file per item: `type: ITEM | WEAPON | ARMOR | FOOD`
- `blocks/` → one YAML file per block: `type: BLOCK` (`base-block` required)
- `furniture/` → one YAML file per furniture piece: `type: FURNITURE`
- `assets/textures/` → your own `.png` files (`.png.mcmeta` animations supported)
- `assets/models/` → your own model `.json` files (Blockbench exports, Bedrock or Java)

See [configuration.md](configuration.md) for every option.
