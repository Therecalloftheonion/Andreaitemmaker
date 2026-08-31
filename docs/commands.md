# Commands & permissions

Aliases: `andreaitemmaker`, `aitem`, `itemmaker`.

## Commands

| Command | Description |
| --- | --- |
| `/aitem give <id> [amount] [player]` | Give a custom item |
| `/aitem list [items\|weapons\|armor\|food\|blocks\|furniture]` | List loaded content |
| `/aitem info <id>` | Show a content entry's details |
| `/aitem pack send [player\|all]` | Send the pack to a player or everyone |
| `/aitem pack url` | Show the current download URL and pack folder path |
| `/aitem pack regenerate` | Regenerate the pack and re-send to online players |
| `/aitem reload` | Reload config + content + regenerate the pack (async, transactional) |
| `/aitem diagnose` | Show plugin, server, Java versions, content counts, pack status/hash/size, generation duration, HTTP server state, warnings |
| `/aitem stats` | Show performance metrics: reload/generation durations, pack size, cache stats, tracked armor players, furniture count |

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `andreaitemmaker.admin` | op | All commands |
| `andreaitemmaker.give` | op | Give items |
| `andreaitemmaker.bypass` | false | Never receive the pack prompt |
| `andreaitemmaker.build` | true | Place custom blocks / furniture (checked against protection plugins too) |

## Reload behavior

`/aitem reload`:

1. creates a reload request and returns immediately — the server never stalls
2. on a background thread: reads config, parses YAML, validates content, builds a
   candidate registry, validates cross-references
3. on the main thread: atomically publishes the candidate state
4. on a background thread: regenerates the resource pack

If anything fails, the previous working state stays active and the error names the exact
content id, config file, field and cause.
