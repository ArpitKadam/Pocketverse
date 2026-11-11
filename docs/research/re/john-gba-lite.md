# RE note: John GBA Lite

Task 3, 2026-11-12. Written in our own words. Nothing from the app (code, assets, screenshots) is committed; raw material sits in the gitignored `.re-workspace/`.

Labels: **Observed** = seen directly on the owner's phone or in the shipped package. **Inferred** = concluded from observations. **Unknown** = not determined.

## 1. Identity

| Field | Value | Basis |
| --- | --- | --- |
| Package | `com.johnemulators.johngbalite` | observed |
| Version | v4.15, code 4150 | observed |
| SDK | minSdk 23, targetSdk 36 | observed (decoded manifest) |
| Installer | Play Store | observed |
| APK | single `base.apk`, 22.9 MB, 4 `classes*.dex` | observed |
| ABIs shipped | arm64-v8a, armeabi-v7a, x86, x86_64, one library each: `libemuengine.so` (3.27 MB on arm64) | observed |
| Permissions | WRITE/READ_EXTERNAL_STORAGE, VIBRATE, FOREGROUND_SERVICE, POST_NOTIFICATIONS, INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, WAKE_LOCK, AD_ID and three ad-services permissions, plus one own signature-level permission for non-exported dynamic receivers | observed |
| Hardware features | touchscreen and gamepad both `required=false` | observed |
| Monetisation | AdMob banner and interstitial ads, Play Billing ("Purchase John GBA") | observed |

Package-level inference: the Java package `com.johnemulators.*` holds shared code for a family of emulators (the Theme dialog offers NES, SNES, GBC and GBA colour schemes, and the native library contains GB/GBC and SNES code). This is the GBA-branded build of that shared codebase.

## 2. Components and surfaces

Decoded from the binary manifest with `tools/axml_dump.py` and cross-checked against `dumpsys package`.

| Component | Exported | Role |
| --- | --- | --- |
| `activity.RomListActivity` | yes, LAUNCHER | library screen, singleTop |
| `activity.ImportActivity` | yes | `ACTION_VIEW` for `application/zip` over `file:` and `content:`; own task affinity `.import`, removed from recents. This is the only external "hand a game to the app" contract, and it takes zip archives only |
| `activity.GameActivity` | no | in-game screen; manifest orientation is "behind" (follows the activity underneath), so it is not locked |
| `activity.SettingActivity`, `AboutActivity`, `UpdateActivity` | no | settings, about, update prompt |
| `activity.StateListActivity` | no | save-state slot picker |
| `activity.CheatListActivity`, `GbaCheatListActivity` | no | cheat list; the GBA variant is the one used for GBA games |
| `activity.EditLayoutActivity` | no | on-screen controller layout editor |
| `activity.GamePadDeviceActivity`, `GamePadKeyMapActivity`, `GamePadKeyDetectActivity` | no | physical gamepad selection, key mapping, key detection |
| `gameprovider.GameProvider` | yes (authority `com.johnemulators.johngbalite.gameprovider`) | exported content provider. Purpose not examined (Unknown). For Pocketverse it is not a contract we need |
| `service.NotifyService` | no | foreground-style notification service |
| Google Mobile Ads, WorkManager, profile installer | mixed | library components, ignored |

`<queries>` names `com.johnemulators.dbxsync` (the "John DataSync" companion app) and the Play Store. The "Cloud settings" menu asks to install that companion before it can back up or sync app data.

**Handoff contract:** none for games. There is no intent action that takes a ROM path or URI and starts emulation. The only outside entry is the zip `VIEW` filter. Lesson for our own design: a launcher-to-engine contract needs an explicit, documented extra for the game path and per-game config; this app has none because it is a monolith.

## 3. Native engine

One shared library, `libemuengine.so`, loaded by the Java class `com.johnemulators.engine.EmuEngine`.

Observed JNI surface (36 methods on `EmuEngine`, 20 on `audioengine.AudioEngine`):

- Lifecycle and media: `openROM`, `closeROM`, `isOpened`, `reset`, `isROM`, `isROM2`, `getROMType`, `getSupportedRomType`, `getROMSize`, `getCurROMName`, `getCurROMNameExt`, `getCurROMPath`, `getCurROMCRC`, `getCurROMType`.
- Frames: `doFrameDirect`, `doFrameDirect2`, `convertTexBufToBitmap`, `getBitmapWidth`, `getBitmapHeight`, `getFrameRate`, `setEmulationThread`.
- Save data: `flushSaveRAM`, `saveState`, `loadState`.
- Cheats: `addCheatCode`, `clearCheatCode`, `isValidCheatCode`.
- Sound parameters: `getSoundSampleRate`, `getSoundChannels`, `getSoundBufferLength`, `getSoundLastWrittenSize`, `setSoundEnabled`.
- GB/GBC extras: `setGBCCustomPalette`, `setGBCGraphicSettings`.
- Audio engine: output open/close/write/play/pause/stop/flush, volume, playback rate, plus pitch and time-stretch processors.

What the symbols tell us (inferred, from exported and internal names in the library's string table):

- The GBA core is of the VisualBoyAdvance family. Evidence: functions named `cheatsAddGSACode`, `cheatsAddCBACode`, `cheatsCBADecrypt`, the string "Unsupported VisualBoyAdvance save game version %d", and the HLE BIOS routines (`BIOS_CpuSet`, `BIOS_LZ77UnCompWram` and so on, taking a `_GBAEnv`). "HLE BIOS" means it emulates BIOS calls in software, so it needs no BIOS file for most games.
- The same library also contains Super Nintendo code (names prefixed `Sfc...`, e.g. `SfcAddCheat`, `SfcProActionReplayToRaw`) and Game Boy / Game Boy Color code (`gbCheatUpdateMap`). BIOS-file names `BS-X.bios` and `STBIOS.bin` appear for the SNES side.
- Link dependencies: `libOpenSLES.so`, `libjnigraphics.so`, `libz.so`, `liblog.so`. There is **no** OpenGL ES reference in the native library.

Licence note: the VBA family is GPL-licensed, so the lineage is a reminder that "closed" apps can contain open code. It changes nothing for us. We still never copy from this app, and we take VBA-family behaviour from upstream (`visualboyadvance-m`, researched in Task 2).

## 4. Rendering, input, audio (architecture level)

- **Rendering (inferred):** software framebuffer. The native side fills a buffer, `convertTexBufToBitmap` and `libjnigraphics` turn it into an Android Bitmap, and a custom view (`emulation.EmuView`, shown as `game_emuview` in the UI dump) draws it. The virtual pad is drawn inside that same view: the UI dump shows no child views for the buttons, so touch regions are custom-drawn. The Java side has an `EmuThread` and a `FrameStabilizer` class for pacing.
- **Audio (inferred):** native OpenSL ES output (library link) with a Java `AudioEngine`/`EmuAudio` layer; the app's dex strings also reference `AudioTrack` (this could be a library string), so a Java-side fallback path is plausible. Which path is active on this device is Unknown. "Low latency mode" in Settings is the user-facing switch.
- **Input:** `EmuKeyPad` (physical keys), `EmuVirtualPad` (touch), plus the three GamePad activities for device choice, mapping and detection. Vibration strength and a "Dpad diagonals area size" are configurable.
- **Storage access:** `fileutils.DocumentUtils` and `DocFileEx` indicate Storage Access Framework (document tree) access, and the About screen prints "Scoped storage mode". This matches the shared folder below.

## 5. On-disk layout (observed)

Root: `/sdcard/Johnemulators/` (contains a `.nomedia` file so the gallery ignores it).

```text
Johnemulators/
├── .nomedia
├── game/                  (empty, top-level)
└── GBA/
    ├── game/              Pokemon Dreamstone Mysteries.gba          (the ROM, owner-placed)
    ├── save/              Pokemon Dreamstone Mysteries.sav          (131072 bytes, raw battery save)
    ├── state/             Pokemon Dreamstone Mysteries.jg0 / .js0   (state / thumbnail, slot 0)
    ├── autostate/         Pokemon Dreamstone Mysteries.jga / .jsa   (resume state / thumbnail)
    └── cheat/             (empty; no cheat file created yet)
```

Facts established by running the game once with the owner's approval (backup taken first into `.re-workspace/backups/johnemulators-pre-launch/`; the new files were left in place):

- All per-type folders are flat and keyed by the ROM's base file name. There is no per-game folder, and no per-game config file was observed. Two ROMs with the same file name would collide.
- `.sav` appears on first launch at 131072 bytes (128 KiB). Matches the owner's fixture save.
- `.jg0` is a gzip stream. Decompressed it is 740,238 bytes and starts with the 32-bit little-endian value 10, followed by the 12-byte game title and 4-byte game code from the cartridge header (e.g. `POKEMON EMERBPEE`). That is the layout of a VBA-style save state, version 10. The owner's fixture `.jg0` decompresses to the same size with `POKEMON FIREBPRE`, so the format is stable across ROMs and across the owner's older files.
- `.js0` and `.jsa` are JPEG thumbnails (JFIF header), one per state.
- `.jga` is a second gzip VBA-style state of the same decompressed size. It is rewritten while the game runs (modified time kept advancing during play), so autostate is periodic, not exit-only.
- The state-list screen names slot 0 "Slot1 (Quick save)" and offers "Undo loading state". The toolbar's down-arrow icon in the game wrote the quick-save slot (the state file appeared at that moment). Slot numbering beyond the quick-save slot was not observed (Unknown).
- The cheat directory's file format is Unknown: nothing was written because no cheat was saved. A native cheat-list loader/saver exists, with messages "Unsupported cheat list version %d", "Unsupported cheat list type %d" and "cheats are for game %s. Current game is %s", so the list file is versioned, typed and stores a game identifier.

Private data under `/data/data/` is not readable on this unrooted phone, so config storage (probably shared preferences) was not examined.

## 6. Library, import and game identification

- Library: one list with three views from the drawer (Favorites, History, All). Each row shows a thumbnail (black until played), the title taken from the file name, a status line ("Not played", otherwise last-played info) and the platform plus size ("GBA 32.00 MB"). Toolbar: search, game folder picker, import file, overflow (View, Rescan storage).
- Import: ImportActivity takes zip archives; the file-import button and the game-folder picker cover loose files. The app scans the `GBA/game` folder ("Rescan storage").
- Identification: ROM type detection is native (`isROM`, `getROMType`, `getCurROMCRC`), so identification is content-based with a CRC. How the CRC is used (database, cheat matching) is Unknown.

## 7. Cheats (observed UI and inferred internals)

- Entry points: in-game menu "Cheats" and the "Enable cheating" switch under Settings > Other. Cheating is off by default.
- Cheat list screen (empty here): title "Cheats", toolbar actions Add and Help.
- Add dialog fields: Name (placeholder "New code"), Cheat type, Code (hint `00112233:AABB / 00112233 AABBCCDD`). Buttons: Help, Cancel, OK.
- Cheat type choices: Unspecified, Raw code, GameShark, GameShark v3, CodeBreaker. "Unspecified" means auto-detect.
- Help text lists the accepted shapes: raw 8/16/32-bit (`00112233:AA`, `:AABB`, `:AABBCCDD`), GameShark (`00112233AABBCCDD`), GameShark v3 (`00112233 AABBCCDD`), CodeBreaker (`00112233 AABB`).
- Native helpers (inferred from names): per-format verify and add functions, CodeBreaker decryption and CRC helpers, GameShark v1 decryption, a GameShark code-file importer, enable/disable/delete, per-frame key checks for conditional codes.
- Not offered in this build: Action Replay v3 as its own type, and no cheat database browser. Action Replay codes are not named in the UI; whether they work as "GameShark v3" is Unknown.

## 8. UI reference summary (full spec comes in Task 8)

Raw screenshots, uiautomator dumps and a capture helper are in `.re-workspace/ui-reference/johngbalite/` (19 screens/states; gitignored).

Screens captured: library (portrait), navigation drawer, library overflow menu, Settings (three scroll positions), Theme dialog, Cloud settings dialog, About, in-game portrait with virtual pad, in-game menu, Cheats list, Add-cheat dialog (with type spinner), Cheats help, interstitial ad, State list, Controller Settings dialog, Speed dialog.

Design facts (measured on a 720x1600 px, 280 dpi screen, so 1 dp = 1.75 px):

- Toolbar 48 dp tall (84 px) under a status bar in the same colour. Default theme "Orange (GBA)": toolbar and status bar `#FF9C00`, list background `#303030`, drawer `#FAFAFA` with selected row `#DCDCDC`, dialogs `#424242` over a dimmed scrim. List title text `#C1C1C1`. Other themes: Red (NES), Gray (SNES), Green (GBC), Blue, Red, Purple, Pink, Black.
- Library row: 130 px (about 74 dp) tall, thumbnail about 80 x 72 dp on the left, two text lines. Toolbar has four 48 dp icon buttons on the right.
- Drawer: width about 490 px (280 dp), rows 48 dp tall, grouped as Favorites / History / All, a divider, then Settings / Theme / Cloud settings / Help / Purchase / About.
- Settings: flat preference list with section headers Quality (quality mode, layouts, orientation, clock and battery meter), Video (smoothing), Audio (enable, volume, low latency), Virtual pad (auto-hide, vibration, dpad diagonal area size, enable A+B between A and B), Gamepad (settings), Other (load autosave at startup, enable cheating, debug FPS).
- In-game portrait: top banner ad row, a 48 dp action row (down-arrow = quick save, observed; up-arrow presumably quick load, not tested; fast-forward; menu), the game image scaled to full width, then the virtual pad in the lower third: L and R pills at the top corners, d-pad left, A and B circles right (A above B), SELECT and START pills bottom centre, clock at bottom-left and battery percent at bottom-right.
- In-game menu (modal list): Load state, Save state, Settings, Reset, Controller Settings, Screenshot, Speed, Cheats, Purchase John GBA, Exit.
- Speed choices: x0.25, x0.5, x1 (default), x2, x4, x8, x16. Controller Settings: Hide virtual pad, button A turbo, button B turbo, toggle L/R.
- Landscape was not captured (orientation changes need a system setting, so it is left for the owner to rotate; see open questions).

Pocketverse must redraw every one of these elements originally and drop the ads and the purchase upsell.

## 9. Open questions

1. Landscape layout and the EditLayout screen (needs rotation or taps inside the editor).
2. Cheat list file format on disk (needs a saved cheat; would write one file).
3. Content and purpose of the exported `GameProvider`.
4. Slot numbering beyond the quick-save slot, and what "History" records.
5. Audio path actually used (OpenSL ES vs AudioTrack).
6. Where preferences and per-game settings (layout per resolution, smoothing) live; private storage is not readable.
7. How DataSync backups are laid out (companion app not installed here).
8. Whether Action Replay codes work under "GameShark v3".

## 10. Evidence trail

APK SHA-256 `317b0c55b36fd00b49ee0ba66f46381826903eef680e5d4f5eb7693994416706`; arm64 library SHA-256 `4b363b85e1f17bba0464128b6d914ed8d7c9de141d85a93b10eb8456758a15ce`. Methods: `adb pull` of the app's own APK, binary manifest decode, `dumpsys package`, zip inventory, string table of the shared library, `uiautomator dump`, `screencap`, read-only `ls` of shared storage, and one normal in-app launch of the owner's ROM.
