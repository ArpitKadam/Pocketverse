# Synthesis: what the five apps teach us

Task 8, 2026-11-12. This is design-level. It draws only on the five RE notes (`john-gba-lite.md`, `melonds.md`, `joiplay.md`, `joiplay-renpy-plugin.md`, `joiplay-rpgm-plugin.md`) and the Task 2 comparison. Nothing here is copied from any app; every recommendation is our own design. Evidence labels are those of the notes (observed, source, inferred). Open items are collected at the end.

## 1. What a good launcher-to-engine contract looks like

Three real contracts were observed:

| App | Contract | Strength | Weakness |
| --- | --- | --- | --- |
| MelonDS | One exported activity, one action (`LAUNCH_ROM`), one path-or-URI extra, plus a "boot firmware only" flag (source) | tiny, self-describing, shortcut-friendly | no per-game config travels with it; the engine looks it up in its own private database |
| JoiPlay to plugins | explicit component to a `PermissionActivity`, two JSON string extras (`game`, `settings`), plugin forwards extras to its engine activity; engine config also written to a file (`mkxp.json`) or passed as environment variables (Ren'Py) (observed + public source) | one shape for every engine; settings travel with the launch | needs all-files access in every plugin; fixed public folders; no return value; extras are an undocumented private protocol |
| John GBA Lite | none: monolith, zip `VIEW` filter only | nothing to maintain | cannot be driven by another app |

**Recommendation for Pocketverse:**

1. Engines are in our own APK, so there is no cross-app contract to publish. Define the contract as a Kotlin interface (`engine-api`) and, where an engine must run in its own process, as one explicit internal intent whose extras are (a) a `game` record (id, title, engine type, absolute game-folder path, launch file) and (b) a path to a single canonical config file. Keep intent extras small and stable; put everything else in the file.
2. The config file is **re-read on every launch** and written by the launcher from the per-game `meta.json` merged over global settings. This is JoiPlay's `mkxp.json` pattern with a clean owner (the launcher) and one schema (ours), not per-engine dialects.
3. Add what none of them has: a **result**. The engine reports an exit reason (normal exit, crash, user-quit, needs-permission) and writes a short `last-run.json` (duration, exit reason). This drives play-time stats, crash recovery and the autostate rule from the spec.
4. Expose a launch action for external callers (shortcuts, launchers like Daijisho/ES-DE, which MelonDS already supports). One action, one `uri` extra, validated. Never accept raw paths from outside the app without checking they resolve inside a library folder.

## 2. What a good per-game folder looks like

Observed layouts:

- **John GBA Lite:** flat folders per type (`game/ save/ state/ autostate/ cheat/`), files keyed by the ROM's base file name. Two ROMs with the same name collide. Thumbnails sit beside states (`.js0`, `.jsa`).
- **MelonDS:** saves and states next to the ROM by default (`<name>.sav`, `<name>.ml0`..`.ml8`), state screenshots hidden in private storage (not portable), cheats in a private database.
- **JoiPlay:** `games/<id>/` holding the full extracted game plus tool files (`logs.txt`, `.online_path_cache`, `.nosearch`, `UserData/{AppData,Temp}`, optional `configuration.json`), saves for Ren'Py in `<game>/saves`. Game list in a private database.

**Design rules for ours** (the spec's section 7.1 layout stands; these refine it):

1. One self-contained folder per game keyed by title plus a content-hash short id. No file-name collisions, copyable between phones.
2. Keep the original game files untouched in `game/`. Anything the runtime generates (compiled scripts, path caches, logs) goes into a separate `engine/` subfolder, not into `game/`. JoiPlay and Ren'Py both write generated files into the game folder (`script.rpyc`, `cache/`); we want to be able to re-verify or re-import the original.
3. `save/` holds whatever the engine considers its save data: raw `.sav` for cartridges, `UserData/AppData` equivalents for RPG Maker (via the engine's data-path setting), `saves/` for Ren'Py.
4. State thumbnails live **inside** the folder, next to each state. MelonDS stores them privately and loses them on copy.
5. `meta.json` carries engine type, hashes, per-game settings and the chosen UI mode. Everything the launcher knows about a game must be reconstructible from the folder alone: no private-database-only data (JoiPlay's game list is lost without a backup).
6. Add a marker file so media scanners ignore the folder (all three apps do this).

## 3. Save, state and cheat data we may need to migrate (spec section 7.4)

| Source | Item | Format (observed) | Migration verdict |
| --- | --- | --- | --- |
| John GBA Lite | battery save `.sav` | raw 128 KiB cartridge save | trivially portable |
| John GBA Lite | state `.jg0`, autostate `.jga` | gzip of a VBA-style save state, version 10, 740,238 bytes, title+code at start | readable only by a VBA-compatible loader; whether mGBA's state loader accepts it is **unverified** (check in Phase 3). Otherwise offer "import the battery save only" |
| John GBA Lite | thumbnails `.js0`/`.jsa` | JPEG | usable as is |
| MelonDS | battery save `.sav` | raw cartridge dump (512 KiB for the owner's HeartGold hack) | trivially portable |
| MelonDS | state `.ml0`..`.ml8` | uncompressed `MELN` container, version 13.0 in the owner's file; the current core is 14 | the state is bound to the core version; import only if our core accepts the major version, else offer save-only import and say why |
| MelonDS | cheats | private database; XML import format exists | import via the XML route if the owner exports; no direct extraction on a non-rooted phone |
| JoiPlay | RPG Maker saves | Ruby Marshal dumps (`File A.rxdata`) in the engine's data dir (`UserData/AppData`, not confirmed) | portable if we run the same engine family and keep the same data-path layout; confirm with one real save in Phase 5 |
| JoiPlay | Ren'Py saves | Ren'Py's own save files in `<game>/saves` | portable between Ren'Py runtimes of the same major version; confirm per game |
| JoiPlay | game list, per-game settings | private database, partly `configuration.json` in the game folder | import only the per-game `configuration.json` |

Rule restated: always copy, never modify originals.

## 4. Cheats across the three game types

- **GBA** (John GBA Lite UI): code types offered are Raw, GameShark, GameShark v3 and Code Breaker, with auto-detection ("Unspecified"). No separate Pro Action Replay type. The native names (VBA family: `cheatsAddGSACode`, `cheatsAddCBACode`, CBA decryption, GSA v1 decryption) tell us which algorithms exist. The full format reference is `docs/research/CHEAT_FORMATS.md`.
- **NDS** (MelonDS): Action Replay DS codes only, applied by the core's AR engine; per-game cheat folders; XML databases import. See `CHEAT_FORMATS.md`.
- **`.exe`-style games** (JoiPlay): no code-entry equivalent. Cheats are **scripts injected into the running game**: an F8-triggered in-game scene for RPG Maker (XP, VX, VX Ace, Pokémon Essentials before and after v19), a JavaScript menu for MV/MZ, the developer console for Ren'Py. All switched by one global flag.

**Recommendation:** `cheats-script` ships per-engine script packs written by us, with the same three-part structure (script pack, enable flag read at start, one trigger mapped to a touch button) plus what JoiPlay lacks: named, saved, toggleable and freezable entries stored in the game's `cheats/` folder (spec 8.1), not fixed menus.

## 5. Storage decision input (for `docs/architecture/STORAGE.md`, Phase 1)

- JoiPlay and all its plugins hold `MANAGE_EXTERNAL_STORAGE` and share fixed public folders (`/sdcard/JoiPlay/...`). That is the simplest route for engines that need real file paths, and it costs a system permission dialog flow in every component (observed twice).
- MelonDS uses the Storage Access Framework only (no storage permission), with an app-folder fallback; it never needs real paths because its core reads through file descriptors.
- John GBA Lite runs in "scoped storage mode" and keeps a public shared folder.
- mkxp-z and Ren'Py need real paths (case-insensitive lookups, directory listing, `Dir.pwd`).

**Recommendation:** keep the library in the app's own external directory (`Android/data/<pkg>/files/Pocketverse/`) as the default, which gives real paths without any permission, and import by copying from a SAF-selected source. Offer an opt-in "library folder in shared storage" mode that requests all-files access for users who want to see and edit files with other apps. Decide and record in Phase 1; the spec's options (a) and (b) as default, (c) as opt-in.

## 6. Process and packaging model (new, from the plugin studies)

- Two SDL-based engines that each carry their own SDL and their own embedded language runtime (Ren'Py: SDL 2.0.20 and Python, observed; mkxp: SDL version not checked, Ruby) are unlikely to live happily in one process (inferred; native symbol clashes are a known hazard). JoiPlay avoids this by shipping each engine in its own APK. We want one APK. **Recommendation:** run each engine family in its own Android process (`android:process=":mkxp"`, `":renpy"`, `":libretro"`) with its own activity, all inside our APK. This isolates crashes and native-symbol conflicts. To be validated in Phase 5; if it fails, fall back to shipping engine packs as separate installable modules.
- Size: the JoiPlay RPG Maker plugin is 79 MB (three engine variants) and the Ren'Py plugin 47 MB (two). Plan for on-demand engine packs (dynamic feature modules or asset packs) so users install only what they use.
- Compatibility work is **data plus hooks**: a shared list of source-text replacements per engine, a path-case index, a small Windows-environment shim, an input-method override. Build these as data-driven features of `engine-mkxp`/`engine-renpy`, owned by us.

## 7. UI synthesis (details in the three specs)

All three reference apps share one skeleton, which is what the `ThemeMode` contract should capture:

| Element | GBA (John) | NDS (MelonDS) | PC (JoiPlay) |
| --- | --- | --- | --- |
| Library | single list, drawer for Favorites/History/All, rows with thumbnail | single list with Search/Sort/overflow, rows with banner icon | card grid with three layouts (small, large, list), filter dialog |
| Per-game screen | long-press menu only | dedicated details screen with PLAY and per-game settings | card menu (patch, export, edit, delete, settings...) |
| In-game | pad drawn inside the game view; action row with quick save/load/fast-forward/menu | transparent control layer over the surface; Back opens a Pause list | Android-view pad (keyboard-style keys for RPG Maker); collapsible top bar |
| Settings | flat preference list | grouped preference screens | one screen of collapsible groups with a global or per-game switch |
| Look | orange accent on dark grey | dark with red accent | near-black with grey controls, user primary colour |

Shared interaction rules we will implement once and render per mode: quick save/load slot plus numbered slots, fast-forward, cheat access from the in-game menu, per-game overrides on global settings, physical controller and keyboard as first-class input.

## 8. What the apps do that we deliberately avoid

1. Silent rejection (JoiPlay dropped an unrecognised Ren'Py folder with no message). We explain why, per spec 7.2.
2. Ads, upsell items, billing prompts, donation builds: not reproduced.
3. Public shared protocol folders and universal all-files access by default.
4. Saves keyed only by file name (collision-prone), thumbnails in private storage, and settings only in a private database.
5. Engine-generated files mixed into the original game folder.
6. States silently tied to one core version with no import path (we show the version and report incompatibility).
7. A proprietary component in the engine bundle (Live2D) without a licence.

## 9. Open questions carried forward

1. Whether mGBA loads John GBA Lite's VBA-style states (Phase 3 spike).
2. Whether a MelonDS state of version 13 loads in the installed 2.0.1 core and in the core we ship.
3. Where Pokémon Essentials saves land under the plugin's data path (needs one real in-game save in Phase 5).
4. The exported `GameProvider` in John GBA Lite and the `gamedb.zip` in JoiPlay: purpose unknown, not required.
5. Landscape captures for John GBA Lite and MelonDS, and the DS screen arrangement modes (source-read only).
6. Which of the three mkxp engine builds JoiPlay uses for each choice.
7. Process-per-engine model on real hardware (Phase 5).
8. Whether to offer GPL-3.0-only or GPL-3.0-or-later for our own files (owner decision recorded as open in `docs/licenses/DECISION.md`).
