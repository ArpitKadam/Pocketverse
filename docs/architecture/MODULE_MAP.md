# Module map (revision after Phase 0)

Task 8, 2026-11-12. Revises the initial proposal in CLAUDE.md section 6 using the Phase 0 research. The initial tree stands; this document lists what changes, what is added and why. When the owner approves, the section 6 tree in `CLAUDE.md` can be replaced by the tree below. No code exists yet; everything is a proposal for Phase 1 and later.

## Revised tree

```text
Pocketverse/
├── app/                        shell: navigation, DI, theme host, process/activity declarations per engine family
├── core/
│   ├── core-model/             Game, Platform, EngineType, GameRecord (meta.json schema), CheatEntry, SaveSlot
│   ├── core-common/            result types, logging, version-tagged JSON helpers
│   ├── core-storage/           GameFolder API, path resolution (real path vs SAF), per-game folder manager
│   └── core-database/          Room: library index (rebuildable from folders), play stats
├── feature/
│   ├── feature-library/
│   ├── feature-import/         pure detection + import pipeline, explicit rejection reasons
│   ├── feature-player/         in-game screen host, control overlay, pause/menu, state slots UI
│   ├── feature-cheats/
│   └── feature-settings/
├── engine/
│   ├── engine-api/             EngineRuntime, EngineLaunchRequest, EngineResult, EngineCapabilities
│   ├── engine-libretro/        host for GBA and NDS cores (LibretroDroid candidate)
│   ├── engine-mkxp/            Android port of mkxp-z + Ruby shim pack (our own)
│   ├── engine-renpy/           Ren'Py runtime host (Android packaging approach from the official RAPT)
│   ├── engine-rpgmv/           MV/MZ WebView host (if chosen)
│   └── engine-compat/          NEW: shared compatibility data for script engines (patch list schema, path-case index, Win32/ENV shim spec)
├── cheats/
│   ├── cheats-core/            canonical model, storage, import/export (text, .cht, XML)
│   ├── cheats-gba/             decoders for raw, GameShark, GSA v3, Action Replay, Code Breaker
│   ├── cheats-nds/             Action Replay DS decoder and applier
│   └── cheats-script/          per-engine script packs (RGSS, Ren'Py, MV/MZ) and the flag/trigger protocol
├── ui/
│   ├── ui-theme-api/           ThemeMode contract (see below)
│   ├── ui-theme-gba/
│   ├── ui-theme-nds/
│   └── ui-theme-pc/
├── docs/  research/ (gitignored)  .re-workspace/ (gitignored)  tools/
```

## Changes and reasons

| # | Change | Reason (evidence) |
| --- | --- | --- |
| 1 | `engine-api` gains an explicit **launch request** (game record + config-file path) and **result** (exit reason, play time), not only run/pause/state methods | JoiPlay's contract has no result and relies on private extras; MelonDS's has no config; we want both (`docs/research/re/SYNTHESIS.md` section 1) |
| 2 | `engine-api` gains **capabilities** (supports state save/load, supports memory-write cheats, supported cheat formats, requires real file paths) | engines differ widely: libretro cores vs script engines (Tasks 3-7); features must degrade honestly, not be faked |
| 3 | New module `engine-compat` | JoiPlay's compatibility layer is data (text patches), path-case indexing, an environment shim and input overrides (Tasks 5-7). Keeping it separate lets `engine-mkxp` and `engine-renpy` share one patch-list schema, and keeps the data out of engine code |
| 4 | `engine-mkxp` and `engine-renpy` own an **engine-specific generated folder** and never write into `game/` | generated scripts and caches pollute the original game in JoiPlay (Task 6); our folder rules keep originals verifiable |
| 5 | Each engine family runs in its **own Android process** declared in `app` | native symbol and runtime conflicts between Python-, Ruby- and SDL-based engines (SYNTHESIS section 6). Validate in Phase 5 |
| 6 | `core-storage` exposes a **path resolver** with two backends: real file path (app external dir or opt-in all-files) and SAF document tree | MelonDS needs no paths, mkxp-z and Ren'Py need real ones (SYNTHESIS section 5); decided in `docs/architecture/STORAGE.md` in Phase 1 |
| 7 | `core-database` is only an **index**, rebuildable from game folders | JoiPlay and MelonDS keep game data in private databases that cannot be reconstructed or ported |
| 8 | `cheats-core` owns import/export for `.cht`, plain text and the XML `codelist`; format decoders stay in `cheats-gba` and `cheats-nds` | container formats are shared across platforms (`docs/research/CHEAT_FORMATS.md`) |
| 9 | `cheats-script` ships **script packs per engine plus a trigger protocol** (global enable flag, one key mapped to a touch button) | the F8 scene pattern in RPG Maker, the developer console in Ren'Py, the JS menu in MV/MZ (Tasks 5-7) |
| 10 | `feature-import` returns an **explicit rejection reason** for unknown folders | JoiPlay's silent rejection of a Ren'Py folder (Task 6) |
| 11 | Engines are packaged as **on-demand packs** where possible (dynamic features or asset packs) | JoiPlay plugin sizes: 79 MB (RPG Maker), 47 MB (Ren'Py) |

## Candidate dependencies (recommendations, not decisions)

- GBA: mGBA core; NDS: melonDS core (embedded via LibretroDroid or `melonDS-android-lib`, decided by a Phase 3 frame-time spike); RPG Maker XP/VX/VX Ace: mkxp-z with our own Android port; Ren'Py: official runtime with RAPT-style packaging; Flash (optional): `ruffle-rs/ruffle-android`. Licences are in `docs/licenses/COMPATIBILITY_MATRIX.md`; the app is GPLv3 (`DECISION.md`).

## `ThemeMode` contract: what it must carry (from the three UI specs)

Tokens (colour, type scale, spacing, radii); library layout variant (list, grid small/large); library chrome (drawer vs toolbar-only vs card menu); per-game screen variant (long-press menu, details screen, card menu); in-game chrome (pad skin, action row, top bar, pause menu style); settings layout (flat list, grouped screens, collapsible groups); dialog style; transition set. Adding a fourth mode must not touch any feature module (spec 9.4).

## Phase 1 inputs

Decisions to take at the start of Phase 1: STORAGE.md (default app-external library plus opt-in shared folder), `meta.json` schema version 1, Gradle module skeleton with empty READMEs, the `GameFolder` API, the process declarations. No Phase 1 work starts before the owner has reviewed Phase 0.
