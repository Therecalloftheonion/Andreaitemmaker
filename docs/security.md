# Security

## Path traversal

Every path coming from YAML, config, the API, imported models or textures is validated by
a single centralized safe-path resolver. Rejected at load time, with an error naming the
exact file and field:

- `../` and `..\` escapes
- absolute paths (`/etc/...`, `C:\...`)
- Windows drive paths and UNC paths
- backslash variants and encoded traversal
- symlink escapes

Only files inside the approved asset roots (`assets/models/`, `assets/textures/`) are
accepted. Malicious paths are rejected with a clear error, never silently normalized.

## HTTP server

The built-in pack server is minimal by design:

- serves **only** `/pack.zip` — request paths are never mapped to filesystem paths, so no
  arbitrary file can ever be exposed
- streams the pack in chunks (no full-file buffering per request)
- bounded thread pool (no unbounded thread creation)
- rejects unsupported methods
- `Content-Length`, `Content-Type`, `Cache-Control`, `ETag`/304 for caching
- clean shutdown on plugin disable

## Content validation

- Global config corruption aborts a reload (the old state stays active).
- A single bad content entry is reported and skipped without corrupting the rest.
- Duplicate ids and duplicate base blocks are rejected.
- JSON from imported models is structurally validated before use.

## Sensitive data

- `/aitem diagnose` and `/aitem stats` never print secrets (upload tokens, etc.).
- The upload token lives only in `config.yml` (and the git-ignored `plugins/` /
  `Andreaitemmaker/` folders). **Never commit your server folder.**
