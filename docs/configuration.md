# Configuration

All options live in `plugins/Andreaitemmaker/config.yml`. `config-version` is managed
automatically: plugin updates merge new options into your config while preserving every
value you changed.

```yaml
config-version: 1

# Namespace used for all generated resource pack assets (lowercase, no spaces).
namespace: itemmaker

pack:
  # Resource pack format override. Leave as AUTO to detect from the server version.
  # Known formats: 32 (1.20.5-6), 34 (1.21-1.21.1), 42 (1.21.2-3), 46 (1.21.4),
  # 55 (1.21.5), 63 (1.21.6), 64 (1.21.7-8), 69 (1.21.9-10), 75 (1.21.11),
  # 84 (26.1), 88 (26.2+).
  format: AUTO

  # Edge length of generated textures: 16, 32 or 64.
  texture-size: 16

  description: "Andreaitemmaker custom content"

  # Send the pack to players when they join.
  send-on-join: true
  # Force the pack (players who decline get a message).
  required: false
  prompt: "Install the custom content pack?"
  # Re-send the pack to online players after a reload.
  resend-on-reload: true

  # Built-in HTTP server that serves pack.zip. Disable it when using pack.upload instead.
  serve:
    enabled: true
    port: 8163

  # Optional: fixed URL players should download from (overrides the generated one).
  public-url: ""
  # Optional: public IP used to build the URL when public-url is empty.
  public-ip: ""

  # Optional: upload the pack to an external file host (CDN) with HTTP PUT/POST.
  upload:
    enabled: false
    method: PUT
    url: ""
    public-url: ""
    headers:
      Authorization: "Bearer your-token-here"

content:
  # First custom-model-data value assigned automatically (legacy servers only).
  custom-model-data-start: 1000
  # How often worn armor mechanics are checked, in seconds.
  armor-tick-seconds: 2
  # When true, placed custom blocks are immune to explosions (their persistent id stays
  # correct). When false, explosions destroy them like vanilla blocks and the id is cleaned up.
  explosion-protected: true
```

## Validation rules

- `pack.serve.port` must be a valid port (1–65535); anything else falls back to 8163.
- `pack.texture-size` only accepts 16, 32 or 64; anything else falls back to 16.
- `namespace` must be lowercase and contain no spaces.
- Content ids must be lowercase, alphanumeric with `_`/`-` (no spaces).
- `model:` and `texture:` paths must be relative paths inside `assets/`. Traversal
  attempts (`../`, absolute paths, Windows drives, backslashes, symlink escapes) are
  rejected at load time with an error naming the exact file and field.

## Explosions

`content.explosion-protected` (default `true`):

- `true` — placed custom blocks survive explosions; their persistent identity stays correct.
- `false` — explosions destroy them like vanilla blocks; the persistent id is cleaned up
  and drops are handled normally.
