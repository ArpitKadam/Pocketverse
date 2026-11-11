# RE note: JoiPlay RPG Maker plugin (plus the Ruffle runtime)

Task 7, 2026-11-12. Written in our own words. Evidence labels: **Observed** (installed APKs or the owner's phone), **Source** (JoiPlay's public repositories, cloned into the gitignored `research/sources/`), **Inferred**, **Unknown**. Nothing from the APKs or the repositories is copied into this project. Built on Tasks 5 and 6, which cover the shared launch contract; this note covers what is specific to this plugin.

## 1. Identity

| Field | Value | Basis |
| --- | --- | --- |
| Package | `cyou.joiplay.rpgm` | observed |
| Version | 1.20.53-patreon, code 12053 (supporter build, sideloaded) | observed |
| SDK | minSdk 21, targetSdk 34 | observed |
| APK | 79 MB, one 229 KB `classes.dex` | observed |
| Native engines (arm64) | `libmkxp18.so` (14.8 MB), `libmkxp19.so` (15.5 MB), `libmkxp30.so` (17.1 MB), plus `libfluidlite.so` (MIDI synth, 147 KB). The same four exist for armeabi-v7a and x86_64 | observed |
| What the three engines are | three builds of the mkxp engine for different Ruby versions, most plausibly Ruby 1.8, 1.9 and 3.0 (from the names and from the JoiPlay setting "Use Ruby 1.8 for XP/VX") | inferred |
| Other assets | six Ruby scripts (section 5), a bundled 20 MB CJK fallback font (Noto Sans CJK, with its licence file), the plugin's notice pages | observed |
| Permissions | all-files access, storage read/write, media read, VIBRATE, INTERNET | observed |
| Entry points | `LauncherActivity` (exported, shows an info screen: title, version, "this is a plugin for JoiPlay", licence text, "hide icon from the launcher"), `PermissionActivity` (exported, per-generation actions below), `MainActivity` (not exported, landscape, immersive, runs the engine) | observed |

## 2. Lineage and which parts are public

The plugin's own notice says it is a derivative of **mkxp** (Ancurio) and of **android-mkxp** (pulsejet), contains parts of **mkxp-z**, and is licensed GPLv2.

| Repository | What it is | Licence evidence | State |
| --- | --- | --- | --- |
| `github.com/joiplay/android-mkxp` (our clone `479167e`, 2022-01-08) | the Android plugin shell: `LauncherActivity`, `PermissionActivity`, `MainActivity`, SDL activity | **no licence file and no GitHub licence field**; the plugin's notice claims GPLv2 | public snapshot is from 2022 and older than the installed 1.20.53 |
| `github.com/joiplay/mkxp` (our submodule checkout `fb5ae91`, 2022-01-08; repo last pushed 2023-03-12) | JoiPlay's fork of the mkxp engine with JoiPlay-specific Ruby bindings | GitHub says GPL-2.0; the sampled source headers (`main.cpp`, `config.cpp`) say **version 2 or later** | public, older than the installed engines |
| `github.com/joiplay/commons` (MIT), `joipad` (MIT) | shared models, parsers, on-screen pad | MIT `LICENSE` files | public, see Task 6 |

The public snapshots cover the Ruby 1.8 era only. The installed build adds engines `19` and `30`, which are **not** in the public repositories we found. The mkxp-z lineage for those is stated by the plugin's notice; the exact code is Unknown.

Licence consequence for Pocketverse (to be reflected in `COMPATIBILITY_MATRIX.md`): `joiplay/mkxp` headers allow GPL-2.0-or-later, so code could in principle be merged into a GPLv3 app, but the surrounding Android shell has no declared licence, and the installed engines have no public source. So JoiPlay's plugin is a **behavioural reference**; our engine port should start from upstream mkxp-z (GPLv2+) as decided in Task 2.

## 3. Launch contract (specific parts)

Shared with the Ren'Py plugin (Task 6): JoiPlay starts `PermissionActivity` by explicit component; two string extras `game` (title, id, folder, exec file, type) and `settings` (JSON, sections with `{ "boolean": .. }` or `{ "string": .. }` values); the plugin checks all-files access, then starts `MainActivity` and forwards the extras. Observed in Task 5 for this plugin (the intent is `cmp=cyou.joiplay.rpgm/.PermissionActivity (has extras)`), and the extras' names are source-verified from the public plugin shell: `game` and `settings`, plus an optional `customScript`.

Specific to this plugin:

- **One APK, three RPG Maker generations.** `PermissionActivity` declares three actions: `cyou.joiplay.rpgmxp.PermissionActivity`, `cyou.joiplay.rpgmvx.PermissionActivity`, `cyou.joiplay.rpgmvxace.PermissionActivity`. The game JSON's `type` field (`rpgmxp`, and by extension the VX and VX Ace values) carries the same information. Which of the two selects the engine build is Unknown.
- **Generation detection in the plugin (source):** it reads the game's `Game.ini`: the `Library` line names the RGSS DLL (`RGSS102E`-style names for XP, `RGSS2…` for VX, `RGSS3…` for VX Ace), which decides the encrypted-archive extension to look for (`.rgssad`, `.rgss2a`, `.rgss3a`) next to the executable name; `Scripts` names the scripts file. This matches our own detection spec (CLAUDE.md 7.2).
- **Config on disk, two layers.** (a) JoiPlay's global and per-game choices arrive in the `settings` extra. (b) The plugin additionally merges a **per-game `configuration.json`** read from the game folder (or from `/sdcard/JoiPlay/games/<id>/` if the game lives outside shared storage). The owner's Infinite Fusion folder contained exactly such a file (`{"cheats": false}`): the owner's export from JoiPlay carried it. The 2022 source generated an `mkxp.conf` from a template; the installed plugin writes a **`mkxp.json`** instead (observed in Task 5: 56 keys, rewritten on every launch in `Android/data/cyou.joiplay.rpgm/files/`).
- **Fixed shared locations:** the runtime package folder `/sdcard/JoiPlay/RTP/<engine name>/app/` (the owner's phone has `RTP/mkxp-z/app/` with a soundfont `sf.sf2`), a patches file `/sdcard/JoiPlay/patches.json`, and a key-mapping file `/sdcard/joiplay_mappings.txt` read from the root of shared storage (source; not present on this phone).
- **Observed engine name in JoiPlay:** the library showed the game's type as "RPG Maker (mkxp-z)", and the RTP folder is named `mkxp-z`. So the user-facing engine choice is between older "RPG Maker XP/VX/VX Ace" and "RPG Maker (mkxp-z)", the latter being what Pokémon Essentials v19+ games need (Task 2). Which of `mkxp18/19/30` each maps to is inferred, not observed.

## 4. Files and folders the plugin creates (observed in Task 5, matched with source)

In the game folder: `logs.txt` (engine log: preload, per-script patch list, postload, resolution), `.online_path_cache` (lowercased path index so Windows-cased asset names resolve on Android's case-sensitive storage), `.nosearch`, `UserData/AppData` and `UserData/Temp`. In source, `createUserDataFolders` makes `UserData/Temp` and `UserData/AppData` and the engine is told to use them as the application data and temp directories. This is where a Windows game's `%APPDATA%`-style saves land, but no save was written in the Task 5 run (the game was closed on its first menu), so the exact save file path is still **Unknown**. The owner's `File A.rxdata` fixture is a Ruby Marshal dump (header bytes `04 08`), as expected for Essentials saves.

User hook files in the game folder (source): `preload.rb` and `postload.rb` run before and after the game's own scripts if present; custom scripts can also be passed via the `customScript` extra. A `patch/` folder in the game is prepended to the runtime-package search path (observed: `rtps` listed `<game>/patch` first).

## 5. The Ruby layer: engine extensions and shim scripts

**Engine-side extensions (source, public `joiplay/mkxp` bindings; names also found in the installed engines):**

- `MKXP.puts`: log to the game's `logs.txt` and Android's log.
- Input family prefixed `j`: `jupdate`, `jdir4`, `jdir8`, `jtrigger?`, `jpress?`, `jrepeat?`, `jrelease?` and the `ex` key variants: a controller-aware input path the shims swap in.
- Graphics family prefixed `poke_`: `poke_width`, `poke_height`, `poke_resize_screen`, `poke_snap_to_bitmap`: a resizable virtual screen for Essentials.
- A Ruby prelude (public `preload.rb`): fakes `DL::CFunc` and `Win32API`-style calls, strips carriage returns from `String#to_s`, adds integer-style `^`, `&`, `<<`, `>>` on `Float`, and sets `ENV` entries such as `USERNAME`, `COMPUTERNAME`, `USERDOMAIN` to "JoiPlay" so Windows-flavoured scripts do not crash.

**The six asset scripts (observed; behaviour paraphrased):**

| Script | When it runs | What it does |
| --- | --- | --- |
| `pokefix.rb` | only if `$PokemonSystem` exists (the Essentials marker) | stubs functions that do not work on Android (play-time lookup, loop points), pins the default zoom at 0.5, replaces `Graphics.width/height/resize_screen/snap_to_bitmap` with the engine's `poke_*` versions, stubs a downloader module used by one fan game |
| `pokeinput.rb` | Essentials only | redirects every `Input` method (`update`, `dir4`, `dir8`, `trigger?`, `repeat?`, `press?`, `release?` and key-code variants) to the `j*` controller-aware versions |
| `disableautotiles.rb` | when the setting is on | sets one global flag that stops autotile animation refresh (a performance option) |
| `inoritilemapfix.rb` | when the setting is on | a tilemap workaround for tilesets larger than the GPU's maximum texture size; the plugin credits its author and says it is part of mkxp-z |
| `mapoptimizer.rb` | on the game menu's "Optimize Maps" | builds a per-map minimal tileset |
| `rv.rb` | on the game menu's "Unpack/Repack Scripts" | draws a small progress window and converts the Marshal-packed `Scripts.rxdata`/`rvdata`/`rvdata2` to and from a folder of readable script files |

## 6. Cheats: the embedded scripts (the blueprint for our `cheats-script`)

This is the most reusable finding. The engine libraries contain **five Ruby cheat scripts as embedded text**, named by their symbols: `cheat_rpgmxp_rb`, `cheat_rpgmvx_rb`, `cheat_rpgmvxace_rb`, `cheat_pe19_rb`, and a pre-v19 Pokémon Essentials one (its log line reads "Loading cheats for Pokemon Essentials"; the v19 one logs "...Essentials v19"). How they work, from reading them as text:

- **Switch:** a global `$CHEATS`, set from the settings `cheats` flag. When off the log says "Cheats are disabled" (observed in Task 5); when on, "Cheats are enabled" and "Executing Cheat Script".
- **Trigger:** the **F8** key (`Input.trigger?(Input::F8)` and the extended-key variant). The plugin's on-screen pad has an F8 button for exactly this (observed in the Task 5 UI capture, with F2 on the other side), so the "Cheats" feature is a key press, not a separate overlay. The scripts hook in by aliasing the original map or player update method, then open a new `Scene_Cheat`.
- **RPG Maker XP:** a small command window: Level Up, Gain Gold, Get Items, Cancel. Level Up offers +1, +5, +10, +100 levels (implemented as repeated single level-ups); Gain Gold offers 100, 1,000, 10,000, 100,000, 1,000,000.
- **VX Ace style (`Scene_MenuBase` based, shipped as the VX Ace script):** a richer command class: Level, Gold, Get Items, Get Weapons, Get Armors, Cancel, with the same level and gold steps. The item screens reuse the game's own shop UI.
- **Pokémon Essentials:** "Get Items" (reuses the game's Poké Mart screen with an adapter subclass that lists every item and overrides pricing), "Heal Party" (heals each party member), "Level Up" (pre-v19: pick a party member), and a "walk through walls" toggle (aliases the passability check). Both Essentials variants detect the game by constants (`GameData`, `PBItems`, the Mart scene class names), so they cope with Essentials versions whose class names differ.
- **Limit (as the JoiPlay docs warn):** these scripts work only when the game does not heavily rewrite engine scripts, and they need the game's own scene and window classes.

For RPG Maker MV/MZ the equivalent is a JavaScript cheat-menu plugin shipped inside the main JoiPlay APK (Task 5). For Ren'Py it is the always-on developer console (Task 6).

Design implication: our `cheats-script` module can use the same three-part structure with our own code: (1) a per-engine script pack, (2) a global enable flag read by the script at start, (3) one in-game trigger key mapped to a touch button. We should write ours originally and add what JoiPlay lacks: named, saved, freezable entries (spec section 8.1), instead of only fixed menus.

## 7. Engine settings visible to users

Covered in Task 5's settings map; the plugin-side meaning of the RPG group: debug log, Ruby 1.8 mode, custom font, smooth scaling, vsync, frame skip, fast-forward speed, solid fonts, path cache (and prebuilt, and faster enumeration), postload scripts, window size, vertical alignment, font scale, copy text; plus the "P.Essentials" group (fixes, input overrides, tilemap fix, disable autotile animation). All map to keys in `mkxp.json` (Task 5 table). Audio uses OpenAL Soft with a FluidLite software synthesizer for MIDI via the shared soundfont; the engine reads RGSS1, RGSS2 and RGSS3 encrypted archives.

## 8. Ruffle runtime plugin (`cyou.joiplay.runtime.ruffle`)

Observed (no Flash game was run; Flash is outside the owner's use):

| Field | Value |
| --- | --- |
| Version | 1.00.20-patreon, code 10020, minSdk 21, targetSdk 34, sideloaded |
| Native | one library, `libruffle_android.so` (19.9 MB, a Rust build of Ruffle with the wgpu graphics layer; Vulkan and OpenGL ES backends are both present), plus the C++ runtime |
| JNI | `MainActivity.nativeInit`, `keyDown`, `keyUp`, and the AndroidX Games activity startup |
| Notice | derivative of the open-source `ruffle-android` project and parts of Ruffle desktop; Apache-2.0, zlib and MIT components |
| Entry points | `PermissionActivity` with actions `cyou.joiplay.runtime.ruffle.run` and the more generic `cyou.joiplay.runtime.flash.run`; `LauncherActivity` with action `cyou.joiplay.runtime.ruffle.info`; `MainActivity` exported |

- The shape is the same as the other plugins (permission screen first, then the engine). The generic `...flash.run` action suggests a second Flash runtime could answer the same request (inferred).
- JoiPlay's Ruffle settings (render backend OpenGL or Vulkan, quality, scale mode, load behaviour) match the engine's own options.
- A discrepancy: the main app's `<queries>` names `cyou.joiplay.ruffle` (an older package id), while the installed plugin is `cyou.joiplay.runtime.ruffle`. How the main app finds the plugin is Unknown; it may declare a different list in code.
- Upstream: the starting point it names, `torokati44/ruffle-android`, is now the official **`ruffle-rs/ruffle-android`** (MIT/Apache-2.0 by the project's `LICENSE.md`; last push 2026-11-09 per the GitHub API). That is the clean reference if Pocketverse ever adds a Flash engine, and it fits the engine-api model with no extra work on the library side.

## 9. Comparison of the three plugins

| | RPG Maker plugin | Ren'Py plugin | Ruffle plugin |
| --- | --- | --- | --- |
| Launch | explicit `PermissionActivity` + `game` / `settings` extras (observed, extras names source-verified) | same | action names `...flash.run` / `...ruffle.run` (observed), extras presumed same |
| Config to engine | merged settings plus `mkxp.json` and per-game `configuration.json` | environment variables | native `nativeInit` (Unknown details) |
| Game customisation | Ruby postload/preload, text patches from `patches.json`, shim scripts | `patches.json` text replacements, environment switches | none seen |
| Cheats | F8 key opens an in-game Ruby scene | developer console | none |
| Engine source | mkxp / mkxp-z lineage, public for 2022 only | Ren'Py fork, public | ruffle-android, public |

## 10. Lessons for Pocketverse (design level)

1. One launch action + a small JSON "game" object + a "settings" object is enough for all engines; per-engine detail goes in the settings object, not in the intent.
2. Keep a canonical per-game config file that the engine re-reads on each launch (mkxp.json style), and a second, optional per-game override file that travels with the game folder (their `configuration.json`). Our `meta.json` plays that role.
3. Treat compatibility patches as data (a shared JSON list of key/value text replacements) and engine hooks as small scripts with a clear trigger condition (`$PokemonSystem` defined, etc.).
4. Windows-game compatibility needs a lowercase path index, a minimal `ENV`/Win32 shim layer and an input-method override. Plan these for `engine-mkxp`.
5. Everything above depends on all-files access shared across processes. If Pocketverse runs the engines in-process or in its own sibling processes inside one APK, it avoids needing a shared public folder and the system permission dialog flow; this is the main architectural improvement over the JoiPlay model.
6. Do not copy: none of JoiPlay's scripts, patches or code. We write our own and use upstream mkxp-z, Ren'Py and Ruffle as the clean sources.

## 11. Open questions

1. Mapping between `mkxp18/19/30` and JoiPlay's engine choices.
2. Where a Pokémon Essentials save is written (`UserData/AppData`?). Needs one in-game save.
3. Extras for the Ruffle plugin and how the main app discovers it.
4. Whether the F8 cheat scripts also need the `cheats` flag in `mkxp.json` (yes by the `$CHEATS` global) and how the main app flips it per game (settings JSON, inferred).
5. Why the three engines differ (Ruby version splits versus mkxp vs mkxp-z): needs symbols from the engines, not available without a disassembler.

## 12. Test-run record

No change was made to the phone in this task beyond reading and opening the plugin's launcher screens; the Ruffle launcher requires an action intent, so one start attempt just returned to the home screen and its screenshot was deleted. Earlier tasks' phone changes are recorded in their notes.

## 13. Evidence trail

APK SHA-256: RPG Maker plugin `78da7116de54bfcd61db1a47ec680cc4d3228e4ca920791858bced786335fbe9`; Ruffle plugin `c86c368335ef0290eaec2102773d1684c33d9a92b2d46e07cae41553acf79aca`. Methods: `adb pull`, `tools/axml_dump.py`, Python extraction of APK assets and native-library string scans, the public repositories `joiplay/android-mkxp`, `joiplay/mkxp`, `joiplay/commons`, the open Ruffle repository metadata through the GitHub API, plus the Task 5 observations (logcat, `mkxp.json`, `logs.txt`, UI captures).
