# FAQ

## Do I need a resource pack experience? / Do I need a texture editor?

No. The plugin generates textures procedurally from YAML, builds all models/JSON, zips the
pack, and sends it to players. You only write YAML (and optionally drop in your own
models/textures).

## Does it work on Paper?

Yes — Paper is the recommended platform, especially for the newest versions. Spigot works
for the older range.

## Do 3D models work?

Yes. Any item can reference a model in `assets/models/` (Blockbench Java **or** Bedrock
exports — Bedrock ones are converted automatically). On 1.21.2+ a head-slot armor piece
with a model renders its 3D model on the player's head when equipped.

## What about 3D armor on the body (chest/legs/feet)?

The client has no native 3D worn path for body slots — that's a vanilla client
limitation, not a plugin one. Body armor renders from a 2D layer texture
(`armor-texture:`). Only the head supports native 3D.

## How do I add items?

1. Create `plugins/Andreaitemmaker/items/my_item.yml` (see [items.md](items.md)).
2. Run `/aitem reload`.
3. `/aitem give my_item`.

## I have my own model and texture. Where do they go?

- Model → `plugins/Andreaitemmaker/assets/models/`
- Texture → `plugins/Andreaitemmaker/assets/textures/`
- Reference the model in YAML with `model: "assets/models/my_model.json"`.

The model's texture path should point at `<namespace>:item/<name>` (default namespace:
`itemmaker`). Animated textures work: add a `.png.mcmeta` next to the PNG.

## My firewall blocks the port. What now?

Every generation also writes an unzipped `pack/` folder (plus `pack.zip`) next to the
plugin. Host that folder anywhere (file host, web server) and set `pack.public-url`.
Players then download from your URL instead of the built-in server.

## Which versions are supported?

1.20.5 through 26.2+, pack formats 32 → 88. See [compatibility.md](compatibility.md).

## Can I sell it? / Is it open source?

The project is Apache 2.0 licensed and open source on GitHub. You can use, modify and
redistribute it under the license terms — see the LICENSE file.

## Will my old YAML files keep working after an update?

Yes. The YAML format is stable and config migrations only add new options while
preserving your values.

## ItemsAdder / Oraxen imports?

Not supported — the plugin has its own YAML format (which is simpler). Model files and
textures from other plugins can be reused: drop them into `assets/` and reference them.

## Does the plugin work without PlaceholderAPI?

Yes. Placeholders are simply unavailable when PAPI is not installed.
