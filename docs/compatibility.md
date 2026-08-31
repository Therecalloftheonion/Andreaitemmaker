# Version compatibility

| Server | Pack format | Item wiring |
| --- | --- | --- |
| 1.20.5 – 1.20.6 | 32 | CustomModelData + patched vanilla models |
| 1.21 – 1.21.1 | 34 | CustomModelData + patched vanilla models |
| 1.21.2 – 1.21.3 | 42 | `item_model` component + item definitions |
| 1.21.4 – 1.21.8 | 46 / 55 / 63 / 64 | `item_model` + equipment assets for armor |
| 1.21.9 – 1.21.10 | 69 | min/max format in `pack.mcmeta` |
| 1.21.11 | 75 | equipment textures under `textures/entity/equipment/` |
| 26.1 / 26.2+ | 84 / 88 | latest |

- The plugin compiles against Spigot 1.20.6 and uses a thin compatibility layer
  (reflection isolated in one place) for newer APIs — version-specific behavior lives in
  `ServerVersion` / pack-layout classes, not scattered through the codebase.
- `pack.format` in `config.yml` overrides detection if you ever need to pin a format.
- Paper is fully supported (recommended for the newest versions); Spigot works for the
  older range.

## Worn armor by version

- 1.21.2+: body slots render the 2D equipment asset; a head piece with a model renders
  its 3D model on the head natively.
- 1.20.5 – 1.21.1: pieces are wearable but show the vanilla worn texture.

## GeyserMC

Item/armor support through GeyserMC's Bedrock resource packs is **not** implemented.
Bedrock Edition clients render items/armor differently and would need a separate Bedrock
pack. This is a known limitation, not a bug.
