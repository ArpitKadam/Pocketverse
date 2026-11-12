# Storage architecture

Phase 1, task P1-1, 2026-11-12. Decides where the Pocketverse library lives, how engines get real file paths, and what a game folder contains. Evidence is from Phase 0: `docs/research/re/SYNTHESIS.md` sections 2 and 5, and the five RE notes. This document is the source of truth for `core-storage`.

## 1. Constraints

1. mkxp-z and Ren'Py need **real filesystem paths** (directory listing, case-insensitive lookups, writing next to the game). libretro cores and melonDS can read through file descriptors, but real paths are simpler and we want one model.
2. Android 11+ scoped storage blocks arbitrary paths. Three ways around it were seen in the wild:
   - JoiPlay and every plugin hold `MANAGE_EXTERNAL_STORAGE` and share fixed public folders. Works everywhere; costs a system permission dialog flow per component (observed twice).
   - MelonDS uses the Storage Access Framework only and an app-folder fallback; it never needs real paths.
   - John GBA Lite keeps a public shared folder and calls its mode "scoped storage".
3. Android's own app-specific external directory (`Android/data/<package>/files/`) gives real paths and needs no permission, but is **deleted on uninstall** and is **not browsable by other apps or the file manager on Android 11+** (only over adb or the app itself).
4. The spec requires every game folder to be **portable** (copy it to another phone and the game, saves, states and cheats come back) and to be reconstructible without any private database.
5. The Android filesystem is case-sensitive; Windows games are not (JoiPlay solves this with a lowercase path index, which belongs in `engine-compat`, not here).

## 2. Options evaluated

| Option | Real paths | Permission | Other apps and users can see files | Survives uninstall | Verdict |
| --- | --- | --- | --- | --- | --- |
| (a) App-specific external dir | yes | none | no (Android 11+) | no | **default** |
| (b) User-picked SAF tree | no (document URIs) | none, per-tree grant | yes | yes | import source and export target only; copy-in staging for engines that need paths |
| (c) `MANAGE_EXTERNAL_STORAGE` public folder | yes | all-files access | yes | yes | **opt-in** "shared library" mode |

## 3. Decision

1. **Default library root: the app-specific external directory**, `getExternalFilesDir(null)/Pocketverse/`. No permission, real paths, works on first launch.
2. **Opt-in shared library: `/storage/emulated/0/Pocketverse/`** after the user grants all-files access in the app's own settings. The app offers a one-tap **move library** that copies and verifies each game folder before deleting the source folder. The permission is requested only when the user turns the mode on, never at first launch, and never for engines individually (we run in one APK, so there is one permission, not one per plugin).
3. **SAF is used only to import** (user picks a game file or folder, we copy it in) and to **export or back up** (user picks a destination tree and we write a package archive there). Engines never receive SAF URIs.
4. **Fallback:** if the shared library is selected but all-files access is revoked, the app shows a clear banner and falls back to the app-specific directory read-only for existing games until access is restored; it never silently creates a second library.
5. **Uninstall risk is handled in the UI, not hidden:** first launch states that the default library is removed with the app, and offers "back up now" and "switch to shared library". A "last backup" date is shown in settings.
6. The library root is a `LibraryLocation` value stored in app settings: `AppExternal` or `SharedFolder`. `core-storage` resolves every game to an absolute `java.io.File` through it. No feature module builds a path by hand.
7. The **library index** (Room, internal storage) is a cache. Deleting it loses nothing: it is rebuilt by scanning the library root for `meta.json` files (task P1-4).
8. The manifest declares `MANAGE_EXTERNAL_STORAGE` for the opt-in mode. Play Store policy on this permission is strict; if the app is ever listed there, build a second flavor without it (the shared-library mode then simply does not appear). Recorded as an open item, not needed before a public release.

Why not make the shared folder the default: it forces a scary permission before the user has seen the app, it is the thing JoiPlay users had to fight through on every plugin, and the app-specific directory already gives engines everything they need.

## 4. Library layout

```text
<library root>/                       .nomedia (so gallery apps skip it)
├── <Game Title> [<short-id>]/        one self-contained folder per game
│   ├── game/                         the original ROM(s) or the full game folder; never modified by engines
│   ├── save/                         engine-native save data
│   ├── state/                        manual save states and thumbnails
│   ├── autostate/                    automatic resume state and thumbnail
│   ├── cheats/                       cheats.json plus untouched imported originals
│   ├── engine/                       everything an engine generates (compiled scripts, path caches, logs, shader caches)
│   ├── art/                          cover image and extracted icon
│   └── meta.json                     see `META_SCHEMA_V1.md`
├── .index/ (optional)                nothing in Phase 1; reserved for caches that may be rebuilt
```

Changes against the spec's section 7.1: two extra folders, `engine/` and `art/`.

- **`engine/`** exists because JoiPlay and Ren'Py write generated files (compiled scripts, caches, logs, path indexes) into the game folder, which pollutes the original. Ours keeps `game/` byte-identical to what was imported, so it can be re-verified and re-imported.
- **`art/`** holds the cover shown in the library (a ROM banner icon extracted at import, or a user-chosen image), so the library does not depend on any engine being installed.

## 5. Naming rules

- **Folder name:** `<sanitised title> [<short-id>]`. Sanitising: replace `/ \ : * ? " < > |` and control characters with a space, collapse whitespace, trim spaces and dots at the ends, cut the title to 80 characters on a code-point boundary, and avoid Windows reserved names (`CON`, `NUL`, `COM1`...) by appending an underscore, so the folder stays copyable to a PC.
- **`short-id`:** the first 8 hex characters of the content hash (see section 6). On a collision with an existing folder that has a different full hash, extend to 12 characters.
- **Inside `game/`:** the original file name, unchanged (for a folder-style game, the original relative tree).
- **Cartridge saves:** `save/<ROM base name>.sav`, the same raw format other emulators use, so a save can be taken out unchanged.
- **States:** `state/quick.state`, `state/slot1.state` ... with a PNG thumbnail of the same base name (`quick.png`). `autostate/auto.state` plus `auto.png`. The `.state` payload is engine-defined and starts with a Pocketverse header (engine id, engine version, content hash) so an incompatible state is reported, not guessed.
- **Cheats:** `cheats/cheats.json` is canonical; `cheats/originals/` keeps every imported file untouched.

## 6. Content hash (identity)

- **Cartridge games (GBA, NDS):** SHA-256 of the ROM bytes. Also record CRC-32 and the header game code in `meta.json` for cheat-database lookup.
- **Folder-style games (RPG Maker, Ren'Py, MV/MZ):** SHA-256 of a canonical manifest: each regular file under the game root as `<lowercase relative path>\0<size>\n`, sorted by path, excluding the engine-generated paths we know (`logs.txt`, `UserData/`, `saves/`, `cache/`, `.online_path_cache`). This is cheap (no file reads) and stable across copies. The tradeoff: two folders with the same tree and sizes but different bytes hash the same; acceptable for an identity id, not used for integrity.

## 7. Rules `core-storage` enforces

1. Nothing is written outside the game's own folder except app-wide settings and the library index.
2. Every write of `meta.json` and `cheats.json` is **atomic**: write to a temporary file in the same folder, flush, rename over the target.
3. `game/` is created at import and treated as read-only by engines; engines get `engine/` and `save/` as their writable areas.
4. Import copies into a temporary sibling folder and renames it into place on success; a crash leaves no half-imported game (the temporary folder name starts with `.importing-` and is removed on the next start).
5. Deleting a game deletes its folder after a confirmation. "Remove from library only" was dropped: the index is rebuilt from the folders, so a kept folder would reappear on the next scan. Optional export of `save/` before deleting is still to do.
6. Free-space check before an import (ROM size or archive uncompressed size plus a margin), with a clear message when short. JoiPlay needed over 3 GB and about 11 minutes for one game; progress and cancellation are required.
7. All path-building goes through one `GameFolder` type; there are no string concatenations of paths elsewhere.

## 8. Android specifics

- Library root creation: `getExternalFilesDir(null)` can return null when external storage is unavailable; fall back to an error state with a retry, not to internal storage silently (engines and size limits differ).
- `.nomedia` in the library root and in `art/` so media scanners ignore game assets.
- Direct boot and multi-user: the app-specific path already includes the user id; no extra handling.
- Auto Backup and device-to-device transfer: exclude the library from Android's backup rules (large, and engines write generated files); our own package export is the supported route.

## 9. Open items

1. Play Store flavor without all-files access (decide when a store release is planned).
2. Whether to keep thumbnails inside `state/` (chosen) or generate them on demand from the state (rejected: engine-specific and slow).
3. Move-library implementation details and progress UI (P1-3, P1-5).
4. SAF export format: a zip of the game folder named `<folder name>.pvgame.zip` with `meta.json` at the root. The importer for it belongs to Phase 2.
