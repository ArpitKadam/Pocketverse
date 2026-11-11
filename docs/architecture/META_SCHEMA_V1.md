# `meta.json` schema, version 1

Phase 1, task P1-1, 2026-11-12. The file that makes a game folder self-describing and portable (spec section 7.1). Machine-readable form: `META_SCHEMA_V1.json` (JSON Schema draft 2020-12). `core-storage` reads and writes it; the library index is rebuilt from these files.

## Rules

1. UTF-8, no BOM, a single JSON object, pretty-printed with two-space indent for diffability. Keys are camelCase.
2. `schemaVersion` is an integer, currently `1`. A reader must refuse a higher version with a clear message ("created by a newer Pocketverse") and must preserve unknown keys it does not understand when rewriting an equal-or-lower version file.
3. Timestamps are ISO-8601 in UTC with a `Z` suffix.
4. All paths inside the file are **relative to the game folder** and use `/`. No absolute paths, no device names, no user data (privacy: the file travels between phones).
5. Writes are atomic (STORAGE.md section 7).

## Fields

| Key | Type | Required | Meaning |
| --- | --- | --- | --- |
| `schemaVersion` | integer | yes | `1` |
| `id` | string | yes | short id: first 8 (or 12 after a collision) hex chars of `hash.sha256` |
| `title` | string | yes | display title (user-editable; initial value from the ROM header or folder name) |
| `platform` | string | yes | one of `gba`, `nds`, `rpgmaker`, `renpy`, `rpgmaker-mv` (extensible by adding values) |
| `engine` | object | yes | `{ "id": "libretro-mgba" \| "libretro-melonds" \| "mkxp-z" \| "renpy" \| "rpgmv" ..., "variant": optional string, e.g. `"renpy-8"` or `"renpy-7"` }` |
| `launch` | object | yes | `{ "file": "<path under game/>" }` for folder games (e.g. `Game.ini` for RPG Maker, the launcher `.py` for Ren'Py); for cartridge games the ROM file name under `game/` |
| `hash` | object | yes | `{ "sha256": "<64 hex>", "method": "rom" \| "manifest", "crc32": optional 8 hex (cartridge only) }` |
| `rom` | object | cartridge games | `{ "gameCode": "AAAA", "makerCode": optional, "headerTitle": string, "region": optional, "sizeBytes": integer, "unitCode": optional for NDS, "dsiEnhanced": optional boolean }` |
| `import` | object | yes | `{ "importedAt": timestamp, "originalName": string, "sourceKind": "file" \| "folder" \| "archive" \| "package", "appVersion": string }`. No source path is stored |
| `ui` | object | no | `{ "mode": "gba" \| "nds" \| "pc" \| "default", "orientation": optional "auto" \| "portrait" \| "landscape" }` per-game override of the UI mode (spec 9.4) |
| `settings` | object | no | per-game engine and app setting overrides, a map from setting key to `{ "boolean": b }` or `{ "string": s }` or `{ "number": n }`. The envelope mirrors the typed-value shape JoiPlay uses, chosen so a value's type is never guessed |
| `controls` | object | no | `{ "layouts": { "<orientation>": "<layout id>" } }` only a reference; layouts themselves live in the app, per spec 9.4 they are saved per game and per orientation (stored under `engine/` until the layout editor exists) |
| `stats` | object | no | `{ "playTimeSeconds": integer, "lastPlayedAt": timestamp, "launchCount": integer }` |
| `art` | object | no | `{ "cover": "art/cover.png" }` |
| `x` | object | no | free extension map reserved for engines and future features; readers keep it untouched |

## Example (cartridge)

```json
{
  "schemaVersion": 1,
  "id": "3fa94c1e",
  "title": "Example GBA Game",
  "platform": "gba",
  "engine": { "id": "libretro-mgba" },
  "launch": { "file": "example.gba" },
  "hash": { "sha256": "3fa94c1e...", "method": "rom", "crc32": "A1B2C3D4" },
  "rom": { "gameCode": "AXXE", "headerTitle": "EXAMPLE", "sizeBytes": 16777216 },
  "import": { "importedAt": "2026-11-12T10:00:00Z", "originalName": "example.gba", "sourceKind": "file", "appVersion": "0.1.0" }
}
```

## Example (folder game)

```json
{
  "schemaVersion": 1,
  "id": "9b02d7aa",
  "title": "Example RPG",
  "platform": "rpgmaker",
  "engine": { "id": "mkxp-z" },
  "launch": { "file": "Game.ini" },
  "hash": { "sha256": "9b02d7aa...", "method": "manifest" },
  "import": { "importedAt": "2026-11-12T10:05:00Z", "originalName": "Example RPG.zip", "sourceKind": "archive", "appVersion": "0.1.0" },
  "settings": { "cheats": { "boolean": false }, "rpg.fontScale": { "number": 0.75 } }
}
```

## Compatibility notes

- Migrating from JoiPlay: its per-game `configuration.json` (for example `{"cheats": false}`) maps into `settings`.
- A reader that does not know an engine id still lists the game and shows it as "engine not installed", it does not delete or rewrite anything.
