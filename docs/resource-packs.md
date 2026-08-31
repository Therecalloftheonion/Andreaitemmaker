# Resource packs

Every reload regenerates the resource pack. On every generation the plugin writes
**both** `pack.zip` and an unzipped `pack/` folder, so you always have a way to
distribute the pack — even when a firewall blocks the built-in server.

## Delivery strategies

| Strategy | Setup |
| --- | --- |
| **Built-in server** (default) | Port 8163 must be reachable from players' clients |
| **Fixed URL** | Host `pack/` or `pack.zip` anywhere → set `pack.public-url` |
| **Upload to a file host** | `pack.upload.enabled: true` + URL/method/headers → players download from your CDN |

`/aitem pack url` shows the current download URL and folder path.
`/aitem pack regenerate` rebuilds the pack for all online players.

## How generation works

- **Never blocks the server.** On reload the plugin snapshots the content and builds
  textures, models, the zip and the SHA-1 on a background thread; the finished pack is
  swapped in atomically, so the previously generated pack keeps being served until the
  new one is fully ready. Overlapping reloads/generations coalesce into a single run (the
  latest requested state wins).
- **Deterministic identity.** The pack id/hash is derived from the pack content — an
  unchanged config regenerates the identical hash (clients can keep their cached copy),
  any change produces a new hash and a new prompt.
- **Caching.** Generated textures, models and converted models are cached and invalidated
  only when their inputs change.

## Delivery state

The plugin tracks per player: `sent`, `accepted`, `successfully loaded`, `failed`,
`declined`. A player is only considered served after actually loading the pack — a failed
download is retried on the next join. Players who decline are not spammed with repeated
prompts (unless `pack.required: true`, where they get a message).

## The built-in HTTP server

- Serves **only** `/pack.zip` — request paths are never mapped to files, so nothing else
  on the disk can be exposed (no path traversal possible, encoded or otherwise).
- Streams the pack in chunks instead of loading it into RAM per request.
- Bounded thread pool, `Content-Length`, `Content-Type`, `Cache-Control` and
  `ETag`/304 responses so unchanged packs are not re-downloaded.
- Shuts down cleanly on plugin disable.

## Command

```
/aitem pack send [player|all] | url | regenerate
```
