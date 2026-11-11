# UI Spec: NDS mode (reference: MelonDS for Android)

Status: draft for owner review, 2026-11-12. Phase 0 deliverable (CLAUDE.md section 9.3). Nothing here is implemented yet.

Evidence labels used throughout:

- **Observed**: seen in the owner's own captures (`.re-workspace/ui-reference/melonds/`, 720x1600 px, 280 dpi, so 1 dp = 1.75 px) or in the uiautomator dumps next to them.
- **Source**: read in the upstream GPL-3.0 repo at `research/sources/melonds-android` (commit `aaf263d233`, newer than the installed 2.0.1). Paths below are relative to `app/src/main/java/me/magnum/melonds/`.
- **Eyeballed**: measured by hand from a screenshot because the element is custom drawn and absent from the view hierarchy.
- **Not observed**: exists per source or per the RE note, but we have no capture of it.
- **Proposed**: our own default; not a reference fact.

## 1. Purpose, scope, legal line

NDS mode is the Pocketverse UI skin that recreates the layout, flow, density, colour mood and feel of MelonDS for Android, so `.nds` games feel the way the owner is used to. It covers the library, per-game details, settings structure, the emulation screen with its touch-controller layer, the pause menu, save-state slots and the cheat screens.

Legal line (CLAUDE.md 2.1 and 9.2):

- We copy layout, flow and interaction design. We copy no artwork: no icons, launcher art, bitmaps, skins, fonts, screenshots or string text. All strings in this spec that are quoted from the reference are quoted as observation only; Pocketverse writes its own wording with the same structure and tone.
- No Nintendo, DS or Pokemon names, logos or boot sounds. The "DS shell" is never drawn; the game surface is shown bare.
- Controller glyphs (d-pad, X/Y/A/B diamond, L/R, microphone, fast-forward) are redrawn as Compose vectors. Button letters are plain text in an OFL font.
- The app is GPL-3.0 upstream, but we still treat it as a reference for behaviour, not a source of code or assets.
- Colour and size values are facts and are recorded; token names are ours (`pv.nds.*`).

Out of scope for this spec: DSiWare Manager, RetroAchievements tab, Boot firmware, input key-mapping screen, Video/Audio/Input/General settings pages (listed in the settings root only, not captured), layout list/editor screens (source only, section 5).

## 2. Screen inventory

Ids are the Android `resource-id`s from the uiautomator dumps (Observed). "Our id" is not assigned yet; Phase 6 gives each a Compose test tag.

### 2.1 Library, empty (`01_romlist`)
- **Purpose:** list of games; first-run empty state.
- **Entry/exit:** launcher entry. Exits to Search (inline), Sort popup, overflow popup, ROM details (tap row), ROM settings (row gear).
- **Elements:** status-bar background view (`view_status_bar_background`, 77 px = 44 dp), toolbar (`toolbar`, 98 px = 56 dp), title "melonDS" (left, x = 28 px = 16 dp), actions `action_search_roms`, `action_sort`, overflow ("More options"), pull-to-refresh container (`swipeRefreshRoms`), list (`listRoms`), empty-state text (`textRomListEmpty`, centred, text "No ROMs found").
- Empty text is centred horizontally and sits at about 55% of screen height (y 866-909 of 1600).

### 2.2 Library with a ROM (`09_romlist_with_rom`)
- **Purpose:** the populated list.
- **Row (`layoutRomItem`, 112 px = 64 dp tall):** icon `imageRomIcon` (84 px = 48 dp, at x = 14 px = 8 dp), title `textRomName`, secondary line `textRomPath` (file name), trailing button `buttonRomConfig` ("Rom settings", 84x84 px = 48 dp, gear glyph). A 1 px divider (`#494949`) closes the row.
- Gear opens ROM details (Observed). Tapping the row is assumed to launch the game; that tap was not captured and is unverified. Confirm on device (open question 2).
- Title is the in-ROM banner title; the secondary line is the file name (Observed: "Pokemon Sacred Gold 1.05 -Complete.nds" under the banner title).

### 2.3 Overflow menu (`02_overflow`)
- Popup anchored under the overflow icon, right-aligned, 343 px = 196 dp wide, rows 84 px = 48 dp (`content`, `title`). Starts at y = 168 px, i.e. overlapping the toolbar's lower edge (anchored to the icon bottom).
- Items in order: Boot firmware (with a submenu arrow `submenuarrow`, 42 px = 24 dp), DSiWare Manager, Refresh ROM list, Settings.
- Text inset 28 px = 16 dp from the popup edge (Observed: title x=398 vs popup x=370).

### 2.4 Sort menu (`03_sort`)
- Popup under the sort icon, 343 px wide, rows 48 dp. Each row: leading icon `icon` (24 dp, x offset 14 px = 8 dp) then `title`. Items: Alphabetically, Recently played. Which one is currently selected is shown by the icon; selected-state styling was not distinguished (open question 6).

### 2.5 Settings root (`04_settings`)
- Toolbar with Navigate-up button (`ImageButton`, 98x98 px = 56 dp square at x=0) and title "Settings" at x = 126 px = 72 dp.
- Container `settings_container` > `list_container` > `recycler_view`. Nine rows, each 128 px = 73 dp: leading icon frame (`icon_frame`, 98 px wide, icon 42 px = 24 dp at x = 28 px = 16 dp), `title`, `summary`.
- Rows in order: General (Theme, generic settings), ROMs (search directory, icon filtering, cache settings), Save Files (save file and save state locations), System (firmware settings, custom BIOS, JIT), Video (filter, threaded rendering, FPS counter), Audio (volume, latency, microphone source), Input (key mapping, layouts, haptic feedback), RetroAchievements (login and manage), Cheats (enable and import).
- Text left edge x = 126 px = 72 dp (icon column 56 dp wide plus 16 dp gutter).

### 2.6 Settings: ROMs (`08_roms`)
Rows: Search directory (summary shows the path), Icon filtering ("None (Pixelated)"), Max. ROM cache size (summary "8GB" plus a `seekbar`, 664 px = 380 dp wide), Clear extracted ROM cache ("Cache size: 0B"). Row heights: 128 px for title+summary, 184 px for the seek-bar row.

### 2.7 Settings: Save Files (`05_save_files`)
Rows: Save next to ROM file (switch `switch_widget`, `widget_frame` 110 px wide at the right, row 94 px = 54 dp), Save file directory ("Not set"), Save state location (summary "Save file directory").

### 2.8 Settings: System (`07_system`)
Rows: Internal firmware settings (summary), Custom BIOS and firmware (no summary, 94 px row), Enable JIT (summary: improves performance but may cause freezes or crashes; switch). The JIT row is 158 px tall because the summary wraps to two lines.

### 2.9 Settings: Cheats (`06_cheats`)
Rows: Enable cheats (switch), Import cheats (long explanatory summary, 188 px row: XML databases only, replaces previously imported cheats).

### 2.10 ROM details (`10_rom_config`)
- **Purpose:** per-game settings and the PLAY entry point.
- **Entry/exit:** from the row gear; Back or the up arrow returns to the library; PLAY starts the emulation screen.
- **Elements:** up arrow (84x84 px button at x=7), banner icon (74 px, about 42 dp), game title in the toolbar (x=228 px), PLAY button (231x63 px = 132x36 dp, fully rounded, accent fill, bold caps label), "Play time: 0m" to its right, a two-tab strip (CONFIGURATION, RETROACHIEVEMENTS; active tab has a 2-3 px accent underline, inactive an `#494949` line), then five two-line rows (112 px = 64 dp): ROM display name, Boot system (Default), Microphone source (Default), Controller layout ("Use global layout"), GBA slot (None).
- The tab strip is full width, tabs 360 px (205 dp) each, equal split. Tab labels are upper-case, bold, with letter-spacing.

### 2.11 Emulation screen, portrait (`11_game_portrait`, `12_game_after_wait`)
See sections 4.3, 5, 6.
- A black full-screen surface (the game), the control layer on top, and a Compose HUD (`layout_achievement`, 0,0 to 720x77) that is empty unless an achievement event shows.
- System bars: the status bar is hidden or fully overlaid in game (screens start at y about 65 px; source uses the cutout inset as safe top, so this is the display-cutout height, not the status bar). Eyeballed.

### 2.12 Pause dialog (`13_pause_menu`)
Standard AppCompat alert dialog titled "Pause", single-choice-less list. Dialog 627 px wide (358 dp) at x=46 (26 dp margins), 611 px tall (349 dp), title region 93 px with a 14 px divider band, 6 rows x 84 px (48 dp): Settings, Save state, Load state, Cheats, Reset, Exit. Source also defines Rewind and Achievements options (`ui/emulator/rom/RomPauseMenuOption.kt`), shown only when rewind or RetroAchievements are enabled (`EmulatorViewModel.kt`, lines about 999-1002). They are not visible on the owner's phone (Observed: six items).

### 2.13 Save-slot dialog (`14_load_state`)
Dialog titled "Save slot" (reused for both Save and Load; in this capture it was Load). Custom content `view_slot_root` rows. Row 0 is the **Quick Slot**: 157 px (90 dp) tall, three lines (label "Quick Slot.", date `text_date`, time `text_time`) plus a trailing delete button `button_delete` (56 px = 32 dp, "x" glyph). Rows 1 to 8: 78 px (45 dp) tall, one line "N. <Empty>" or date/time when filled. Footer `buttonPanel` with one text button CANCEL (accent colour, right-aligned, 139x95 px touch box). The dialog behind is dimmed with the game still visible; the controller layer dims with it. The bottom screen shows the slot thumbnail? **Not observed** (empty slots; the thumbnails stored in private storage per the RE note section 4.1 were not seen in any row).

### 2.14 Cheats: folder list (`16_cheats`)
- Toolbar: up arrow, title "Cheats", right action "Enabled cheats" (toggle-all checkbox-style icon, 84 px box at x=629).
- Body: folder row (84 px = 48 dp): red folder glyph (about 36 px) at x about 30, name at x = 126 px. Default folder "My cheats".
- Floating button (`Add cheat folder`), 98 px = 56 dp circle, accent fill, folder-plus glyph, right inset 28 px = 16 dp, bottom edge 70 px above screen bottom.

### 2.15 Cheat folder, empty (`17_cheat_folder`)
Same toolbar with the folder name as title. Centred text "This folder is empty. Try adding your own cheat code" (y about 832-902, horizontally centred, wraps to a 636 px column). The floating button is the same "Add" button; its content description still reads "Add cheat folder" (Observed), but inside a folder it adds a cheat.

### 2.16 New-cheat form (`18_cheat_add`)
Toolbar: close icon (x), title "New cheat", SAVE (upper-case, accent, right). Three outlined text fields on the `#303030` background: Cheat name (112 px = 64 dp tall, focused: accent border and floating accent label), Description (112 px), Code (210 px = 120 dp, multi-line). Fields 664 px = 380 dp wide at x = 28 px (16 dp), 28 px (16 dp) gaps. Keyboard is the system IME; layout resizes above it.

## 3. Navigation graph

```
 [Launcher]
     |
     v
 LIBRARY (RomListActivity)  --- Search ---> inline filter (not captured)
   |  |  \------ Sort popup ----> (Alphabetically | Recently played)
   |  \--------- Overflow ------> Boot firmware (submenu, not captured)
   |                              DSiWare Manager (not captured)
   |                              Refresh ROM list (swipe-refresh does the same)
   |                              Settings ------> SETTINGS ROOT
   |
   |-- row gear -----------------> ROM DETAILS (RomDetailsActivity)
   |                                  |-- PLAY ----> EMULATION
   |                                  |-- Back ----> LIBRARY
   |-- row tap ------------------> EMULATION   (source/inferred, see 2.2)

 SETTINGS ROOT --> General | ROMs | Save Files | System | Video | Audio | Input
                   | RetroAchievements | Cheats       (up arrow / Back returns)

 EMULATION (EmulatorActivity, singleTask, can be launched by intent)
   |-- Back key ---------------> PAUSE dialog        (Observed; source handleBackPressed -> pauseEmulator)
   |-- Back again / outside tap / cancel -> resume   (source: setOnCancelListener -> resumeEmulator)

 PAUSE dialog
   |-- Settings -----> SETTINGS (opens over the paused game; Back returns to game)
   |-- Save state ---> SLOT dialog (save mode)
   |-- Load state ---> SLOT dialog (load mode)   --- CANCEL --> PAUSE/resume
   |-- Cheats -------> CHEAT FOLDER LIST --> FOLDER --> NEW CHEAT --(SAVE | x)--> FOLDER
   |-- Reset --------> (confirm? not observed) restarts the game
   |-- Exit ---------> LIBRARY (source: exitEmulator(force=false))
```

Back-key behaviour:
- Library: Back leaves the app (assumed; not captured).
- Settings and sub-pages: Back or up arrow goes up one level.
- Emulation: Back opens Pause (Observed; `EmulatorActivity.kt` `handleBackPressed` pauses the emulator, which shows the dialog). The same Pause is triggered by the optional on-screen "pause" component (`onPausePressed`).
- Pause dialog: Back cancels it and resumes (source `setOnCancelListener -> resumeEmulator`). Observed: the Pause dialog reopened via the Back key a second time after dismissal (`15_pause_again`).
- While any overlay is active, hardware input is not forwarded to the game (`dispatchKeyEvent`, `EmulatorOverlayTracker`).

## 4. Wireframes

Units: columns are approximate; the real screen is 360 dp wide at 280 dpi (720 px).

### 4.1 Library row

```
+--------------------------------------------------------------+ 56dp toolbar  #333333
| melonDS                              [search] [sort]  [ : ]  |
+--------------------------------------------------------------+
| [48dp icon]  Title (banner title)                      [gear]|  64dp row
|              File name.nds  (secondary, dimmer)       48dp   |
+--------------------------------------------------------------+ 1px #494949
|                                                              |
|                      (empty: "No ROMs found")                |
```

### 4.2 ROM details

```
+--------------------------------------------------------------+
| [<]  [icon]  Game title                                      |  toolbar #303030 (no elevation seen)
| [  PLAY  ]   Play time: 0m                                   |  36dp pill, accent
|  CONFIGURATION            |        RETROACHIEVEMENTS          |
|  ======(accent)           |  ------------------ (#494949)     |
|  ROM display name                                            |
|  game name (dim)                                             |  64dp rows
|  Boot system / Default                                       |
|  Microphone source / Default                                 |
|  Controller layout / Use global layout                       |
|  GBA slot / None                                             |
+--------------------------------------------------------------+
```

### 4.3 Emulation screen, portrait (Observed layout; positions eyeballed)

```
y=0   +----------------------------------+
      | (cutout / safe top ~65px = 37dp) |
y=65  +----------------------------------+
      |                                  |
      |   TOP SCREEN   720 x 540 px      |   full width, 4:3
      |                                  |
y=605 +----------------------------------+
      |                                  |
      |  BOTTOM SCREEN (touch) 720x540   |   directly below, no gap
      |                                  |
y=1145+----------------------------------+
      |[L]    [hinge][soft][mic][ff]   [R]|  utility row y 1157-1244
      |  50dp   40dp  40dp  40dp 40dp 50dp|
      |   +--+                    (X)     |
      | --+  +--    SELECT START (Y) (A)  |  d-pad bottom-left,
      |   +--+      (small circles) (B)   |  diamond bottom-right
      +----------------------------------+ y=1600 (nav-bar inset 48px)
```

The control layer is drawn on top of a black background; the game surface and the controls do not overlap in the default portrait layout. If the two screens plus the controls do not fit vertically, source moves L/R and the utility row up to the top of the bottom screen (overlap) (`impl/layout/DefaultLayoutProvider.kt`, `buildDefaultPortraitLayout`, `utilityButtonsTop`).

### 4.4 Pause dialog

```
 +--------------------------------------+  x=26dp margins, 358dp wide, bg #424242
 |  Pause                               |  title 93px
 |--------------------------------------|
 |  Settings                            |  48dp rows, text x=24dp
 |  Save state                          |
 |  Load state                          |
 |  Cheats                              |
 |  Reset                               |
 |  Exit                                |
 +--------------------------------------+   game dimmed behind
```

### 4.5 Slot dialog

```
 +--------------------------------------+
 |  Save slot                           |
 |  Quick Slot.                      [x]|  90dp, delete button 32dp
 |  Mon, 08 Jun 2026                    |
 |  17:41:00                            |
 |--------------------------------------|
 |  1. <Empty>                          |  45dp rows, 2px dividers #595959
 |  2. <Empty>                          |
 |  ... through 8.                      |
 |                          [ CANCEL ]  |  accent text button
 +--------------------------------------+
```

### 4.6 Cheat form

```
 [x]   New cheat                    SAVE
 +-------------------------------------+
 |Cheat name (focused: accent border)  | 64dp
 +-------------------------------------+
 +-------------------------------------+
 | Description                         | 64dp
 +-------------------------------------+
 +-------------------------------------+
 | Code                                | 120dp, multi-line
 |                                     |
 +-------------------------------------+
 ........(system keyboard)........
```

## 5. DS dual-screen layout modes

### 5.1 What the source actually has

Important finding: upstream has **no enum of named screen modes** (no "stacked", "side by side", "single screen" setting). Everything is a **list of positioned rectangles**: a `UILayout` holds a main-display `ScreenLayout` and a secondary-display `ScreenLayout`, and each `ScreenLayout` holds `PositionedLayoutComponent(rect, component, alpha, onTop)` entries (`domain/model/layout/UILayout.kt`, `ScreenLayout.kt`, `PositionedLayoutComponent.kt`). `TOP_SCREEN` and `BOTTOM_SCREEN` are just two of the 18 components (`LayoutComponent.kt`). The "modes" below are what `impl/layout/DefaultLayoutProvider.kt` generates by default; users can then drag, resize, overlap and re-layer anything in the layout editor. A layout is keyed by a **variant** (`UILayoutVariant`: UI size, insets, orientation, folds, displays), so the same custom layout stores separate geometry per variant. Screen size is always 256x192 (`domain/model/NDS.kt`, aspect 4:3).

Layout configuration (`domain/model/layout/LayoutConfiguration.kt`): id, name, type (DEFAULT or CUSTOM), orientation (FOLLOW_SYSTEM, PORTRAIT, LANDSCAPE), `useCustomOpacity` + `opacity` (default 50 for new custom layouts), plus the variant map. Background per screen layout: `backgroundId` + mode (STRETCH, FIT_CENTER, FIT_TOP, FIT_LEFT, FIT_BOTTOM, FIT_RIGHT; `BackgroundMode.kt`).

| Default arrangement | Generated when | Geometry (source) | Captured? |
| --- | --- | --- | --- |
| **Portrait stacked** | single display, portrait, no horizontal fold | Both screens full safe width, top above bottom, no gap; if 2 screens would be taller than the safe height they shrink to half the height each and centre horizontally. Controls under the screens | **Yes** (11, 12, 14) |
| **Landscape side by side (66/34)** | single display, landscape, no fold | Top screen = 66% of safe width, aligned to top-left; bottom screen takes the remaining width at the same top edge (so it is smaller). L/R at top corners; utility row top-centre; d-pad and diamond bottom corners; SELECT/START bottom-centre | **No, source, not observed** |
| **Folding portrait ("flip-phone")** | portrait or landscape with a horizontal fold | Top screen ends at the fold, bottom screen starts below it; each as large as the half allows; L/R and utility row at the fold's lower edge | **No, source, not observed** |
| **Folding landscape ("book")** | landscape with a vertical fold | Top screen left of the fold, bottom right of it, each as large as fits; shifted down by 50 dp + 8 dp if there is space for L/R above | **No, source, not observed** |
| **Single screen per display** | a secondary display exists | Each display shows one screen, centred and scaled to fit (`centerScreenIn`). Built-in dual-screen device: the default display is assumed to be the top screen. External display: the main display shows the **bottom** screen (plus all soft controls) and the external one shows the top screen full-size | **No, source, not observed** |
| **Custom** | user edits a layout | Any rectangle for either screen; screens may overlap; `onTop` picks which is drawn above; alpha per component | **No, source, not observed** |

Which of these the owner sees when they change "Layout" in settings or ROM details ("Use global layout" was observed) is unknown; the layout list and editor screens were not captured (open question 3).

### 5.2 Touch mapping to the bottom screen (source)

`ui/emulator/input/TouchscreenInputHandler.kt`: on ACTION_DOWN it presses the TOUCHSCREEN input and sends a position; ACTION_MOVE keeps sending; ACTION_UP releases. The position is **normalised against the bottom-screen view's own width and height** (`x / viewWidth * 256`, `y / viewHeight * 192`, clamped to 0-255 and 0-191). With several fingers it **averages** the pointers, deliberately imitating a resistive panel (comment in source). Unbuffered dispatch is requested for low latency. Consequence for us: the touch rectangle is exactly the bottom-screen rectangle wherever it sits; no separate calibration.

### 5.3 Screen swap and related toggles (source)

- `BUTTON_SWAP_SCREENS` and the Input `SWAP_SCREENS` swap which screen is the touch screen: `RuntimeLayoutView.swapScreens()` flips `areScreensSwapped` and calls `updateScreenInputs()`; the external presentation swaps too. It is a runtime toggle, not stored in the layout.
- `BUTTON_TOGGLE_SOFT_INPUT` hides or shows the on-screen controls (`toggleSoftInputVisibility`), with a toggle state shown on the button. Mapped to the second icon in the utility row by position (icon identity not verified).
- `BUTTON_FAST_FORWARD_TOGGLE` toggles fast-forward (`fastForwardEnabled`), shown toggled. `BUTTON_MICROPHONE_TOGGLE` toggles `microphoneEnabled`. `BUTTON_HINGE` is the DS lid-close input.
- Other components that exist in the layout model but are not in the default layout: RESET, PAUSE, SWAP_SCREENS, QUICK_SAVE, QUICK_LOAD, REWIND.

### 5.4 Not visually captured (explicit)

Landscape in any form; every arrangement except portrait stacked; single-screen and external-display views; the layout list, layout selector and layout editor; the background picker; per-screen opacity UI; filtering and integer-scale options. The only emulation screenshots are portrait stacked (title animation in 12, game scene behind the slot dialog in 14).

## 6. Control map

### 6.1 Observed portrait positions (screenshot, **eyeballed**; 1 dp = 1.75 px; percent of 720x1600)

| Control | Box px (x0,y0 to x1,y1) | Size dp | Position % (x, y of box origin) | Notes |
| --- | --- | --- | --- | --- |
| Top screen | 0,65 to 720,605 | 411x309 | 0, 4.1 | 4:3, full width |
| Bottom screen (touch) | 0,605 to 720,1145 | 411x309 | 0, 37.8 | directly under top screen |
| L | 0,1157 to 87,1244 | 50x50 | 0, 72.3 | notched trapezoid glyph |
| R | 633,1157 to 720,1244 | 50x50 | 87.9, 72.3 | mirrors L |
| Utility 1 (hinge-style icon) | 210,1157 to 280,1227 | 40x40 | 29.2, 72.3 | rounded rect |
| Utility 2 (hand/touch icon) | 287,1157 to 357,1227 | 40x40 | 39.9, 72.3 | likely soft-input toggle (source) |
| Utility 3 (microphone) | 363,1157 to 433,1227 | 40x40 | 50.4, 72.3 | |
| Utility 4 (fast-forward) | 440,1157 to 510,1227 | 40x40 | 61.1, 72.3 | |
| D-pad (visible cross) | 46,1299 to 274,1524 | 130x129 | 6.4, 81.2 | arms about 78 px (45 dp) thick |
| Diamond X (top) | 505,1277 to 581,1353 | 43x43 | 70.1, 79.8 | circles about 76 px |
| Diamond Y (left) | 429,1355 to 505,1431 | 43x43 | 59.6, 84.7 | |
| Diamond A (right) | 582,1355 to 658,1431 | 43x43 | 80.8, 84.7 | |
| Diamond B (bottom) | 505,1431 to 581,1507 | 43x43 | 70.1, 89.4 | |
| SELECT | 262,1482 to 330,1552 | 39x40 | 36.4, 92.6 | small circle with label |
| START | 345,1482 to 414,1552 | 39x40 | 47.9, 92.6 | |

Visual gap between the d-pad and the diamond: the middle third of the row is empty.

### 6.2 Source default geometry (`DefaultLayoutProvider.kt`, portrait)

Component sizes: d-pad and ABXY boxes 140 dp square; L and R 50 dp; utility and SELECT/START 40 dp; 4 dp spacing. Positions: d-pad bottom-left and diamond bottom-right, both against the safe edges; SELECT/START either side of centre on the bottom row; utility row of four 40 dp buttons centred on the screen middle; L/R at the outer corners of the utility row.

Differences between source default and the installed build (Observed vs Source): the visible d-pad and diamond sit about 26 dp inboard from the screen edges instead of flush; SELECT/START sit about 12 dp left of centre with a gap of about 8 dp. The installed 2.0.1 may predate the current default, or the owner's layout was edited. We follow what was **observed**; the proposed tokens in section 7 use the observed values.

### 6.3 Layout model storage (source)

Custom layouts are persisted as JSON, `layouts.json` in the app's private `filesDir`, via Gson (`impl/InternalLayoutsRepository.kt`, constant `DATA_FILE`). Each component is stored as a rect in pixels per layout variant (UI size and insets are part of the variant key), with `alpha` and `onTop`. For Pocketverse the equivalent lives in `meta.json` (per game) plus a global layout, saved per orientation per CLAUDE.md 9.4, but as **dp or fractions rather than pixels** so it survives resolution changes. That is a Pocketverse decision, not an upstream behaviour.

### 6.4 Touch targets elsewhere (dp, from the dumps)

| Target | Size dp | Source |
| --- | --- | --- |
| Toolbar icon buttons (search, sort, overflow, up, close) | 48 to 56 (84-98 px) | dumps 01, 04 |
| ROM row | 360x64 | 09 |
| Row gear button | 48x48 | 09 |
| Settings row | 360x73 (54 for single-line) | 04, 05 |
| Switch | about 47x27 thumb area inside a 63x54 frame | 05 |
| Popup menu item | 196x48 | 02, 03 |
| Pause list item | 358x48 | 13 |
| Slot row | 358x45 (90 for Quick Slot) | 14 |
| Slot delete | 32x32 (hit area probably larger) | 14 |
| Dialog button (CANCEL) | 79x54 | 14 |
| FAB | 56x56 | 16 |
| Cheat text fields | 380x64 (120 for Code) | 18 |

## 7. Design tokens (`pv.nds.*`)

Colour samples use PIL on the screenshot PNGs (exact pixel values; JPEG/anti-alias artefacts are possible at edges, so only flat regions were sampled).

### 7.1 Colour

| Token | Value | Sample point (file, x,y) |
| --- | --- | --- |
| `pv.nds.color.statusBar` | `#222222` | 09 (300,10) |
| `pv.nds.color.toolbar` | `#333333` | 09 (300,150) |
| `pv.nds.color.background` | `#303030` | 09 (300,800) |
| `pv.nds.color.divider` | `#494949` | 09 (300,287), 10 inactive tab line (500,356) |
| `pv.nds.color.dialog` | `#424242` | 14 (300,360) |
| `pv.nds.color.dialogDivider` | `#595959` | 14 (300,581) |
| `pv.nds.color.accent` | `#F44336` | 10 PLAY (60,225), 16 folder (40,217), 16 FAB (610,1450) |
| `pv.nds.color.accentSwitchThumb` | `#EF4235` | 07 (670,475) |
| `pv.nds.color.accentSwitchTrack` | `#6C3632` (about 50% accent over background) | 05 (640,222) |
| `pv.nds.color.accentFieldBorderFocused` | `#DA4035` | 18 (30,260) |
| `pv.nds.color.accentFieldLabelFocused` | `#A83C34` (anti-aliased text, not reliable; use accent) | 18 (100,217) |
| `pv.nds.color.fabShadow` | `#282828` | 16 (650,1535) |
| `pv.nds.color.gameBackdrop` | `#000000` | 11 (360,800) |
| `pv.nds.color.controlFill` | `#525252` (opaque after composition on black) | 11 (40,1190), (600,1370), (280,1500), (220,1170) |
| `pv.nds.color.dpadFill` | `#525252` base, arm glyph `#A3A3A3` | 11 (160,1350) |
| `pv.nds.color.controlGlyph` | light grey, about `#A0A0A0` (eyeballed; text and icon strokes) | 11 |
| Text primary / secondary | near-white and mid-grey; not sampled (anti-aliased text). Proposed: `#FFFFFF` at 87% and 60% | (proposed) |

Observed control opacity: controls over black read as `#525252`, which fits about 32% white; the source default for a new custom layout is `opacity = 50` (`LayoutConfiguration.newCustom`). The actual compositing formula is unverified (open question 4).

Light theme: the General settings page lists "Theme"; we only captured the dark theme. A light variant is **not observed**.

### 7.2 Typography (APPROXIMATE, from XML text-box heights)

Typeface appears to be a geometric sans (Montserrat-like) in the screenshots; this is visual only, not verified in resources. Proposed OFL substitute: Montserrat (log in `docs/licenses/THIRD_PARTY.md` when adopted).

| Token | Observed box height px / dp | Approx size |
| --- | --- | --- |
| `pv.nds.type.toolbarTitle` | 47 px / 27 dp | about 20 sp |
| `pv.nds.type.listTitle` (settings, rom title) | 38-43 px / 22-25 dp | 16 to 18 sp |
| `pv.nds.type.listSummary` | 34 px / 19 dp | about 14 sp |
| `pv.nds.type.dialogTitle` | 47 px / 27 dp | about 20 sp |
| `pv.nds.type.dialogItem` | 43 px / 25 dp | about 18 sp |
| `pv.nds.type.button` (PLAY, SAVE, tabs) | 30 px / 17 dp | 14 sp, bold, upper-case, letter-spaced |
| `pv.nds.type.controlLabel` (X Y A B, L R) | glyphs about 35-45 px | 20 to 24 sp, medium weight |
| `pv.nds.type.controlLabelSmall` (SELECT, START) | about 16 px cap height | 9 to 10 sp |

### 7.3 Spacing, sizes, radii, elevation

| Token | Value |
| --- | --- |
| `pv.nds.space.gutter` | 16 dp (28 px) text/icon left inset |
| `pv.nds.space.listTextStart` | 72 dp (126 px) in settings and cheats |
| `pv.nds.space.romRowTextStart` | 64 dp (112 px) |
| `pv.nds.space.fieldGap` | 16 dp |
| `pv.nds.space.dialogMargin` | 26 dp (46 px) each side |
| `pv.nds.size.statusBar` | 44 dp (77 px) on this phone (device value) |
| `pv.nds.size.toolbar` | 56 dp (98 px) |
| `pv.nds.size.iconAction` | 24 dp glyph in a 48 dp (84 px) hit box |
| `pv.nds.size.romIcon` | 48 dp |
| `pv.nds.size.fab` | 56 dp |
| `pv.nds.size.playButton` | 132x36 dp |
| `pv.nds.size.rowSimple` / `rowTwoLine` / `rowRom` | 54 / 73 / 64 dp |
| `pv.nds.size.dialogRow` | 48 dp (pause), 45 dp (slot) |
| `pv.nds.size.field` | 64 dp; code field 120 dp |
| `pv.nds.size.ctrl.largeBox` / `lr` / `small` / `gap` | 140 / 50 / 40 / 4 dp (source) |
| `pv.nds.radius.play` | full pill (18 dp) |
| `pv.nds.radius.fab` | full circle |
| `pv.nds.radius.field` | about 4 dp (eyeballed) |
| `pv.nds.radius.dialog` | about 4 dp or less (looks near-square; eyeballed) |
| `pv.nds.radius.ctrlSmall` | about 6 dp for utility buttons (eyeballed); L/R are notched shapes; XYAB, SELECT, START are circles; d-pad cross has square arm ends |
| `pv.nds.elevation.toolbar` | none visible (flat, tonal change only) |
| `pv.nds.elevation.fab` | standard FAB shadow (`#282828` under it) |
| `pv.nds.elevation.dialog` | tonal only; scrim dims the game to about 40% brightness (eyeballed) |

## 8. Interaction rules

| Rule | Basis |
| --- | --- |
| Slot 0 is the **Quick Slot**; slots 1 to 8 are numbered. The same dialog is used for Save and Load; empty slots read "N. <Empty>" and filled ones show weekday-date and time. Quick Slot has a delete button | Observed (14); RE note 4.1 |
| States are `<ROM name>.ml<N>`, N = 0..8 | RE note 4.1 (observed on disk) |
| Pause menu order: Settings, Save state, Load state, Cheats, Reset, Exit; Rewind and Achievements are conditional | Observed + Source |
| Back in game opens Pause; Back from the dialog resumes | Observed + Source |
| Pause freezes emulation; dismissing resumes | Source (`pauseEmulator`, `resumeEmulator`) |
| Cheats are organised as folders containing cheats; there is a default "My cheats" folder; "toggle all" is in the toolbar; the floating button adds a folder at the folder-list level and a cheat inside a folder | Observed (16, 17); inside-folder add inferred from the empty-state text |
| New cheat requires name, description and code fields; SAVE is a toolbar action | Observed (18); required-field rules not observed |
| Cheats only apply when Settings > Cheats > Enable cheats is on | RE note 7 |
| Settings is a two-level list (root rows open sub-pages); switches toggle in place and the whole row is the touch target | Observed (04, 05) |
| Library: sort choices Alphabetically and Recently played; Search is inline; pull to refresh rescans | Observed + Source |
| ROM details edits per-game overrides; "Use global layout" means no override | Observed (10) |
| On-screen controls follow press state (flat pressed look not captured), optional haptic feedback via KEY_PRESS and KEY_RELEASE constants | Source (`FeedbackInputHandler.kt`) |
| Hardware key and gamepad input is blocked while an overlay (pause, dialogs) is open | Source |
| Screen stays on while playing | Source (`disableScreenTimeOut`) |

## 9. Animation notes

- **Observed:** no transition could be judged from still captures; we have no screen recording. Treat all transition behaviour as unknown.
- **Source-defined:** screen push/pop uses `anim/fragment_translate_enter_push`, `_exit_pop` etc. (horizontal translate) and dialogs use `dialog_slide_in/out`, with duration 250 ms (`res/values/values.xml`, `fragment_translate_duration`). The dialog slide direction is not verified.
- **Proposed defaults (ours):** screen push/pop 250 ms horizontal slide with standard easing; dialog fade/slide 150 ms; ripple on rows; no animation on game-surface start; scrim fade 150 ms; FAB scale-in 100 ms. All are Proposed and subject to owner review.

## 10. Edge cases

- **Rotation:** landscape was not captured. Source default is the 66/34 side-by-side layout (section 5.1), and layouts can be forced to Portrait or Landscape or follow system (`LayoutOrientation`). Treated as **source, not observed**. We store controller geometry per orientation (CLAUDE.md 9.4).
- **Notch and cutout:** the default layout uses the display-cutout insets as the safe area (`getDisplaySafeInsets`, `EmulatorActivity.kt` cutout handling), so screens and controls avoid the cutout. Observed portrait screens start about 65 px below the top, which is consistent. API 29 and above only; below that, zero insets.
- **Foldables:** source handles a horizontal fold (screens attach to either side of the hinge) and a vertical fold (book layout), and the `ScreenFold` model has SEAMLESS and GAP types. **Not observed.**
- **Dual-screen and external display:** per section 5.1; `ExternalPresentation`/`ExternalLayoutRender` draw the second screen. Not observed.
- **Gamepad focus:** hardware controllers and keyboards drive the game directly through `InputProcessor`, and key mapping lives in the Input settings (not captured). For Pocketverse, all menus must also be focus-navigable (CLAUDE.md 9.4); focus order, focus ring style and Back/B mapping are not defined by the reference and are open (question 7).
- **Accessibility:** the 40 dp utility and SELECT/START buttons and the 32 dp slot delete button are below 48 dp. Plan: keep the original sizes by default (the look is the point), add an accessibility toggle that enlarges hit targets to 48 dp without changing artwork, and list the deviation in `PARITY.md`. Contrast: accent `#F44336` on `#303030` is about 3.4:1, under 4.5:1 for small text; same note applies to CANCEL and SAVE.
- **Haptics:** optional, a setting under Input; two feedback types (press, release). Intensity and pattern not observed.
- **Keyboard (IME):** the cheat form shrinks above the keyboard; the Code field must stay reachable.
- **Dialog on a dim controller:** while the slot dialog is open, the controller layer is dimmed with the scrim (Observed in 14); do not leave it fully lit.

## 11. Open questions for the owner

1. Do you want the exact installed default placement (controls inset about 26 dp, SELECT/START left of centre) or the cleaner current-source default (flush to edges, centred)? Section 6.2.
2. Does tapping a library row launch the game directly, or open ROM details first? Not captured as a tap; please confirm on your phone.
3. Which screen layouts do you actually use (stacked, side by side, a custom one)? Please capture landscape and the Layout list/editor screens, or tell us which to prioritise; they are the largest unobserved area.
4. How translucent are your controls (opacity setting in Layout) and do you use the optional pause/quick-save/rewind buttons?
5. Is a light theme needed in NDS mode, or dark only (what was observed)?
6. Sort menu: does the selected option show a different icon or tick? Capture it if so.
7. Gamepad/keyboard focus behaviour in menus is not defined by the reference. Is D-pad navigation plus A/B (select/back) acceptable as our own design?
8. Boot firmware and DSiWare Manager: in scope for NDS mode, or later?
9. Which font should we use if the geometric look matters to you (Montserrat OFL is the proposed stand-in)?

## 12. Parity checklist stub

| Reference screen | Reference behaviour | Our implementation | Intentional deviation |
| --- | --- | --- | --- |
| Library, empty | Toolbar with title, search, sort, overflow; "No ROMs found" centred | TBD Phase 6 | None planned |
| Library, with ROM | 64 dp rows, icon, banner title, file name, gear; pull to refresh | TBD Phase 6 | Own icons and wording; Pocketverse adds platform badge? (decide) |
| Overflow menu | 4 items incl. Boot firmware submenu | TBD Phase 6 | Items we do not support are hidden |
| Sort menu | Two options with leading icons | TBD Phase 6 | None planned |
| Settings root | 9 icon+title+summary rows | TBD Phase 6 | Pocketverse-wide settings live elsewhere; NDS page set is a subset |
| Settings ROMs / Save Files / System / Cheats | Row lists with switches, summaries, seek bar | TBD Phase 6 | Storage choices follow STORAGE.md, not SAF search directory |
| ROM details | PLAY, play time, tabs, five config rows | TBD Phase 6 | Backed by `meta.json`; RetroAchievements tab omitted unless built |
| Emulation, portrait | Stacked screens, control layer, 4 utility buttons | TBD Phase 6 | Accessibility toggle for hit targets |
| Emulation, landscape | Source: 66/34 side by side | TBD Phase 6 | Not observed; verify before parity sign-off |
| Pause dialog | 6 items, Back opens/closes | TBD Phase 6 | None planned |
| Save-slot dialog | Quick Slot + 1-8, delete, CANCEL | TBD Phase 6 | Slots live in the game folder `state/`; screenshots stored there too |
| Cheats folder list | Folders, toggle all, FAB | TBD Phase 6 | Canonical cheat model and file import beyond XML |
| Cheat folder empty | Centred hint text | TBD Phase 6 | None planned |
| New-cheat form | Name, description, code, SAVE | TBD Phase 6 | Format auto-detect indicator added (cheats section 8.2) |
| Layout list / editor | Source: draggable, resizable components | TBD Phase 6 | Not observed; scope open |
| External display / folds | Source only | TBD Phase 6 | Not observed |
