# RE note: JoiPlay (main app)

Task 5, 2026-11-12. Written in our own words. JoiPlay is closed source: this note records behaviour and contracts, never code or assets. Nothing from the app (APK contents, `patches.json`, bundled documentation, screenshots) is committed; raw material is in the gitignored `.re-workspace/`.

Labels: **Observed** = seen on the owner's phone or in the shipped package. **Inferred** = concluded from observations. **Unknown** = not determined.

## 1. Identity

| Field | Value | Basis |
| --- | --- | --- |
| Package | `cyou.joiplay.joiplay` | observed |
| Version | 1.20.410-patreon, code 120410 (supporter build, sideloaded) | observed |
| SDK | minSdk 21, targetSdk 34, OpenGL ES 2.0 required | observed |
| APK | 27 MB, 3 `classes*.dex`, many assets (see section 4); native libs for arm64-v8a: `libjoiplay.so` (1.4 MB), `libjoidec.so` (3.0 MB), `libinnoextract.so` (3.1 MB) | observed |
| Permissions | `MANAGE_EXTERNAL_STORAGE` (all-files access), READ/WRITE_EXTERNAL_STORAGE, READ_MEDIA_*, INTERNET, VIBRATE, FOREGROUND_SERVICE (+DATA_SYNC), INSTALL_SHORTCUT, POST_NOTIFICATIONS, WAKE_LOCK, RECEIVE_BOOT_COMPLETED, ad and install-referrer permissions | observed |
| Supported game types (from its bundled documentation) | Ren'Py, RPG Maker XP/VX/VX Ace/MV/MZ, Construct 2/3, TyranoBuilder, Flash, HTML | observed |
| Engines inside the main app | HTML-family games (RPG Maker MV/MZ, Construct, Tyrano) run in a WebView inside JoiPlay itself. Ren'Py, RPG Maker XP/VX/VX Ace and Flash run in **separate plugin APKs** | observed |

The `-patreon` suffix marks a supporter build. We observe it as installed; nothing here touches billing or licence checks.

## 2. Entry points

| Component | Exported | Role |
| --- | --- | --- |
| `activities.SplashActivity` | yes, LAUNCHER | start; also the target of a broad `VIEW` filter for `file:` and `content:` URIs whose path ends in `.exe`, `.sh`, `.py`, `.html` or `.swf`, and for the custom scheme `joiplay:` |
| `installer.InstallerActivity` | yes | `VIEW` filter for `.jgp` (JoiPlay game package) files |
| `activities.JoiHubActivity` | yes | deep link `joihub://joiplay.cyou/joihub` (online hub/compatibility list) |
| `activities.MainActivity`, `hub.HubActivity`, `activities.FileManagerActivity`, `PluginEditorActivity`, `TranslatorActivity`, `ShortcutActivity`, `IntroActivity` | no | library, settings (a fragment of `MainActivity`), own file manager, MV/MZ plugin on/off editor, translation tool, shortcut launcher |
| `html.HTMLActivity`, `html.TyranoActivity` | no | WebView game hosts, landscape sensor |
| `activities.UpdateActivity` | yes | update screen |
| `utilities.JoiFileProvider` | no (grants URI permissions) | file sharing, authority `cyou.joiplay.joiplay.provider` |
| Services: `innoextract ExtractService`, `UpdateService`, `ClearCacheService` | no | installer extraction (GOG/Inno Setup style), updates, cache clearing |

`<queries>` names the plugins `cyou.joiplay.ruffle` and `cyou.joiplay.renpy.legacy`, so the main app expects an open-ended plugin family (installed here: `renpy`, `rpgm`, `runtime.ruffle`).

A game is registered by pointing JoiPlay at its **launch file** (the `.exe`, `.sh`, `.py` or `.html`), which is why the manifest filter is shaped around those suffixes. The engine is then classified, not the `.exe` run: our own spec (CLAUDE.md 7.2) makes the same choice.

## 3. How JoiPlay hands a game to a plugin (the key contract)

Observed by launching one real game (Infinite Fusion, RPG Maker XP engine type "RPG Maker (mkxp-z)") with the owner's approval, and by reading both plugin manifests.

**Step 1: intent.** Logcat shows JoiPlay writing "Launcher : Launching <game title>" to its own log, then the system line `START u0 {flg=0x10000000 cmp=cyou.joiplay.rpgm/.PermissionActivity (has extras)}` from JoiPlay's user id. So:

- It is an **explicit-component** intent to the plugin package's `PermissionActivity` (exported, single top), with `FLAG_ACTIVITY_NEW_TASK`.
- The intent has extras. Android does not log their contents, and no decompiler is available here. For the Ren'Py plugin the extras are known from its public source (Task 6): two string extras, `game` and `settings`, each a JSON object. The RPG Maker plugin is closed, but it shares the same library and launch pattern, so it very likely takes the same two extras (inferred).
- The plugin's manifest also declares per-engine actions on `PermissionActivity`: `cyou.joiplay.rpgmxp.PermissionActivity`, `cyou.joiplay.rpgmvx.PermissionActivity`, `cyou.joiplay.rpgmvxace.PermissionActivity` (one APK serves three RGSS versions), and `cyou.joiplay.renpy.PermissionActivity` in the Ren'Py plugin. Inferred: the action selects the RPG Maker generation when the component alone is not enough. Not confirmed on the wire in this run, because the log line shows only the component.

**Step 2: plugin start-up.** The plugin's `PermissionActivity` checks storage permission (the plugin also holds all-files access), then starts the plugin's non-exported `MainActivity` (landscape, immersive), which runs the engine. The first launch showed an Android permission dialog from the system's permission controller.

**Step 3: configuration travels in a file, not in the intent (inferred, strongly).** Before the plugin starts, JoiPlay rewrites `/sdcard/Android/data/cyou.joiplay.rpgm/files/mkxp.json`. Observed twice, with two different install paths: the file's `gameFolder` and `rtps` entries changed to the new game's folder and nothing else changed. It contains 56 keys. They are the upstream mkxp-z config names plus JoiPlay's own:

| Group | Keys seen |
| --- | --- |
| Game identity | `gameFolder` (absolute path), `execName`, `windowTitle`, `rgssVersion` (0 = auto-detect) |
| Asset search path | `rtps` (ordered list: the game's `patch` folder, the game folder, then the shared RTP folder), `patches` (path to the global `patches.json`) |
| Display | `defScreenW`/`defScreenH` (640x480), `fixedAspectRatio`, `smoothScaling`, `vsync`, `fixedFramerate`, `frameSkip`, `syncToRefreshrate`, `fullscreen`, `winResizable`, `verticalScreenAlign` (e.g. top-center), `integerScaling*`, `maxTextureSize` |
| Input and speed | `fastForward`, `fastForwardSpeed`, `enableReset`, `anyAltToggleFS`, `SDLControllerMappings` |
| Text | `fontScale`, `customFont`, `fontSubs`, `solidFonts`, `copyText` |
| Audio | `midi.soundFont` (shared `sf.sf2`), `midi.chorus`, `midi.reverb`, `SE.sourceCount` |
| Performance | `pathCache`, `prebuiltPathCache`, `fastPathEnum`, `subImageFix`, `enableBlitting`, `animateAutotiles` |
| Scripting | `enablePostloadScripts`, `preloadScripts`, `postloadScripts`, `customScript`, `useUnpackedScripts`, `useScriptNames`, `rubyLoadpaths`, `cheats`, `debugMode` |

**Step 4: return value.** None observed. The game is closed from the plugin's own top bar (close button with a "Do you want to close the game?" confirmation), and Android returns to JoiPlay's task. No result intent or callback was seen.

**Shared locations (all under `/sdcard/JoiPlay/`, readable by every plugin because they hold all-files access):**

| Path | Purpose |
| --- | --- |
| `games/<12-hex id>/` | one folder per game; the id is JoiPlay's internal game id (here it was also the default game name; how it is derived is Unknown) |
| `RTP/mkxp-z/app/` | shared RPG Maker runtime package files plus the MIDI soundfont `sf.sf2` (4.1 MB) |
| `patches.json` | one global script-patch list, shared by all engines |
| `Android/data/cyou.joiplay.joiplay/files/logs/JoiPlay.txt` | JoiPlay's log, recreated each run |

Lesson for Pocketverse: this works because every component has all-files access and agrees on a fixed shared folder. The cost is `MANAGE_EXTERNAL_STORAGE`. This is the evidence we need for `docs/architecture/STORAGE.md` (spec section 6, option c). Our cleaner alternative is one explicit launch intent whose extras are a path (or URI) and a config-file path, with the engine reading one canonical JSON.

## 4. Library, import and game identification

- Add menu (observed): Add Game, Install Package (JGP), Import RGA File, Import Archive File. Library toolbar: Add, Filter (search by name, type, sort by name/date/popularity), overflow (Layout, Compatibility List, Settings, Backup/Restore, Remove Missing Games, Help, About).
- Archive import (observed): its own file picker chooses a zip; the app extracts into `games/<id>/` with a progress dialog ("Extracting game files…"), then asks for **name** (default is the id when the archive has no usable title), **version**, and an optional **icon** (PNG, JPG, ICO or WEBP). Our 2.6 GB zip (26,778 entries) extracted to about 3.1 GB in roughly 11 minutes on this phone. Native extractors in the app: an archive lister/extractor with AES support (`libjoidec`: list, size, password check, extract), and in `libjoiplay`: ZIP, RGSS encrypted archives (RGSSAD/RGSS3A), Electron ASAR, CAB, Flash projectors and Enigma-packed executables. `libinnoextract` unpacks Inno Setup installers. `bsdiff`/`bspatch` functions suggest the "RGA" patch files are binary diffs (inferred).
- Type detection (observed): the extracted folder was classified "RPG Maker (mkxp-z)" with no user choice. The folder had `Game.ini` (`Library=RGSS104E.dll`, `Scripts=Data\Scripts.rxdata`), `Data/*.rxdata`, the RGSS DLLs and a Windows `.exe`. That matches the markers in CLAUDE.md 7.2.
- Export: the game menu can export a game as a JGP package that carries files, metadata and the game's settings.
- Per-game settings: Settings has a "Game Selection" dropdown (All = global, or one game). A game-specific settings file is written inside the game folder: the owner's Infinite Fusion folder already contained a small `configuration.json` with only `{"cheats": false}`. A **compatibility database** (`gamedb.zip`, one JSON file) ships in the APK; its use is Unknown.
- Backup/Restore: a backup holds the game list, settings and internal data. The game list lives in private storage (not readable on this phone).

## 5. What the plugin does to a game at run time (observed in the per-game log)

JoiPlay's RPG Maker plugin writes `logs.txt` in the game folder. For Infinite Fusion it recorded:

- A **preload** script, then the game's own scripts, with a long list of **text patches applied to script source before it is evaluated** ("Applied patch: A to B"). Patch kinds seen: swap Ruby encoding constants for a string, remove `.freeze`, replace `Dir.pwd` with an empty string, wrap `eval` calls in an override hook (`MKXP.apply_overrides`), game-specific fixes (a bitmap-copy call gets an opacity argument), and turn keyboard-only input off. This matches `patches.json`: global file of `{author, comment, key, value}` triples, grouped by engine (`rpgm` 83 entries, `joiplay` 17, `rpgmmv` 17, `renpy` 6, `construct` 9, and empty `html`, `rpgmmz`, `tyrano`), including targeted Ren'Py fixes for named games.
- A **postload** script stage ("Executing postload scripts") and the line "Cheats are disabled."
- Requested game resolution `512 x 384` (the game's own size, scaled by the plugin).
- Files created in the game folder: `logs.txt`, `.online_path_cache` (a lowercased-path index so the case-sensitive Android filesystem behaves like Windows), `.nosearch`, and `UserData/{AppData,Temp}` (empty after one launch; very likely where the engine's per-user data and saves go, but no save was written in this run, so unconfirmed).

Lesson: "make unmodified Windows games run" is mostly path-case handling plus source-level text patches plus override hooks, driven by a shareable patch file. Our `engine-mkxp` should treat patches as data, not code.

## 6. Cheats (observed behaviour and settings)

- A single global/per-game switch "Enable Cheats". When on, a **Cheat button is added to the virtual gamepad** and **cheat scripts are injected** into the game. The documentation warns they work only on games that do not modify engine scripts.
- For RPG Maker MV/MZ the APK ships the injected JavaScript assets: a 58 KB cheat-menu plugin (`Cheat_Menu.js`), engine glue plugins (`JoiMV.js`, `JoiMZ.js`) and a performance plugin. For XP/VX/VX Ace the equivalent is the plugin's postload script stage plus the `cheats` flag in `mkxp.json`.
- No GameShark/Action Replay-style code entry exists, as expected for these engines (spec section 8.1).

## 7. Settings map (observed UI plus bundled documentation, paraphrased)

Seven groups, each global or per game: **App** (default game folder, theme: System/Wallpaper/Day/Night/Amoled, primary colour, auto-close, enable cheats, password lock, experimental features, content filter), **Gamepad** (hide pad, diagonal movement, button opacity and size, key mapping), **Ren'Py** (auto-save, hardware video, phone/small variant, vsync, low memory, fewer updates, model-based rendering, recompile scripts), **HTML** (local HTTP server, NW.js API shim, WebGL, desktop mode, external modules), **RPG** (debug log, Ruby 1.8 mode, custom font, smooth scaling, vsync, frame skip, fast-forward speed, solid fonts, path cache, prebuilt path cache, faster path enumeration, postload scripts, window size, vertical alignment, font scale, copy text to clipboard, and MV-only core-script/WebGL2/bitmap/Pixi options), **P.Essentials** (Pokémon Essentials fixes, input overrides, a tilemap workaround for oversized tilesets, disable autotile animation), **Ruffle** (render backend, quality, scale mode, load behaviour). Footer buttons: "Reset to Default", "Clear Runtime Packages", a save button.

The "P.Essentials" group is the part most relevant to the owner's games: JoiPlay ships Essentials-specific override scripts as a first-class setting. Our `cheats-script`/`engine-mkxp` should do the same.

## 8. Rendering, input and UI reference (summary; full spec in Task 8)

- In-game (observed, plugin screen, landscape): game image top-left, large black margins; a virtual keyboard-style pad: ENTER, F2, F8, ESCAPE at the top corners, an 8-way d-pad (left), Z/X, CTRL_LEFT, SHIFT_LEFT, Q, R in a diamond (right), a refresh/rotate icon. Pad and top bar are normal Android views (visible in `uiautomator`), not custom-drawn. A collapsible top bar reveals six icons: close, rotate, volume, keyboard, fast-forward, settings. A collapsible bottom handle exists too.
- Library (observed): Material-style app bar titled "JoiPlay" with Add, Filter, overflow; a grid of game cards (large icon, three-dot button, title, version, type text); layouts Grid small, Grid large and List.
- Settings (observed): back arrow and title, a "Game Selection" outlined dropdown, then collapsible group headers with chevrons; switches with descriptive sub-text; a floating save button.
- Look: dark default theme with background near `#1B1B1B`; light grey pad buttons (about `#B3B3B3` labels on dark `#4F4F4F` keys). Themes: System, Wallpaper, Day, Night, Amoled with a user-chosen primary colour.
- Orientation: plugin games are locked to landscape; JoiPlay's own screens followed the last rotation chosen in-game (the library stayed landscape after a game's rotate button), so rotation state is shared across the task.

## 9. Open questions

1. Exact extras for the RPG Maker plugin (known for the Ren'Py plugin: `game` and `settings` JSON strings; see `joiplay-renpy-plugin.md`). Confirming needs a decompiler or Frida, neither configured here.
2. How the 12-hex game id is derived.
3. Where Pokémon Essentials saves land (`UserData/AppData`?) and whether `File A.rxdata`-style saves can be copied in. Needs one real in-game save.
4. What `gamedb.zip` contains and how it is used.
5. The "RGA" patch format (bsdiff-based?).
6. How the MV/MZ path works end to end (WebView in main app, JoiMV/JoiMZ glue).
7. The plugin protocol for Ren'Py and Ruffle: covered in Tasks 6 and 7.

## 10. Test-run record (changes made on the phone)

With the owner's approval I pushed the 2.6 GB `InfiniteFusionAndroid.zip` to `/sdcard/PocketverseTest/`, imported it through JoiPlay, launched the game (twice, one of them by mistake), and then removed it from JoiPlay with "Delete folder" ticked. JoiPlay may keep a small amount of residual data: a log entry, shared preferences, per-game settings and its game-list entry. The plugin's `mkxp.json` now points at the deleted game folder. The test zip and my `/sdcard/PocketverseTest` folder were deleted, and the extracted game folder was deleted by JoiPlay. `/sdcard/JoiPlay` is back to `RTP/`, `patches.json` (unchanged, same size and date) and an empty `games/` folder that JoiPlay created. The owner's earlier JoiPlay game folder `InfiniteFusionAndroid` was already gone before this test (only `RTP` and `patches.json` existed), so nothing of the owner's pre-existing game data was touched.

## 11. Evidence trail

Main APK SHA-256 `b03831493b0d0c98c8d2690af06e413bb96eae92014157c3f4a65ca11aa5cad2`; RPG Maker plugin `78da7116de54bfcd61db1a47ec680cc4d3228e4ca920791858bced786335fbe9`; Ren'Py plugin `6e6e77ca868e7d94bcab867113d2708d1296432f74a8ed82ad84d322eb3271a4`. Methods: `adb pull` of the three APKs, `tools/axml_dump.py` manifest decode, zip inventory, string scans of dex and native libraries, the bundled documentation file (read, not reproduced), logcat around one launch, read-only shared-storage listings, `mkxp.json`, `patches.json`, per-game `logs.txt`, `uiautomator dump` and `screencap`.
