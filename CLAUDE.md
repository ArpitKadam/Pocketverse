# CLAUDE.md — Pocketverse: Android All-in-One Game Hub (.gba / .nds / .exe)

> App name: **Pocketverse**.
> Before any public release, check name availability (Play Store, F-Droid, GitHub) and run a trademark search.
> Owner: Arpit. Style: modular, production-quality, iterative, heavily documented.

---

## 1. Mission

Build a single Android app that replaces this daily setup:

| Today (installed on the owner's phone)        | Handles                         |
|-----------------------------------------------|---------------------------------|
| John GBA Lite                                 | `.gba` games                    |
| MelonDS                                       | `.nds` games                    |
| JoiPlay                                       | `.exe` games (RPG Maker / Ren'Py fan games) |
| JoiPlay RPG Maker plugin                      | RPG Maker XP / VX / VX Ace / MV |
| JoiPlay Ren'Py plugin                         | Ren'Py visual novels            |

Primary use case: Pokémon games and fan games (including Pokémon Essentials-based RPG Maker games).

Goals, in priority order:
1. One library, one import flow, one save/state/cheat system for all three formats.
2. A per-game self-organising folder structure (Section 7).
3. A cheat system that works on every format (Section 8).
4. A UI with three switchable modes that reproduce the look, layout and feel of John GBA Lite, MelonDS and JoiPlay (Section 9), so it feels instantly familiar and nostalgic.
5. Everything offline-first. No accounts, no network needed to play.

---

## 2. Non-negotiable rules

### 2.1 Legal / licensing
- **Never copy proprietary code or binaries** from John GBA Lite, JoiPlay or its plugins into this project. That includes decompiled source, `.dex`, `.so`, resources and assets. Closed-source apps are studied for *behaviour*, never lifted.
- The **owner's game files and BIOS files are theirs**. The app bundles **no ROMs, no games, no commercial BIOS**. Use open-source replacement BIOS where available. Users import their own files.
- Every third-party dependency must be recorded in `docs/licenses/THIRD_PARTY.md` with: name, version/commit, license (SPDX id), source URL, how it's linked (static / dynamic / separate process).
- **Check licence compatibility before vendoring anything.** Watch for GPLv2-only vs GPLv3 conflicts, and for MPL/LGPL linking rules. Decide and document the app's own licence in `docs/licenses/DECISION.md` *before* integrating GPL code.
- Do not bypass ads, billing, licence checks, signature checks or DRM in any app. Do not repackage or redistribute anyone's APK.
- **UI design:** copying layout, flow and interaction design is fine; copying artwork is not. Never ship another app's icons, logos, bitmaps, skins, screenshots or non-free fonts, and never use Nintendo / Pokémon / Game Boy / DS names, logos or boot sounds. Draw every asset originally (Section 9.2).

### 2.2 Safety with the device
- adb work is on the **owner's own phone only**. Before any command that could change or delete data (`pm clear`, `pm uninstall`, `rm`, `settings put`, anything with `su`), **stop and ask**. Back up first (`adb backup` or `adb pull` into `.re-workspace/backups/`).
- Never uninstall or clear the existing emulator apps. The owner's saves live there.
- Prefer read-only observation: `pm list`, `dumpsys package`, `logcat`, `adb pull` of the app's own APK for analysis.

### 2.3 Honesty
- If something can't be done (or can only be done partly), **say so and document the gap** in `docs/KNOWN_LIMITATIONS.md`. Do not fake support to hit a checklist.
- Verify claims about formats, opcodes and licences against primary sources (upstream repos, docs) rather than memory. Record the source in the doc you write.

---

## 3. Working agreements

- **Language/stack (default, change only with a documented reason):** Kotlin + Jetpack Compose for UI, Gradle multi-module, Room for the library DB, Coroutines/Flow, NDK + CMake for native cores, JNI bridges kept thin. Primary ABI `arm64-v8a`; add `armeabi-v7a` later if needed.
- **Modular and organised.** Clear subpackages, one responsibility per module, **no loose files** at module or repo root beyond build files and docs. Public API of each module lives in a small `api` package; everything else is `internal`.
- **Documentation ships with code.** Every module has a `README.md`. The repo has a top-level `README.md` and an `EXPLANATION.md` that explains the architecture and *why* decisions were made, written so the owner can follow it end-to-end. Update docs in the same change as the code.
- **Iterative.** Work in small phases (Section 4). After each phase: summarise what was done, what was learnt, what's next, and wait for the owner's review. The owner often shares revised code and wants to discuss implementation details, so keep diffs reviewable and explain trade-offs.
- **Tests.** Unit tests for parsers, detectors, cheat decoders and path logic (these are pure Kotlin and easy to test). Instrumented tests for import and save/state round-trips.
- Prefer small, composable interfaces over frameworks. No premature abstractions, but engine and theme boundaries are deliberate (Sections 6 and 9).

---

## 4. Phase plan

| Phase | Name | Output |
|-------|------|--------|
| 0 | Research, reverse engineering and UI reference capture (understand) | `docs/research/` and `docs/design/` complete |
| 1 | Project skeleton + storage + library DB | Builds, empty library, folder layout creation |
| 2 | Import pipeline + format detection | Import a `.gba` / `.nds` / game folder into its own folder |
| 3 | Embedded GBA + NDS emulation | Play, save, state, autostate |
| 4 | Cheat engine for GBA + NDS | All three code formats working |
| 5 | `.exe`-game engines | RPG Maker (mkxp-z based), Ren'Py, + engine cheat backends |
| 6 | Theme/UI modes + polish | Three UI modes matching the reference specs, settings, migration of old saves |

**Do not start Phase 1 until Phase 0 deliverables are reviewed by the owner.**

---

## 5. Phase 0 — Research and reverse engineering

All Phase 0 output goes in `docs/research/`. Raw working material goes in `.re-workspace/` (**gitignored**, never committed).

### 5.1 Download and evaluate open-source alternatives

Shallow-clone candidate projects into `research/sources/` (gitignored). For each, record: repo URL, commit hash, licence, last-commit date, Android support status, build system, how cores/engines are separated from UI, and notable strengths/weaknesses. **Also look for better or newer alternatives than the ones listed**; the list is a starting point, not the answer.

Starting candidates:

- **Frontends / all-in-one:** Lemuroid (libretro-based, Android), RetroArch (Android), Daijishō / Pegasus (launcher design only).
- **GBA cores:** mGBA, gpSP (libretro fork), VBA-M, others you find. Compare accuracy, speed on mobile, cheat API, save-state format, licence.
- **NDS cores:** melonDS (core + melonDS-Android fork), NooDS, DeSmuME. Compare as above, plus 3D renderer options and DS-specific cheat support.
- **RPG Maker XP / VX / VX Ace:** mkxp-z, android-mkxp, mkxp forks. Highest priority, since this is what Pokémon Essentials fan games need.
- **RPG Maker 2000/2003:** EasyRPG Player.
- **RPG Maker MV/MZ:** WebView / NW.js-style approaches; look for existing Android wrappers.
- **Ren'Py:** the official Ren'Py Android packaging (RAPT) and how apps embed it.
- **Others worth a look:** Ruffle (Flash), Godot runtimes, anything newer that unifies several of these.
- **Prior art:** search GitHub/F-Droid/forums for apps that already combine console emulation with RPG Maker/Ren'Py. Document what exists, how far they got, and what they chose not to do.

Deliverable: `docs/research/OPEN_SOURCE_ALTERNATIVES.md` with a comparison matrix and a recommendation per format (with a runner-up), plus `docs/licenses/COMPATIBILITY_MATRIX.md`.

### 5.2 Reverse-engineer the 5 installed apps (to understand, not to copy)

Use `adb` and the **REA MCP** (reverse-engineering MCP server). Run `/mcp` first to see which tools are actually available (e.g. a Ghidra- or jadx-style analyser) and note them in `docs/research/re/TOOLING.md`. If a tool isn't available, say so and fall back to adb + standard CLI tools.

Apps to study:

1. John GBA Lite
2. MelonDS (installed Android build)
3. JoiPlay (main app)
4. JoiPlay Ren'Py plugin
5. JoiPlay RPG Maker plugin

For **each** app, produce `docs/research/re/<app>.md` in the **researcher's own words** covering:

- Package name, version, ABIs, permissions (from the manifest / `dumpsys package`).
- Declared components: activities, services, intent filters, exported surfaces. For JoiPlay + plugins, **how the main app hands a game to a plugin** (intent actions, extras, URIs, file-path contract, return values).
- On-disk layout the app creates (data dirs, save locations, state locations, config files), and what formats they appear to use (e.g. raw `.sav`, state container structure).
- How the app finds and identifies games and how it lays out per-game configuration.
- How cheats are stored and applied (file format, UI flow) in the apps that support them.
- Rendering/input/audio pipeline at the architecture level (SurfaceView vs GLSurfaceView, SDL, native threads, etc.).
- For plugins: which upstream open-source engine they appear to embed, what compatibility "shims" or override scripts they add, and what environment/config knobs they expose. Map each observation to the upstream project that is the clean reference for it.
- Open questions and things that could not be determined.

Allowed methods: reading the manifest and resources, `dumpsys`, `logcat`, observing file I/O and directory changes while using the app normally, listing native-library names and exported JNI symbols, launching exported intents, and reading strings for architectural clues.

Not allowed: copying decompiled code, extracting or committing proprietary `.so` / `.dex` / assets, defeating ads/billing/licensing, patching or repackaging an APK.

Where an app is open source (MelonDS), read the **upstream source** as the primary reference and use the installed build only to confirm behaviour.

In the same pass, capture the **UI reference** for John GBA Lite, MelonDS and JoiPlay as described in Section 9.3.

Deliverable: the five per-app notes plus `docs/research/re/SYNTHESIS.md`. This is a design-level summary ("what a good launcher ↔ engine contract looks like", "what a good per-game folder looks like") that informs *our own* original design.

### 5.3 Phase 0 exit checklist
- [ ] Open-source comparison matrix + recommendations
- [ ] Licence compatibility matrix + proposed app licence
- [ ] Five RE notes + synthesis
- [ ] Three UI reference specs (`docs/design/UI_SPEC_*.md`), each reviewed by the owner
- [ ] Cheat-format research (Section 8.1) done
- [ ] Proposed module map (Section 6) revised if research demands it
- [ ] Owner has reviewed

---

## 6. Architecture (initial proposal — revise after Phase 0)

```
Pocketverse/
├── app/                        # thin shell: navigation, DI, theme switch host
├── core/
│   ├── core-model/             # Game, Platform, CheatEntry, SaveSlot types
│   ├── core-common/            # utils, result types, logging
│   ├── core-storage/           # per-game folder manager (Section 7)
│   └── core-database/          # Room: library, play stats
├── feature/
│   ├── feature-library/
│   ├── feature-import/         # detection + import pipeline
│   ├── feature-player/         # in-game screen, controls overlay, menus
│   ├── feature-cheats/         # cheat manager UI
│   └── feature-settings/
├── engine/
│   ├── engine-api/             # EngineRuntime interface (load, run, pause, save/load state, memory access, cheats hook)
│   ├── engine-libretro/        # JNI host for libretro cores (GBA, NDS)
│   ├── engine-mkxp/            # RPG Maker XP/VX/VX Ace
│   ├── engine-renpy/           # Ren'Py
│   └── engine-rpgmv/           # RPG Maker MV/MZ (WebView-based), if chosen
├── cheats/
│   ├── cheats-core/            # canonical cheat model, storage, orchestration
│   ├── cheats-gba/             # GameShark / Action Replay / Code Breaker for GBA
│   ├── cheats-nds/             # Action Replay (+ other DS formats found in research)
│   └── cheats-script/          # engine-native cheats for .exe-style games
├── ui/
│   ├── ui-theme-api/           # ThemeMode contract
│   ├── ui-theme-gba/
│   ├── ui-theme-nds/
│   └── ui-theme-pc/
├── docs/                       # research/, design/, licenses/, architecture/, KNOWN_LIMITATIONS.md
├── research/                   # (gitignored) cloned sources
├── .re-workspace/              # (gitignored) RE scratch space
└── tools/                      # dev scripts, each documented
```

Key rule: **features talk to engines only through `engine-api`.** Adding a new engine (e.g. Godot, Ruffle) must not touch library, cheats UI or storage code.

Android storage constraint to resolve in Phase 1 (document the decision in `docs/architecture/STORAGE.md`): mkxp/Ren'Py-style runtimes need real filesystem paths, while Android 11+ scoped storage restricts them. Evaluate (a) app-specific external dir, (b) user-chosen SAF tree with copy-in/staging, (c) `MANAGE_EXTERNAL_STORAGE` (note: JoiPlay's plugin changelog shows it eventually needed this). Pick one with reasons and a fallback.

---

## 7. Game library and per-game filesystem

When a game is imported, the app **identifies it on its own** and creates an independent folder for it. The user shouldn't have to choose a platform or arrange files.

### 7.1 Layout

```
Pocketverse/                            # main folder (location per STORAGE.md)
├── <Game Title> [<short-id>]/          # one folder per imported game, fully self-contained
│   ├── game/                           # the ROM or the full game folder (.exe-style games)
│   ├── save/                           # in-game saves (battery saves, engine save files)
│   ├── state/                          # manual save-state slots
│   ├── autostate/                      # automatic resume state (on exit / pause / crash recovery)
│   ├── cheats/                         # this game's cheat files (canonical + imported originals)
│   └── meta.json                       # platform, engine, title, ids, hashes, import date, per-game settings
└── (app-wide: library DB, themes, logs live outside game folders)
```

- Each game folder must be **portable**: copying it to another phone with the app should restore the game, saves, states and cheats.
- `<short-id>` comes from a content hash so two versions/hacks of the same title never collide.
- Never write outside the game's own folder except app-wide config/DB.
- Deleting a game offers: remove from library only / delete folder (with confirmation and an optional export of `save/`).

### 7.2 Format detection (content-based, not extension-based)

- **GBA:** validate the cartridge header (fixed byte, logo/checksum, title, game code, maker code). Handle zipped/7z input by extracting into `game/`.
- **NDS:** validate the header (title, game code, unit code, header CRC). Note DSi-enhanced and homebrew cases.
- **`.exe`-style games** (a game folder, archive, or `.exe`): detect the *engine*, not the `.exe`.
  - RPG Maker XP/VX/VX Ace: `Game.ini`, `Data/*.rxdata` / `*.rvdata` / `*.rvdata2`, `RGSS*.dll`, optionally `Scripts` content (Pokémon Essentials markers).
  - RPG Maker MV/MZ: `www/js/rpg_*.js` or `js/rmmz_*.js`, `package.json`, `data/System.json`.
  - Ren'Py: `renpy/` directory, `game/*.rpy` / `.rpyc` / `.rpa`, `lib/`.
  - Unknown: refuse politely, explain why, and log the file list for future engine support. A generic Windows `.exe` is **not** supported (no Wine/Box64 layer in scope).
- Detection lives in `feature-import` as pure, unit-tested logic with fixtures (synthetic, tiny, license-free sample files only).

### 7.3 Pipeline
`select → sniff → identify → create folder → copy/move into game/ → hash → write meta.json → insert into DB → (optional) fetch art from local files only`

No network calls during import.

### 7.4 Migration (later phase)
Offer to import existing saves from John GBA Lite, MelonDS and JoiPlay into `save/`. Use the formats observed in Phase 0. Always copy, never modify the originals.

---

## 8. Cheat system

### 8.1 Scope — what "works for everything" means

The owner requires cheats to work **for all three game types**. Be precise about what that means technically:

- **GBA:** support **all three** code families: **GameShark**, **Action Replay**, **Code Breaker**, including their versions/encodings (some are encrypted and some use master codes). Research the exact variants (e.g. GameShark v1/v2/v3, Pro Action Replay v1/v2/v3, Code Breaker versions) against open-source implementations (e.g. mGBA, VBA-M) and write down each variant's format and decryption steps in `docs/research/CHEAT_FORMATS.md`.
- **NDS:** support **Action Replay DS** codes fully (this is the dominant NDS format and the one melonDS/DeSmuME databases use). Research whether GameShark DS / Code Breaker DS codes are practical and publicly documented; implement what can be done properly and document the rest honestly.
- **`.exe`-style games:** GameShark / Action Replay / Code Breaker are **cartridge hardware formats** and do **not exist** for RPG Maker or Ren'Py games. Equivalent power must come from **engine-native cheat backends** in `cheats-script`:
  - RPG Maker XP/VX/VX Ace (mkxp): Ruby script injection (switches, variables, party, items, gold, flags) and Pokémon Essentials' debug facilities where present.
  - RPG Maker MV/MZ: JavaScript injection/plugins.
  - Ren'Py: console/variable access and `config.developer`-style hooks.
  - Support "set once" and "freeze/lock" semantics, per-game saved cheat lists, and one-tap enable/disable.

"Definition of 100%": every code *format* above is parsed, decoded and applied correctly, verified by a **conformance matrix** (`docs/cheats/CONFORMANCE.md`: format × opcode type × test vector × result × engine/core). Any opcode type that cannot be supported is listed in `docs/KNOWN_LIMITATIONS.md` with the reason. Do not claim support that the tests do not prove.

### 8.2 Design
- A **canonical cheat model** (`core-model`): name, platform, source format, raw lines, decoded operations, enabled flag, notes, origin (user-entered / imported file).
- **Parsers/decoders per format** (`cheats-gba`, `cheats-nds`), pure Kotlin, heavily unit-tested with published test vectors. Include format auto-detection from pasted text, with manual override.
- **Appliers** per engine: translate decoded operations into the core's mechanism (libretro cheat API or direct memory writes per frame for GBA/NDS; script injection for `.exe` engines). The cheat engine calls only the `engine-api` cheat hook.
- **Storage:** inside the game's `cheats/` folder, one canonical file (e.g. JSON) plus the **untouched originals** the user imported. Support import from common cheat file formats found during research (libretro `.cht`, plain text, DeSmuME/R4-style databases) and export back out.
- **UI (`feature-cheats`):** one cheats screen per game: add/paste codes, name them, toggle each, reorder, group, search a bundled-by-user database. Show a clear indicator of the detected format and whether it's decoded successfully.
- Handle **master codes**, conditional codes, slide/loop codes, and per-region differences. Log decode errors to the user in plain language.

---

## 9. UI — three switchable modes that match the apps being replaced

### 9.1 Requirement
The owner wants the **same UI design as the apps they use today**, to get the nostalgic feel. Pocketverse therefore ships three UI modes, switchable at runtime (global default plus per-game override):

| Mode | Recreates the UI design of | Used for |
|------|----------------------------|----------|
| GBA mode | John GBA Lite | `.gba` games |
| NDS mode | MelonDS (Android) | `.nds` games |
| PC mode  | JoiPlay (and the in-game overlays of its Ren'Py / RPG Maker plugins) | `.exe`-style games |

The user can also pick any mode for any game (e.g. view the library in GBA mode while playing an NDS game).

### 9.2 What "same design" means (and where the line is)

**Replicate closely (functional and visual design):**
- Screen inventory, navigation flow and information architecture: library, game detail/menu, in-game menu, settings structure, dialogs.
- Layout and placement of every control; default on-screen gamepad arrangement, sizes and spacing; DS dual-screen layouts (stacked, side-by-side, single-screen, touch behaviour).
- Menu hierarchy and ordering; wording *style* (write our own strings with the same structure and tone).
- Colour mood and contrast, typography feel, density, corner radii, iconography *style*, transitions and animation feel.
- Landscape and portrait behaviour.

**Do not copy (assets and branding):**
- Their icon files, logos, bitmaps, skins, sounds, fonts that aren't freely licensed, and screenshots (none may be committed or bundled; reference screenshots stay in the gitignored workspace).
- Their app names or branding, and any ads or "Lite"/upgrade upsells.
- Nintendo / Pokémon / Game Boy / DS names, logos and boot sounds; shell artwork that reproduces trademarked product design verbatim.

Redraw every asset originally: Compose-drawn vectors, our own pixel art, or freely licensed assets (record each licence in `docs/licenses/THIRD_PARTY.md`). Fonts: use open-licence fonts (e.g. OFL) that evoke the originals, and log them.

### 9.3 Capturing the reference (Phase 0, with the RE pass)
For each of the three apps, on the owner's own phone via adb:
1. Walk through **every screen, state, dialog and setting**, in portrait and landscape, with `adb exec-out screencap -p` (and `screenrecord` for transitions and animations). Store in `.re-workspace/ui-reference/<app>/` (gitignored).
2. For standard-Android screens, run `uiautomator dump` to capture view hierarchies (bounds, classes, ids) and derive exact positions and sizes in dp. Game surfaces and custom-drawn controls won't appear there, so measure them from screenshots and document how.
3. Capture the **on-screen controller**: positions, sizes, opacity, edit mode, layout presets, per-orientation differences.
4. Sample colours and measure spacing; record them as **our own named design tokens** (values are facts; names and structure are ours).

Then write `docs/design/UI_SPEC_GBA.md`, `UI_SPEC_NDS.md`, `UI_SPEC_PC.md`, each containing: screen inventory, navigation graph, wireframes (ASCII or our own SVG), control map (dp/percent), design tokens (colour, type scale, spacing, radii), interaction rules, animation notes, and edge cases (rotation, notches, foldables, gamepad focus). **The owner reviews each spec before Phase 6 begins.** Mark anything unclear as an open question for the owner instead of guessing.

### 9.4 Technical design
- `ui-theme-api` defines a `ThemeMode` contract (tokens, layouts, control skins, navigation chrome, transitions). `ui-theme-gba`, `ui-theme-nds` and `ui-theme-pc` implement it; adding a fourth mode must not touch any feature module.
- Feature modules (library, player, cheats, settings) are written once and **rendered through the active mode**, so a new feature appears in all three UIs.
- Switching is instant (no restart, no losing game state). The per-game choice is stored in `meta.json`.
- On-screen controls are editable and saved **per game and per orientation**; physical gamepads and keyboards are first-class (full focus navigation).
- Where fidelity conflicts with accessibility (touch targets under 48dp, low contrast), keep the original look as the default, offer an accessibility toggle, and note the deviation in the spec.

### 9.5 Definition of done
`docs/design/PARITY.md` lists, per screen: the reference behaviour (described in words), our implementation, and any intentional deviation with the reason. A mode is "done" only when every reference screen has an entry and the owner has approved it side by side on the device.

---

## 10. Documentation deliverables

- `README.md` — what the project is, how to build, module overview.
- `EXPLANATION.md` — architecture and reasoning in plain language, updated each phase.
- `docs/research/` — OPEN_SOURCE_ALTERNATIVES.md, CHEAT_FORMATS.md, `re/` notes + SYNTHESIS.md.
- `docs/licenses/` — THIRD_PARTY.md, COMPATIBILITY_MATRIX.md, DECISION.md.
- `docs/architecture/` — STORAGE.md, ENGINE_API.md, CHEAT_ENGINE.md, THEMES.md.
- `docs/design/` — UI_SPEC_GBA.md, UI_SPEC_NDS.md, UI_SPEC_PC.md, PARITY.md.
- `docs/cheats/CONFORMANCE.md` — the test matrix.
- `docs/KNOWN_LIMITATIONS.md` — honest list of what doesn't work and why.
- A `README.md` in **every module**.

---

## 11. How to start (first session)

1. Read this file fully, then ask the owner where their REA MCP configuration is (and what it is), and for the phone's connection details if adb needs them.
2. Create the empty doc/folder skeleton (Section 6 and Section 10) with the READMEs and `.gitignore` entries for `research/` and `.re-workspace/`.
3. Run `adb devices` and confirm the owner's phone is connected. List installed package names for the five apps (read-only).
4. Execute Phase 0: Section 5.1, then 5.2 (including the UI reference capture in Section 9.3), committing docs as you go.
5. Stop at the Phase 0 exit checklist and present a summary for review.
