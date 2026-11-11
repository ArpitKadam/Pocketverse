# RE note: JoiPlay Ren'Py plugin

Task 6, 2026-11-12. Written in our own words. The plugin APK is closed-source in its shipped form, but JoiPlay publishes the plugin's source under the upstream licences (the plugin's own info page links it, as the LGPL requires). So this note rests on three kinds of evidence, labelled throughout: **Observed** (installed APK or the owner's phone), **Source** (read in JoiPlay's public repositories, cloned into the gitignored `research/sources/`), **Inferred**. Nothing from the APK or the repositories is copied into this project.

## 1. Identity

| Field | Value | Basis |
| --- | --- | --- |
| Package | `cyou.joiplay.renpy` | observed |
| Version | `8.1.3_7.6.3_001-patreon`, code 813001 (supporter build, sideloaded) | observed |
| Meaning of the version | two Ren'Py runtimes in one APK: **8.1.3** and **7.6.3** | observed (two native engines, below) |
| SDK | minSdk 21, targetSdk 34, GL ES 3.0 required | observed |
| APK | 47 MB, one 309 KB `classes.dex`, native libraries for arm64-v8a and armeabi-v7a | observed |
| Permissions | all-files access, storage read/write, media read (images, video, audio), VIBRATE, WAKE_LOCK, INTERNET | observed |
| Plugin first run | never launched before this task: `Android/data/cyou.joiplay.renpy/files` was empty | observed |

Native libraries (arm64): `librenpython.so` (25.4 MB, the Ren'Py 8.1.3 engine, embedded Python 3), `librenpython7.so` (22.4 MB, the Ren'Py 7.6.3 engine, embedded Python 2) and `libLive2DCubismCore.so` (426 KB). SDL2 2.0.20 and FFmpeg are linked in. **Licence hazard:** the Live2D Cubism Core is Live2D's proprietary SDK; it is the only non-free-software component seen. Pocketverse must not bundle it without Live2D's licence agreement. Live2D support is a niche Ren'Py feature, not needed for our baseline.

## 2. Upstream lineage (the clean references)

| What | Where | Licence | Notes |
| --- | --- | --- | --- |
| The engine | `github.com/joiplay/renpy`, a fork of Ren'Py with one branch per version pair (e.g. `8.1.0_7.6.0`, newest `8.3.x_7.8.x`). Our clone: branch `8.1.0_7.6.0`, commit `5502175f8f` (2023-05-19) | Ren'Py: MIT plus LGPL-derived parts (see Task 2) | The installed 8.1.3/7.6.3 build has no public branch of that exact name, so the 8.1.0 branch is the nearest reference |
| The Android packaging | `github.com/joiplay/renpy-build`, a fork of Ren'Py's `renpy-build` with the RAPT Android prototype. Our clone: commit `e762a1294b` (2021-11-04, older than the installed build) | no licence field; Ren'Py terms | The plugin's `PermissionActivity` and `PythonSDLActivity` live under `rapt/prototype` |
| The shared contract library | `github.com/joiplay/commons` (commit `e711f047d2`) and the on-screen pad library `github.com/joiplay/joipad` (commit `16bd910b6e`) | **MIT** (checked from the LICENSE files) | `commons` holds the game and settings data models and JSON parsers used by every JoiPlay plugin |

The plugin describes itself (info page, observed) as a derivative of Ren'Py's official Android build (RAPT) and of Ren'Py, with LGPL, zlib and MIT components, and it asks that derivatives comply with the LGPL. This matches Task 2: Ren'Py's licence is MIT plus LGPL-derived parts, so an app that embeds the runtime must keep notices and let users replace the LGPL parts.

## 3. Launch contract (JoiPlay main app to this plugin)

**Observed on the phone:** launching the test game logged `START u0 {flg=0x10000000 cmp=cyou.joiplay.renpy/.PermissionActivity (has extras)}` from JoiPlay's user id, then the plugin's process started with `PermissionActivity`, and after the permission steps `org.renpy.android.PythonSDLActivity` took the foreground.

**Source (plugin's `PermissionActivity` and `PythonSDLActivity`):**

1. JoiPlay starts `PermissionActivity` with explicit component and two string extras:
   - `game`: a JSON object with `title`, `id`, `folder`, `execFile`, `type` (defaults: type `rpgmxp`, exec `Game.exe`; the same model serves every plugin).
   - `settings`: a JSON object with sections. `renpy` holds each Ren'Py option as `{ "<name>": { "boolean": <true|false> } }` (7 options: `cheats`, `renpy_hw_video`, `renpy_autosave`, `renpy_phonesmallvariant`, `renpy_vsync`, `renpy_less_memory`, `renpy_less_updates`). `gamepad` holds button opacity, scale and six or more key-code mappings (A, B, C, X, Y, Z, L, R). The shared library also has an `mkxp` configuration model whose values are `{ "string": ... }` or `{ "boolean": ... }` objects, which is the RPG Maker plugin's section (inferred to use the same envelope).
2. `PermissionActivity` checks storage permission, and on Android 11 and later requires all-files access: if missing, it shows its own "needs all files access" dialog and sends the user to the system settings page (observed exactly this on the device, with the OK button leading to the "Allow access to manage all files" toggle). If the intent has no extras it shows "got no data".
3. When permission is in place it starts `PythonSDLActivity`, **forwarding the same extras unchanged**.
4. `PythonSDLActivity` parses the two JSON strings into a game model, a Ren'Py configuration model and a gamepad model, builds the on-screen pad (joipad), then prepares the Python process by setting environment variables (next section) before the engine starts.
5. No result is returned to JoiPlay. The user closes from the pad's top bar; Android returns to JoiPlay's task (observed).

Consequence for Task 5's open question: the intent extras are `game` and `settings` (JSON strings) for this plugin. The RPG Maker plugin is closed, but it was built from the same shared library (it has the `mkxp` model) and showed the same `START ... PermissionActivity (has extras)` pattern, so the same two extras are very likely (inferred, not source-verified for that plugin).

## 4. Environment variables (the plugin-to-engine contract)

Source-read in the fork's `main.py` and the packaging activity, and the full set of names was **also found in the installed build's `classes.dex`** (observed), so the installed plugin matches:

| Variable | Set from | Effect in the engine |
| --- | --- | --- |
| `ANDROID_PUBLIC` | the game's folder (`game.folder`) | base directory; the game itself is `<base>/game`; logs go here |
| `ANDROID_PRIVATE`, `ANDROID_APK`, `ANDROID_OLD_PUBLIC` | plugin's files dir, APK path, same as public | runtime unpack location and APK access |
| `ANDROID_EXTRAS` | optional | extra search path added to the game's search path |
| `RENPY_PATCHPATH` | path to JoiPlay's `patches.json` | JSON file; its `renpy` array of `{key, value}` text replacements is applied to **script source as it is loaded** (the engine logged "Loaded 6 patches" on our run, matching the 6 entries in the owner's file) |
| `JOIPLAY_SAVEDIR` | `<game folder>/saves`, or an app-private folder when the game is outside shared storage on Android 10 and older | where saves are written (observed: "Saving to .../MiniVN/saves") |
| `JOIPLAY_AUTOSAVE`, `JOIPLAY_HW_VIDEO`, `JOIPLAY_VARIANT_PHONE` | the three matching settings | autosave on choice, hardware video, force the "small/phone" UI variant (the fork changes Ren'Py's own screen-size heuristic to this switch) |
| `RENPY_GL_VSYNC`, `RENPY_LESS_MEMORY`, `RENPY_LESS_UPDATES`, `RENPY_RENDERER=gles` | settings | rendering knobs (observed in log: "Initializing gles2 renderer") |
| `RENPY_RECOMPILE`, `RENPY_DONT_USE_GL2`, `ANDROID_TRAP_BACK_BUTTON` | extra settings in the installed build | recompile scripts every launch, avoid the GL2 renderer, capture the Back key |

## 5. What the fork changes in Ren'Py (source diff against upstream 8.1.0, 15 files, about 140 lines added)

Behaviour, in plain words:

- **Patches as data:** a `patch_list` config entry and a loop that replaces text in each script file as it is read. This is the main compatibility tool: named per-game fixes (for example reordered arguments in specific visual novels) live in `patches.json`, not in code.
- **Android paths:** game directory, base directory and save directory are taken from the environment instead of from Ren'Py's packaged-APK layout; the asset-pack lookup of the stock Android build is removed.
- **Phone variant switch** and **hardware-video fallback** (a movie on a channel that would crash with hardware video now turns hardware video off instead of raising).
- **Extra audio channel** `joimovie` registered for movies.
- **Tolerance patches:** an unknown preference name returns nothing instead of raising; a missing tooltip or empty narrator window is caught; a missing font falls back to the bundled DejaVu font; a `store` reference is added to three evaluation contexts; a game-specific flag (`developer_show_all_exceptions`) exists "for a certain game".
- **Developer console always on:** the guards that restrict the console and developer helpers to `config.developer` are removed, and a 318-line developer menu file is dropped. This is the likely hook behind the plugin's **Cheats button** (observed in the top bar): Ren'Py's own console lets a player read and set story variables. The exact cheat mechanism was not traced (Unknown); the in-APK assets include an injected `z_joi_patch.rpy` (init script that forces the quick menu on).
- **Key remaps:** extra keymap entries (`n` hides windows, `w` opens the game menu).

## 6. APK contents (observed)

- `assets/x-renpy/x-common/`: Ren'Py's own `common` scripts (about 268 files), with `.rpy.orig` backups beside the four files the fork edited (`00compat`, `00console`, `00library`, `00preferences`). The name prefix `x-` is the Android asset packer's convention.
- `assets/x-game/x-z_joi_patch.rpy`: a 116-byte script injected into every game, loaded last by its `z_` prefix.
- `assets/private.mp3`, `assets/private7.mp3`: gzip archives disguised as MP3 (a standard Android packaging trick to avoid asset compression). At launch the plugin extracted `private7.mp3` to `files/private7` (observed in log). They carry the Python library and the Ren'Py engine files for 8.x and 7.x respectively.
- `assets/html/info.html` and `license.html`: the plugin's notices (LGPL 2.1 text and a component list).
- No game files, no engine selection UI beyond a launcher screen that says it is a plugin and not a standalone app.

## 7. Runtime layout on disk (observed with the synthetic test game)

Game folder `<anywhere>/MiniVN/` (the game stays where it is; JoiPlay's "Add Game" does not copy):

```text
MiniVN/
├── MiniVN.py          launch stub the user points JoiPlay at
├── game/              the game; compiled script.rpyc and cache/ (screens, analysis, bytecode) appear here
├── renpy/  lib/       placeholders I added so JoiPlay would classify the folder (see section 8)
├── saves/             Ren'Py save directory (here only a sync folder was created)
├── log.txt            the engine's own log, rewritten each launch
├── .nomedia, .nosearch
```

Plugin private data: the extracted runtime under `files/private7/` (not readable on this unrooted phone). JoiPlay's shared folder `/sdcard/JoiPlay/` contains `patches.json`, which the plugin reads through `RENPY_PATCHPATH`.

**Which runtime was used:** the engine reported `Ren'Py 7.6.3.23091805` for my synthetic game. Why it chose 7.x instead of 8.x is **Unknown**: the log calls `getRenPyVersion()` first, so it probes the game. My game had no engine files, so the 7.6.3 fallback may simply be the default. A real 8.x game (with `renpy/` and `lib/` from its own distribution) would show whether the probe reads those.

Observed engine log content: device model, screen diagonal (5.89 in), Ren'Py version, search paths (the game's `game` folder, then the plugin's private runtime `game`), "Loaded 6 patches", the save path, autosave off, hardware video on, GL ES renderer on an Adreno 619 with a 4096x4096 maximum texture size, a virtual resolution of 800x600 on a 1523x720 physical area, and a 3.4 s start-up to interface-ready.

## 8. Detection finding in JoiPlay (main app)

JoiPlay silently refused my first test folder (`game/script.rpy` and a `.py` launcher only): no error, no card in the library. After adding empty-placeholder `renpy/` and `lib/` folders it classified the game as **"Ren'Py"** immediately. So the Ren'Py detector needs the distribution-style folders (our spec section 7.2 lists `renpy/`, `game/*.rpy|rpyc|rpa` and `lib/`, which is consistent), and the missing-marker path fails without telling the user. For Pocketverse: always explain why a folder is rejected (spec 7.2 already says so).

## 9. Cheats and settings (what the user sees)

- Ren'Py settings group in JoiPlay (observed text, from Task 5): auto save, HW video, phone/small variant, VSync, less memory, less updates, model-based rendering, recompile scripts. The plugin's top bar adds a **cheats** icon (instead of the RPG Maker plugin's fast-forward icon) next to close, rotate, volume, keyboard and settings. The pad is an overlay of Android views, not engine-drawn (observed in the UI dump).
- Pokémon-style code entry does not exist for Ren'Py; the console route above is the equivalent, as the spec says (section 8.1).

## 10. Test-run record (changes made on the phone)

With the owner's approval I created a 3-file synthetic project (my own text, no third-party content) in `/sdcard/PocketverseTest/MiniVN`, added it in JoiPlay, launched it twice, then removed it from JoiPlay with "delete folder" ticked and removed my folder. `/sdcard/PocketverseTest` no longer exists and `/sdcard/JoiPlay` is unchanged.

**Permission changes (owner chose "grant and leave"):** the Ren'Py plugin was granted photos/videos, music/audio and "all files access" (it needs them to launch any game). These stay enabled. The earlier lesson holds: a JoiPlay plugin always needs all-files access, which supports the Task 5 storage conclusion.

## 11. Open questions

1. How the plugin chooses between the 8.1.3 and 7.6.3 runtimes for a real game.
2. The exact mechanism behind the Cheats button (console hook vs an injected cheat script).
3. Behaviour with a real 8.x game, RPA archives, Live2D, hardware video.
4. The `gamepad` settings JSON fields in full (read from `commons`, not needed for our design).
5. Whether the installed 8.1.3 build differs from the public 8.1.0 branch beyond version bumps (no public branch of that name).

## 12. Evidence trail

Plugin APK SHA-256 `6e6e77ca868e7d94bcab867113d2708d1296432f74a8ed82ad84d322eb3271a4`. Methods: `adb pull`, `tools/axml_dump.py`, zip inventory and Python extraction of assets, string scans of the native libraries and `classes.dex`, shallow clones and diffs of `joiplay/renpy` (against upstream tag `8.1.0.23051307`), `joiplay/renpy-build`, `joiplay/commons`, `joiplay/joipad`, logcat around two launches, `uiautomator dump`, `screencap`, read-only listings.
