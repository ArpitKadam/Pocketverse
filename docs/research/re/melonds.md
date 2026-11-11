# RE note: MelonDS (Android)

Task 4, 2026-11-12. Written in our own words. MelonDS is open source (GPL-3.0), so the **upstream source is the primary reference** and the installed build was used only to confirm behaviour. Source read: `research/sources/melonds-android` at commit `aaf263d233` (2026-11-09, gitignored) and its `melonDS-android-lib` submodule at `431ab4bd00`. Raw captures stay in the gitignored `.re-workspace/`.

Labels: **Observed** = seen on the owner's phone or in the shipped package. **Source** = read in upstream code. **Inferred** = concluded from the above. **Unknown** = not determined.

## 1. Identity

| Field | Value | Basis |
| --- | --- | --- |
| Package | `me.magnum.melonds` | observed |
| Version | 2.0.1 PS, code 40 | observed |
| Meaning of "PS" | the `playStore` product flavor, which appends " PS" to the version name (the GitHub flavor appends " GH") | source: `app/build.gradle.kts` |
| Upstream | rafaelvcaetano/melonDS-android, GPL-3.0; release 2.0.1 on 2026-05-06 | Task 2 matrix |
| Source vs installed | the cloned source is newer than the 2.0.1 release (pushed 2026-11-09), so source findings may include post-release changes | inference |
| SDK | minSdk 24, targetSdk 36 | observed |
| ABIs in the APK | arm64-v8a, armeabi-v7a, x86_64 | observed |
| Native libraries | `libmelonDS-lib.so` (the DS core, 5.4 MB on arm64), `libmelonDS-android-frontend.so` (112 KB JNI glue), plus AndroidX graphics and CameraX helpers | observed |
| APK | 21 MB, single `classes.dex`, no split APKs | observed |
| Installer | Play Store | observed |

Permissions (observed): CAMERA and RECORD_AUDIO (DSi camera and DS microphone emulation, source-backed by `MelonDSAndroidCameraHandler` and the "Microphone source" setting), INTERNET, ACCESS/CHANGE_WIFI_STATE, CHANGE_NETWORK_STATE, NEARBY_WIFI_DEVICES (local-wireless emulation; unverified in this pass), VIBRATE, WAKE_LOCK, POST_NOTIFICATIONS, FOREGROUND_SERVICE and FOREGROUND_SERVICE_DATA_SYNC, RECEIVE_BOOT_COMPLETED. GL ES 3.0 is declared as required. No storage permission: all file access goes through the Storage Access Framework.

## 2. Components and the launch contract

| Component | Exported | Role |
| --- | --- | --- |
| `ui.romlist.RomListActivity` | yes, LAUNCHER, singleTop | library |
| `ui.romdetails.RomDetailsActivity` | no | per-ROM settings and PLAY |
| `ui.emulator.EmulatorActivity` | **yes**, singleTask | the emulation screen; accepts launch intents |
| `ui.settings.SettingsActivity`, `ui.inputsetup.InputSetupActivity` | no | settings, key mapping |
| `ui.cheats.CheatsActivity` | no | per-game cheat manager |
| `ui.layouts.LayoutListActivity`, `LayoutSelectorActivity`, `ui.layouteditor.LayoutEditorActivity` | no | on-screen controller layouts and editor |
| `ui.backgrounds.BackgroundsActivity` | no | screen backgrounds |
| `ui.dsiwaremanager.DSiWareManagerActivity` | no | DSiWare title manager |
| `ui.shortcutsetup.ShortcutSetupActivity`, `DSFirmwareShortcutSetupActivity`, `DSiFirmwareShortcutSetupActivity` | yes | `CREATE_SHORTCUT` pickers for home-screen shortcuts |

**Launch contract (a good model for ours):**

- Observed in the manifest: `EmulatorActivity` has an intent filter with actions `me.magnum.melonds.LAUNCH_ROM` and `me.magnum.melonds.LAUNCH_FIRMWARE`, category DEFAULT.
- Source (`LaunchArgs.kt`, `EmulatorActivity.kt`): the ROM is resolved in this order: an internal parcelable extra `rom`; the intent's data URI; a string extra `PATH` holding a plain file path; a string or Uri extra `uri`. Firmware boot uses boolean `boot_firmware_only` plus integer `boot_firmware_console` (the console enum's ordinal). Home-screen shortcuts are built as `LAUNCH_ROM` intents carrying `uri` = the ROM's document URI.
- Not tested on the device: starting the activity by intent from the shell. It would work from the source reading, but we did not need to run it.
- Lesson: a launcher-to-engine handoff can be one explicit action plus one path-or-URI extra plus a "firmware only" flag. No per-game config travels in the intent; per-game settings are looked up by the engine from its own database.

## 3. Architecture (source)

- UI: Kotlin, Jetpack Compose in newer screens (the in-game HUD is a `ComposeView`; the cheat screens look like Compose but were not confirmed) with AppCompat/XML preference screens for settings. Dependency injection with Hilt.
- Packages: `ui/*` (screens), `domain/*` (models and repository interfaces), `impl/*` (repositories: ROMs, save states, cheats via Room, settings via SharedPreferences, RetroAchievements, DSi NAND), `common/*` (URI handling, ROM processors, OpenGL context), `parcelables`, `migrations`.
- Cores and UI are separated by a thin JNI layer: `MelonEmulator.kt` / `MelonDSAndroidInterface.kt` on the Kotlin side, `MelonDSAndroidJNI.cpp` and friends (`CartLoader`, `EmulatorArgsBuilder`, camera and file handlers, an event messenger back to Kotlin) on the native side. The actual DS core lives in the separate repo `melonDS-android-lib` (a melonDS fork), pulled in as a git submodule. This is the same shape as our planned `engine-api` split.
- Rendering (source): `ui.emulator.EmulatorSurfaceView` is a `SurfaceView`; native code uses EGL with sync fences to present frames (`eglCreateSyncKHR`); a renderer and "threaded rendering" are settings. Optional external-display layout (`ExternalLayoutRender`).
- Audio: Oboe is a submodule (`app/src/main/cpp/oboe`); an audio bitrate/interpolation/latency model exists in settings.
- Other native bits: `faad2` (audio decoding, probably for DSiWare), `enet` (local network emulation).

## 4. On-disk layout and formats

### 4.1 Where things go (source, confirmed on the phone where noted)

| Item | Rule |
| --- | --- |
| ROM discovery | one "search directory" chosen by the user. On this phone it is `/storage/emulated/0/NDS Games` (observed). Extensions handled: `nds`, `dsi`, `ids`, plus `zip` and `7z` archives |
| Game name and icon | read from the ROM's banner (content-based); falls back to the file name. The library row for our test ROM showed the in-ROM title "Pokémon HeartGold" while the file name is "Pokemon Sacred Gold 1.05 -Complete.nds" (observed) |
| Battery save (`.sav`) | file name = ROM file name with its extension replaced by `.sav`. Raw cartridge save, no container (observed: the 524,288-byte file has no header). Location: next to the ROM if "Save next to ROM file" is on, else the chosen "Save file directory", else app storage `Android/data/me.magnum.melonds/files/saves` when the ROM's folder is unknown |
| Save states | `<ROM name without extension>.ml<N>`, N = 0..8. **Slot 0 is the Quick Slot** (observed: the load dialog listed "Quick Slot" with the fixture `.ml0`'s modification time, and slots 1-8 as empty). Location setting: same as the save directory (default), next to the ROM, or app storage `files/savestates` |
| State screenshots | PNG, stored in the app's **private** storage under `files/ss_screenshots/<hash of the ROM's URI>/<slot>.png`. They do not travel with the state file |
| Cheats | **not files**: stored in the app's Room database (private). Imported from XML databases (section 7) |
| Settings, ROM list, play time, layouts | Room database and SharedPreferences (private; not readable on an unrooted phone) |
| ROM cache | extracted-ROM cache with a size cap (default 8 GB; "Clear extracted ROM cache") |

On this phone, "Save file directory" reads "Not set", so with the default settings the saves and states sit next to the ROM in `NDS Games`. The app's external `files/` and `cache/` directories were empty.

### 4.2 Save-state container (observed on the owner's file, cross-checked with core source)

- The fixture `.ml0` (19,760,296 bytes) is **not compressed**. It starts with the ASCII magic `MELN`, then a 16-bit major version (13), a 16-bit minor version (0) and a 32-bit total length equal to the file size.
- After the header come sections, each with a 4-character tag and a 32-bit length: `RCHV`, `NDSG` (16.9 MB, the machine's main memory image), `DMA0`..`DMA7`, `ARM9`, `CP15` and more.
- The melonDS core source in `research/sources/melonds/src/Savestate.cpp` defines the magic and a major/minor check: a state with a different **major** version is rejected, a newer **minor** is rejected. The current upstream core is major 14; the owner's state is major 13. Whether the installed 2.0.1 core accepts it was not tested (we cancelled the load dialog).
- Consequence for Pocketverse: a state is bound to a core version. Importing the owner's old states (section 7.4 of the spec) must check the major version, keep the original untouched, and report incompatibility rather than guess.
- The `.sav` fixture (512 KiB) is a raw dump of the cartridge's flash, matching the game (the ROM header's 4-byte game code is `IPKE`, title "POKEMON HG").

## 5. Library, import and ROM details

- Library (observed): title bar "melonDS" with Search, Sort (Alphabetically, Recently played) and overflow (Boot firmware, DSiWare Manager, Refresh ROM list, Settings). Empty state text "No ROMs found". A row shows the banner icon, the title and the file name, plus a "Rom settings" button. Swipe-to-refresh exists in the layout.
- ROM details screen (observed): ROM display name, Boot system (Default), Microphone source (Default), Controller layout (Use global layout), GBA slot (None), a big PLAY button, "Play time", and CONFIGURATION / RETROACHIEVEMENTS tabs. This is the per-game config surface that our own `meta.json` should cover.
- Identification is by extension first, then header and banner (title, DSiWare flag). No hashing database was seen; RetroAchievements (separate feature) matches by its own hash.

## 6. Settings structure (observed)

Top-level groups: General (theme, generic), ROMs (search directory, icon filtering, ROM cache size), Save Files (save next to ROM, save directory, save-state location), System (internal firmware settings, custom BIOS and firmware, JIT toggle), Video (filter, threaded rendering, FPS counter), Audio (volume, latency, microphone source), Input (key mapping, layouts, haptic feedback), RetroAchievements (login), Cheats (enable cheats, import cheats).

System notes: the app can run without user-supplied firmware (internal firmware settings with a custom BIOS option implies a built-in replacement), JIT can be switched off, and the DS firmware can be booted on its own ("Boot firmware").

## 7. Cheats

- UI (observed): Pause menu > Cheats opens a per-game screen with folders. Default folder "My cheats". Top-right button toggles all; a red floating button adds a folder. Inside a folder, "New cheat" has Cheat name, Description and Code fields and a SAVE action. Settings > Cheats has "Enable cheats" and "Import cheats".
- Import (source and observed text): only **XML databases** are supported; importing deletes previously imported cheats. The parser (`XmlCheatDatabaseSAXHandler`) expects a `codelist` root, a database name, then `game` entries with name, game code and checksum, each with `folder` entries containing `cheat` entries (name, description, codes). This is the familiar R4-style cheat database layout; the code strings inside are Action Replay DS code lines.
- Applying: handled in the native core's Action Replay engine (core files `AREngine`, `ARCodeFile`, `ARDatabaseDAT` seen in Task 2). The Kotlin side only builds the enabled-cheat list per game.
- Not offered: GameShark DS or Code Breaker DS formats; no per-game cheat file on disk.
- Test material: the fixture folder contains `Documents/Action Replay Codes.pdf` (copyrighted guide, not copied anywhere). Task 8 can use code lines from it as test vectors only with the owner's approval.

## 8. UI reference summary (full spec in Task 8)

Raw captures: 19 screens in `.re-workspace/ui-reference/melonds/` (library, menus, settings pages, ROM details, in-game, pause menu, state slots, cheats).

- Look: dark theme. Status bar `#222222`, toolbar `#333333`, background `#303030`, dialogs `#424242`, accent red `#F44336` (folder icon, floating button, SAVE text, focused field). Toolbar 56 dp tall (98 px at 280 dpi).
- Library toolbar: title left; Search, Sort, overflow icons right. List rows: 84 px icon (48 dp), title, file name, config icon on the right.
- Emulation screen (portrait): the game surface fills the screen; a transparent control layer draws the buttons: L and R at the top corners of the control area, a middle row of four small buttons (icons only: a screen-layout/swap-style icon, a touch-hand icon, a microphone, fast-forward), a d-pad bottom left, an XYAB diamond bottom right, SELECT and START as small circles between. Control styling is flat translucent grey. HUD overlay (Compose) at the top for RetroAchievements.
- Pause menu (Back key): a list dialog titled "Pause": Settings, Save state, Load state, Cheats, Reset, Exit.
- Slot dialog titled "Save slot": Quick Slot, then 1..8 with date and time, or "<Empty>".
- Not analysed: screen arrangement options (stacked, side by side, single screen), because the screenshots caught the title screen only; the code (`ui.emulator.*`, `layouteditor.*`) and Task 8 will cover them. Landscape was not captured (same reason as Task 3).

Pocketverse must redraw all of this originally; no icon, bitmap or string is taken from the app.

## 9. Open questions

1. Does the installed core (state major 13 vs upstream 14) load the owner's `.ml0`? Needs one deliberate Load-state tap on the owner's own phone.
2. Screen layouts in portrait and landscape and the layout editor.
3. Firmware/BIOS requirements: what the built-in replacement does for DSi and for games that check firmware.
4. Exact Action Replay code grammar accepted by the core (master codes, conditional and loop opcodes): Task 8 reads the core source.
5. Per-game settings storage format (Room schema): readable in source, but not needed for migration.
6. Foreground-service purpose (likely ROM scanning or imports): not confirmed.

## 10. Test-run record (what was changed on the phone)

With the owner's approval I copied three files into `/sdcard/NDS Games/`: the Sacred Gold ROM, its `.sav` and its `.ml0`. Checksums of the `.sav` and `.ml0` on the phone matched the originals in `DemoGames` before and after the run (MD5 `d05db636...` and `580a09c5...`), and no new files appeared. I then deleted exactly those three files. The app's own database may still hold a library entry and a few minutes of play time for that ROM.

## 11. Evidence trail

Installed APK SHA-256 `6f74db8d43fce7f531e05528fa5f7d97a079ee93747720fd34af7a52b3891977`. Methods: `adb pull` of the app's APK, `tools/axml_dump.py` manifest decode, `dumpsys package`, zip inventory, upstream source reading, `uiautomator dump` and `screencap`, read-only `ls`, byte-level reading of the owner's fixture files, one normal in-app run with a temporary copy of the owner's files.
