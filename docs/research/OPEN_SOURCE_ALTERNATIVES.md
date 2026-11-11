# Open-source alternatives: comparison and recommendations

Task 2, 2026-11-12. Snapshot taken from shallow clones in `research/sources/` (gitignored) plus the GitHub API. Licences are SPDX ids as GitHub reports them, cross-checked against file headers where noted. "Unverified" means not confirmed from a primary source in this pass.

## 1. Candidate matrix

| Project | Format | Repo | Commit | Licence | Last push | Android status | Build | Cheat support (from source) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| mGBA | GBA (+GB) | mgba-emu/mgba | `662dada8b7` | MPL-2.0 | 2026-11-06 | libretro core builds for Android; no first-party app. Latest release 0.10.5 (2025-03-09) | CMake | Core API `mgba/core/cheats.h`; `src/gba/cheats/` has `gameshark.c`, `codebreaker.c`, `parv3.c`; enum `GBACheatType`: autodetect, Code Breaker, GameShark, Pro Action Replay, VBA. libretro exports `retro_cheat_set` |
| gpSP (libretro) | GBA | libretro/gpsp | `5819380c2f` | GPL-2.0 (headers: v2 or later) | 2026-09-19 | libretro core, ARM dynarec, fast on weak devices | Make | own `cheats.c`; formats not examined |
| VBA-M | GBA (+GB) | visualboyadvance-m/visualboyadvance-m | `aad5da47e4` | GPL-2.0 (sampled headers: v2 or later; core files not confirmed) | 2026-11-10 | no Android app | CMake | not examined |
| melonDS | NDS (+DSi) | melonDS-emu/melonDS | `497aa6cdd2` | GPL-3.0 | 2026-11-04 | core only, no Android app | CMake | `AREngine.cpp`, `ARCodeFile.cpp`, `ARDatabaseDAT.cpp`: Action Replay engine in the core |
| melonDS-android | NDS | rafaelvcaetano/melonDS-android | `aaf263d233` | GPL-3.0 | 2026-11-09 | the maintained Android port; release 2.0.1 (2026-05-06); the installed app (`me.magnum.melonds` 2.0.1) matches this lineage | Gradle + NDK, core in submodule `melonDS-android-lib` (GPL-3.0, not fetched in the shallow clone) | Kotlin cheat DB parser/UI in `common/cheats/` |
| melonDS libretro | NDS | libretro/melonDS | not cloned | GPL-3.0 | 2026-07-19 | libretro core; Android performance unverified | CMake/Make | via libretro cheat API; unverified |
| NooDS | NDS | Hydr8gon/NooDS | `4cb1439c33` | GPL-3.0 | 2026-08-09 | Android build included (`make android`) | Make/Gradle | cheat references in `core.cpp`; format not examined |
| DeSmuME | NDS | TASEmulators/desmume | `559bf216bd` | GPL-2.0 (headers: v2 or later) | 2026-11-06 | libretro fork exists; no maintained Android app | Meson/Autotools | `cheatSystem.cpp` |
| RetroArch | frontend | libretro/RetroArch | `5ab2152e6a` | GPL-3.0 | 2026-11-11 | official Android app | Make | `cheat_manager.c`; `.cht` files |
| Lemuroid | frontend | Swordfish90/Lemuroid | `3625bc121d` | GPL-3.0 | 2026-10-16 | full Android app, release 1.17.0 (2026-04-21) | Gradle; cores via submodule `LemuroidCores` (no licence set); JNI host `LibretroDroid` (GPL-3.0) | not examined |
| Pegasus | launcher | mmatyas/pegasus-frontend | `3702598cf9` | GPL-3.0+ (LICENSE.md text) | 2026-11-11 | Android supported; Qt based | CMake/qmake | none, launcher only |
| mkxp-z | RPG Maker XP/VX/VX Ace | mkxp-z/mkxp-z | `f02d586624` | GPLv2+ (README and headers); binaries with default OpenSSL option are effectively GPLv3 | 2026-10-31 | **no Android code in the main repo.** Android forks exist (see section 3) | Meson | n/a (script injection, see CLAUDE.md 8.1) |
| EasyRPG Player | RPG Maker 2000/2003 | EasyRPG/Player | `7db2bb50a2` | GPL-3.0 | 2026-11-08 | `builds/android/` present; latest release 0.8.1.1 (2025-06-02) | CMake | n/a |
| Ren'Py | visual novels | renpy/renpy | `074f2b639c` | MIT, plus parts derived from LGPL code (`sphinx/source/license.rst`); GitHub shows no licence | 2026-11-11 | official packaging via RAPT (per-game APK/AAB); release 8.5.3.26051504 (2026-05-16) | Python + SDL | n/a (console/`config.developer`) |
| Ruffle | Flash | ruffle-rs/ruffle | `c95bde93f4` | MIT OR Apache-2.0 | 2026-11-11 | JoiPlay ships a Ruffle runtime plugin; Android build in the repo not verified | Cargo | n/a |

## 2. Recommendations per format

| Format | Recommended | Runner-up | Why |
| --- | --- | --- | --- |
| GBA | **mGBA** (as libretro core first) | gpSP | mGBA has the broadest verified cheat support (GameShark, Pro Action Replay v3, Code Breaker, VBA-style) in a documented core API, active development, and the weakest copyleft (MPL-2.0, file level). gpSP is faster on weak hardware but less accurate; keep as a low-end fallback only if profiling on real devices demands it. |
| NDS | **melonDS** (core from `melonDS-android-lib` or the libretro core) | NooDS | melonDS has the Action Replay engine inside the core, matches the installed MelonDS app the owner already uses, and the Android port is actively maintained (push 2026-11-09). NooDS is lighter and has an Android build, but its cheat support and accuracy are unverified. DeSmuME is third. |
| RPG Maker XP/VX/VX Ace | **mkxp-z** | an Android mkxp-z fork used as reference only | Pokémon Essentials v19+ needs mkxp-z, not plain mkxp (per community reports, unverified from primary sources). Biggest risk in the project: the main repo has no Android port. |
| RPG Maker 2000/2003 | **EasyRPG Player** | none | Only maintained option, GPL-3.0, Android build exists. Out of the owner's stated games; defer. |
| RPG Maker MV/MZ | own WebView wrapper | `cellicom/rpgmaker-to-android` (MIT, new, unproven) as reference | No mature open-source Android wrapper found. Deferred behind XP/Ren'Py. |
| Ren'Py | official Ren'Py runtime (MIT + LGPL parts) via an RAPT-derived host | none | RAPT builds one app per game; a shared runtime that loads arbitrary games is not documented anywhere. Needs a spike in Phase 5. |
| Flash (optional) | Ruffle | none | Permissive. Only if the owner wants Flash games; not in scope today. |
| Hosting layer | **LibretroDroid** (GPL-3.0) as the JNI/GL host for mGBA and melonDS libretro cores; Lemuroid as architecture reference | Direct embedding of `melonDS-android-lib` | One host for GBA and NDS keeps `engine-libretro` thin. Caveat: the Lemuroid core repo (`LemuroidCores`) has no licence, so we build cores ourselves. |

These are research recommendations. The engine-hosting choice (libretro vs direct embedding for NDS) must be settled by a Phase 3 spike measuring frame time on the owner's phone, not by this document.

## 3. Prior art and Android mkxp forks

No open-source Android app was found that combines console emulation with RPG Maker or Ren'Py. JoiPlay (closed source) covers the engines only; Lemuroid, RetroArch and melonDS-android cover emulation only. Search was web search plus GitHub repo search, so it is not exhaustive.

Android mkxp-z forks (found by an agent through GitHub API; not independently re-checked by cloning):

| Fork | Licence | Last push | Notes |
| --- | --- | --- | --- |
| thehatkid/mkxp-z-android | none | 2023-07-08 | README says "almost works properly"; needs All Files Access on Android 11+ |
| BookerRues9/mkxp-z-android-reworked | none | 2026-05-25 | derived from the above |
| vrcxz/mkxp-z-android-packager | none | 2026-10-05 | |
| nikalbazu24/Essentia | GPL-2.0 (GitHub field; "or later" not checked) | 2026-11-11 | runs XP/VX/VX Ace, launchable from frontends |
| abharrison1995-droid/pif-android | none | 2026-10-26 | Infinite Fusion build, personal use |
| codeberg.org/warmwinter/mkxp-z | unverified | 2026-09-19 | claims Android subproject; README unreadable |

Consequence: several forks carry no licence, so their code cannot be vendored. Plan to port Android support onto mkxp-z ourselves, using the forks and the Essentia GPL code only as behavioural reference after a licence check.

## 4. Open items for later tasks

- NooDS cheat format and DeSmuME/RetroArch `.cht` handling: Task 8 (`CHEAT_FORMATS.md`).
- Whether `libretro/melonDS` meets frame-time targets on the moto g34: Phase 3 spike.
- Confirm `melonDS-android-lib` source (submodule not fetched) when Task 4 reads upstream MelonDS code.
- Essentia licence ("GPL-2.0" only vs "or later"): needed before any reuse.
- Ren'Py shared-runtime embedding: Phase 5 spike.
