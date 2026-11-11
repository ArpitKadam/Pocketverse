# UI Spec: GBA mode (reference: John GBA Lite v4.15)

Status: DRAFT for owner review, 2026-11-12. Source facts: `docs/research/re/john-gba-lite.md` (sections 5-8) and the raw captures in the gitignored `.re-workspace/ui-reference/johngbalite/` (19 screens, 720x1600 px, 280 dpi, 1 dp = 1.75 px, so the screen is 411.4 x 914.3 dp).

Labels: **XML** = bounds read from a uiautomator dump (exact). **PNG~** = eyeballed from a screenshot (custom-drawn views are not in the dumps; accuracy about +-8 px, +-5 dp). **PROPOSED** = our own default, not observed. **UNKNOWN** = not determined.

## 1. Purpose, scope, legal line

GBA mode is one of three Pocketverse UI modes (CLAUDE.md section 9). It reproduces the layout, flow, control placement, density and colour mood of the reference app so the owner's `.gba` workflow feels the same. It is a `ThemeMode` implementation (`ui-theme-gba`); features (library, player, cheats, settings) are written once and rendered through it.

Replicated: screen inventory, navigation, control positions and sizes, menu order, row heights, dialog structure, colour mood, density, type feel.

Not replicated (original assets only):
- No icons, logos, bitmaps, skins, screenshots, sounds or fonts from the reference. All icons are redrawn as Compose vectors; the pad is drawn in code.
- No app name, "Lite"/"Purchase" wording or branding. Our strings are our own, same structure and tone.
- No Nintendo / Game Boy / Pokemon names, logos or boot sounds.
- Deliberately dropped: banner ad row, interstitial ad, "Purchase John GBA" drawer and menu items, and the "Cloud settings" companion-app install flow (we have no companion; see section 10 for what replaces it).
- The theme dialog's colour names that cite other consoles ("Red (NES)", "Gray (SNES)", "Green (GBC)", "Orange (GBA)") are not reused; we name our own variants (section 6.1).

## 2. Screen inventory

Ids are `resource-id` names from the dumps. "Entry/Exit" are observed unless marked inferred.

| # | Screen (capture) | Purpose | Entry | Exit | Key elements |
|---|---|---|---|---|---|
| S1 | Library (01) | List games, open one | App launch; back from Settings/About; drawer rows | Tap row -> S9; hamburger -> S2; overflow -> S3; Back exits app (inferred) | `toolbar`, `menu_romlist_search`, `menu_romlist_romdir`, `menu_romlist_import`, overflow (no id, desc "More options"), `romlist_listview`, `layout_romitem_image`/`romlist_item_image`, `romlist_item_text_name`, `romlist_item_text_subinfo` |
| S2 | Drawer (02) | Switch list view, reach app pages | Hamburger, edge swipe (inferred) | Tap row; tap scrim or Back closes (inferred) | `romlist_nav_view`, `menu_romlist_type_favorites/history/all`, `menu_romlist_preferences`, `menu_romlist_theme`, `menu_romlist_dbxsync`, `menu_romlist_help`, `menu_purchase` (dropped), `menu_romlist_about` |
| S3 | Library overflow (03) | List options | Overflow button | Tap item or outside | Two items: "View", "Rescan storage" (`title`). "View" sub-options not captured (UNKNOWN) |
| S4 | Settings (04 top / scroll1 / scroll2) | Preferences | Drawer > Settings; in-game menu > Settings | Back (up arrow not seen; toolbar shows title only at x=28) | Flat preference list, section headers, checkboxes (see 2.1) |
| S5 | Theme dialog (05) | Pick colour scheme | Drawer > Theme | Pick (applies, closes - inferred) or CANCEL | `select_dialog_listview`, 9 radio rows, `button2` CANCEL |
| S6 | Cloud settings dialog (06) | Explain companion-app requirement | Drawer > Cloud settings | CANCEL / OK (OK opens store: not tested) | `message`, `button2`, `button1`. Dropped (section 10 Q7) |
| S7 | About (07) | App info | Drawer > About | Back | `about_image`, `about_text_name`, `about_text_version`, `about_text_scoped_storage`, developer label, `textView_email`, `textView_privacy_policy` |
| S8 | Game launch (08) | Same as S9 at load time | Tap library row | n/a | Identical element set to S9 with the ad row populated |
| S9 | In-game portrait (09, 15) | Play | S8; resume | Menu button -> S10; Back -> S10 or exit dialog (inferred, UNKNOWN) | `game_framelayout`, `game_emuview` (single custom view holding toolbar row, game image, virtual pad), clock and battery `TextView`s |
| S10 | In-game menu (10, 16b) | Modal list of game actions | Menu icon in action row | Row tap; tap outside (inferred) | `parentPanel` list of 10 rows `text1`: Load state, Save state, Settings, Reset, Controller Settings, Screenshot, Speed, Cheats, Purchase (dropped), Exit |
| S11 | State list (17; 16 is the same screen under an ad) | Choose a slot to load (and presumably save) | Menu > Load state (Save state: same layout, title presumably "Select state slot to save", UNKNOWN); quick-load icon (inferred) | Tap slot; Back | `statelist_listview`; rows `imageView_state_image`, `textView_state_name`, `textView_state_time` |
| S12 | Cheats list (11) | Per-game cheat list | Menu > Cheats | Back | Toolbar title "Cheats", `menu_cheatlist_add`, `menu_cheatlist_help`; empty body (row layout with entries UNKNOWN) |
| S13 | Add-cheat dialog (12) | Enter a code | Add button | OK, CANCEL, HELP | `cheat_dialog_edit_name` (prefilled "New code", selected), `cheat_dialog_spinner_type`, `cheat_dialog_edit_code` (hint), buttons HELP/CANCEL/OK |
| S14 | Cheat type spinner (14) | Choose code family | Tap spinner | Pick | 5 rows: Unspecified, Raw code, GameShark, GameShark v3, CodeBreaker (84 px = 48 dp each) |
| S15 | Cheat help (13) | Explain accepted code shapes | HELP in S13 or Help in S12 | OK | `message` scrollable, `button1` OK |
| S16 | Controller Settings dialog (18) | Pad toggles | Menu > Controller Settings | OK | 4 check rows: Hide virtual pad, Enable button A turbo, Enable button B turbo, Toggle L/R button |
| S17 | Speed dialog (19) | Emulation speed | Menu > Speed | Pick, CANCEL | 7 radio rows: x0.25, x0.5, x1 (Default), x2, x4, x8, x16 |
| S18 | Exit confirm (16_after_ad) | Save before leaving | Menu > Exit (inferred from capture order) | SAVE, DON'T SAVE, CANCEL | Title, message "Do you want to save changes and exit?" plus "Play time: m:ss", three text buttons |
| S19 | Interstitial ad (15_interstitial_ad) | Full-screen ad | Shown between actions | Close | DROPPED. Never reproduced |

### 2.1 Settings content (S4), in order

Section "Quality": Quality mode. Section "Layouts": Save layouts by screen resolution (checkbox, summary "Recommended to enable on foldable devices."), Layouts (Portrait), Layouts (Landscape), Orientation, Clock & Battery meter (checkbox). Section "Video": Smoothing. Section "Audio": Enable audio (checkbox), Audio volume, Low latency mode (checkbox, summary about performance). Section "Virtual pad": Auto hide Virtual Pad (summary "Hide Virtual Pad when using gamepad"), Vibration setting (summary "Set strength of vibration or turn off vibration"), Dpad diagonals area size, Enable A+B between A and B (checkbox). Section "Gamepad": Gamepad settings. Section "Other": Load autosave state at startup (checkbox), Enable cheating (checkbox), Debug (show FPS) (checkbox). Values of non-checkbox rows (modes, sliders) were not opened: UNKNOWN. Pad-layout editing (`EditLayoutActivity`) was not entered: UNKNOWN.

## 3. Navigation graph

```text
                 +-----------------------------+
   launch ---->  |  S1 Library (All/Fav/Hist)  | <---- Back from S4/S7 (observed)
                 +--+------+-------+-----+-----+
        hamburger   |      |       |     | row tap
                    v      |       |     v
             +-----------+  |       |  +-----------------------+
             | S2 Drawer |  |       |  | S9 In-game portrait   |
             +-+-+-+-+-+-+  |       |  +--+-----+----+----+----+
  Settings--+  | | | |      |       |     |     |    |    |
  Theme-----|--+ | | |  overflow  search   menu  quick  FF  Back(?)
  Cloud-----|----+ | |      v     (inferred: icon  save/  toggle
  Help------|------+ |  S3 menu   filter)  |    load      (inferred)
  About-----|--------+                     v
            v                          +--------+
    S4 / S5 / S6 / (help) / S7         | S10    |--Load state--> S11 list (load)
                                       | menu   |--Save state--> S11 list (save)  (inferred)
   S4 also reachable from S10 ---------|        |--Settings----> S4
                                       |        |--Controller--> S16 dialog
                                       |        |--Speed------> S17 dialog
                                       |        |--Cheats-----> S12 -> S13 -> S14/S15
                                       |        |--Reset------> (confirm? UNKNOWN)
                                       |        |--Screenshot-> (toast? UNKNOWN)
                                       |        |--Exit-------> S18 -> S1
                                       +--------+
```

Back-stack: S1 is the root (singleTop launcher). S4/S7/S12/S11 are full activities pushed on S1 or S9; Back pops them (inferred from separate manifest activities). S5/S6/S16/S17/S18/S10/S13 are modal dialogs over their parent. Drawer Settings/Theme/Cloud/Help/About close the drawer first (inferred). In-game Back behaviour is UNKNOWN.

## 4. Wireframes

Scale for grids: 1 char = 15 px wide x 32 px tall (8.6 x 18.3 dp); in-game grid is the full 720x1600 screen (48 x 50 chars). Others are cropped or schematic and marked.

### 4.1 Library (S1, top 400 px, to scale)

```text
+--------------status bar #FF9C00--------------+
+----------------------------------------------+   <- 44 dp status bar (77 px)
| =     All                 ?   [ ]  [+]  :    |   <- 48 dp toolbar; icons: search, folder, add-file, overflow
+----------------------------------------------+
+--------+-Title (16sp)------------------------+   <- row 74 dp (130 px); bg #303030
| thumb  | Not played                          |
|  80x72 | GBA 32.00 MB                        |
+--------+-------------------------------------+
```

Toolbar icon order left to right: [hamburger | title "All" at x=126 px | search | game folder | import file | overflow].

### 4.2 Drawer (S2, to scale, 490 px = 280 dp wide, over a 60% scrim)

```text
+-------------------------------+   y=0 status area, drawer bg #FAFAFA (status strip greyed)
|                               |
|                               |
| * Favorites                   |   rows 48 dp (84 px), icon at x=28 px, label at x=126 px
|                               |
|                               |
| H History                     |
|                               |
|                               |
| G All  (selected, #DCDCDC)    |
|                               |
---------------------------------   1 px divider #DCDCDC at y=358 px, 30 px gap each side
| o Settings                    |
|                               |
| o Theme                       |
| o Cloud settings              |
|                               |
| o Help                        |
|                               |
| o Purchase  (DROPPED)         |
| o About                       |
|                               |
+-------------------------------+   remainder blank down to y=1558 px
```

### 4.3 In-game portrait with virtual pad (S9, to scale)

```text
  status bar (system, 44dp)

+----------------------------------------------+
|      [ad slot - DROPPED in Pocketverse]      |   y 77-189 px in reference
|                                              |
+----------------------------------------------+
 v save   ^ load                >>               =     <- action row, glyph centre y~222 px (PNG~)


+----------------------------------------------+
|                                              |
|                                              |
|                                              |
|    GAME FRAME 720x480px (3:2), full width    |   y 287-767 px (PNG~), pixel-art, black letterbox
|                                              |
|                                              |
+----------------------------------------------+










  +---L----+                        +---R----+   y 1108-1156 px


       +----+                          +-----+
       | ^  |                          |  A  |   A circle 112 px, B circle below, A above B
       +----+                          +-----+
  +-<-+      +->--+
  +---+      +----+                    +-----+
       +----+                          |  B  |
       | v  |                          |     |
       +----+                          +-----+

               +SELECT+  +START-+                  y 1508-1545 px

  2:28 am                                 100%      <- clock left, battery right; text boxes y 1566-1600 px (XML)
```

Without the ad row the vertical position of the game frame is UNKNOWN (ad slot collapsed or kept as blank). PROPOSED: no ad row; action row directly under the status bar; game frame stays at the same distance below the action row.

### 4.4 In-game menu (S10, schematic, same widths)

```text
   +--------------------------------------+   x 46-673 px (26-384 dp), y 409-1267 px, bg #424242
   | Load state                           |   rows 86 px pitch (84 px + 2 px divider #595959)
   |--------------------------------------|
   | Save state                           |
   | Settings                             |
   | Reset                                |
   | Controller Settings                  |
   | Screenshot                           |
   | Speed                                |
   | Cheats                               |
   | Purchase (DROPPED)                   |
   | Exit                                 |
   +--------------------------------------+   9 rows in our build (Purchase removed)
```

### 4.5 State list (S11, to scale, top 700 px)

```text
+--------------status bar #FF9C00--------------+
| Select state slot to load                    |   48 dp toolbar, title only, no actions
+--------+-------------------------------------+
|  100x88| Undo loading state                  |   row pitch 178 px (101.7 dp), thumb 100x88 dp at x=11 px
|  black | <Empty>                             |
+--------+-------------------------------------+   1 px divider #494949
| shot   | Slot1 (Quick save)                  |   title white when filled, grey when empty
|        | 12 Nov 2026 2:30:53 am              |
+--------+-------------------------------------+
| black  | Slot2   <Empty>   ...   to Slot7    |   7 slots plus the Undo row = 8 rows
```

### 4.6 Add-cheat dialog (S13, schematic)

```text
+-----------------------------------------+   x 46-673 px, y 488-1147 px, bg #424242
|  Add                                    |   title 20sp white
|  Name                                   |
|  New code (selected, accent underline)  |   EditText 342x45 dp
|                                         |
|  Cheat type                             |
|  Unspecified                        v   |   spinner 342x24 dp visible, popup rows 48 dp
|                                         |
|  Code (Raw code/GameShark/CodeBreaker)  |   label wraps to 2 lines
|  00112233:AABB                          |   multi-line hint (EditText 67 dp tall)
|  00112233 AABBCCDD                      |
|                                         |
|  HELP                    CANCEL    OK   |   accent text buttons, 54 dp tall
+-----------------------------------------+
```

## 5. Control map (portrait)

All values from 720x1600 px. dp = px / 1.75. Percent of width (x) and height (y). Basis column: XML or PNG~.

### 5.1 Library, drawer, toolbars (XML)

| Target | Basis | origin dp (x,y) | size dp | x % | y % |
|---|---|---|---|---|---|
| Hamburger / up button | XML | 0,44 | 56x48 | 0-13.6 | 4.8-10.1 |
| Search | XML | 226.9,44 | 48x48 | 55.1-66.8 | 4.8-10.1 |
| Game folder | XML | 274.9,44 | 48x48 | 66.8-78.5 | 4.8-10.1 |
| Import file | XML | 322.9,44 | 48x48 | 78.5-90.1 | 4.8-10.1 |
| Overflow | XML | 370.9,44 | 40.6x48 | 90.1-100 | 4.8-10.1 |
| List row 1 | XML | 0,92 | 411.4x74.3 | 0-100 | 10.1-18.2 |
| Row thumbnail | XML | 2.3,93.1 | 80x72 | 0.6-20 | 10.2-18.1 |
| Drawer panel | XML | 0,0 | 280x890 | 0-68.1 | 0-97.4 |
| Drawer rows (each) | XML | 0, 52 + 48n (group 1); group 2 starts y=213 dp | 280x48 | 0-68.1 | 3 rows then 6 rows |
| Cheats Add | XML | 315.4,44 | 48x48 | 76.7-88.3 | 4.8-10.1 |
| Cheats Help | XML | 363.4,44 | 48x48 | 88.3-100 | 4.8-10.1 |
| Settings checkbox frame | XML | 347.4 (right-aligned) | 48x73 | 84.4-96.1 | row-relative |
| Settings checkbox glyph | XML | 363.4 | 32x32 | 88.3-96.1 | row-relative |

Drawer row pitch is 48 dp (84 px); group 1 rows at y 91, 175, 259 px; divider ~358 px; group 2 at 373, 457, 541, 625, 709, 793 px.

### 5.2 In-game (S9)

| Target | Basis | origin dp (x,y) | size dp | x % | y % |
|---|---|---|---|---|---|
| Ad row (dropped) | XML | 0,44 | 411x64 | 0-100 | 4.8-11.8 |
| Game view (whole custom view) | XML | 0,108 | 411.4x806 | 0-100 | 11.8-100 |
| Quick save glyph (down-arrow) | PNG~ | 0,111 | 40x31 | 0-9.7 | 12.2-15.6 |
| Quick load glyph (up-arrow, inferred function) | PNG~ | 91,111 | 40x31 | 22.2-31.9 | 12.2-15.6 |
| Fast-forward glyph | PNG~ | 280,111 | 40x31 | 68.1-77.8 | 12.2-15.6 |
| Menu glyph (3 lines) | PNG~ | 374,111 | 37x31 | 91-100 | 12.2-15.6 |
| Game image | PNG~ | 0,164 | 411x274 | 0-100 | 17.9-47.9 |
| L pill | PNG~ | 16,633 | 87x27 | 3.9-25.1 | 69.2-72.2 |
| R pill | PNG~ | 308,633 | 87x27 | 74.9-96 | 69.2-72.2 |
| D-pad cluster | PNG~ | 17,694 | 142x143 | 4.2-38.8 | 75.9-91.6 |
| D-pad up | PNG~ | 64,694 | 48x47 | 15.6-27.2 | 75.9-81.1 |
| D-pad left | PNG~ | 17,741 | 47x48 | 4.2-15.6 | 81.1-86.3 |
| D-pad right | PNG~ | 112,741 | 47x48 | 27.2-38.8 | 81.1-86.3 |
| D-pad down | PNG~ | 64,789 | 48x48 | 15.6-27.2 | 86.3-91.6 |
| A | PNG~ | 331,693 | 64x64 | 80.6-96.1 | 75.8-82.8 |
| B | PNG~ | 331,773 | 64x64 | 80.6-96.1 | 84.6-91.6 |
| SELECT pill | PNG~ | 130,862 | 63x21 | 31.7-47.1 | 94.2-96.6 |
| START pill | PNG~ | 218,862 | 63x21 | 53.1-68.3 | 94.2-96.6 |
| Clock text | XML | 16,895 | 53x19 | 3.9-16.7 | 97.9-100 |
| Battery text | XML | 361,895 | 35x19 | 87.6-96.1 | 97.9-100 |
| Bottom gesture area (system) | XML | 0,890 | full | 0-100 | 97.4-100 |

Notes: action-row positions are glyph bounds, not hit areas; actual hit rectangles are UNKNOWN (the custom view exposes no children). Pad geometry is symmetric-ish: L/R margins 16 dp; A/B circles share a column right-aligned 20 dp from the edge; d-pad segments are 48 dp squares with the left/right segments slightly flared (outer edges curved). Pad sits in the lower 25% of a 20:9 screen. How the pad scales on other screens (fixed dp vs percent) is UNKNOWN; PROPOSED: percent of width for x, anchored to the bottom for y.

### 5.3 Dialog and list row targets (XML)

| Target | origin dp (x,y) | size dp | x % | y % |
|---|---|---|---|---|
| In-game menu dialog | 26.3,233.7 | 358x490 | 6.4-93.5 | 25.6-79.2 |
| Menu / dialog list row (each) | x 26.3 | 358x48 | 6.4-93.5 | 48 dp pitch + 1 px divider |
| Cheat-add dialog | 26.3,278.9 | 358x377 | 6.4-93.5 | 30.5-71.7 |
| Name field | 34.3,356.6 | 342x45 | 8.3-91.5 | 39-43.9 |
| Spinner | 34.3,436.6 | 342x24 | 8.3-91.5 | 47.8-50.4 |
| Code field | 34.3,517.7 | 342x67 | 8.3-91.5 | 56.6-64 |
| HELP / CANCEL / OK buttons | 38.3 / 229.1 / 308.6, y 597.1 | 64x54 / 79x54 / 64x54 | 9.3-24.9 / 55.7-75 / 75-90.6 | 65.3-71.2 |
| State row thumbnail | 6.3, row top + 6 | 100x88 | 1.5-25.8 | row-relative |
| State row pitch | | 101.7 | | |

Observed targets under 48 dp: pad L/R pills (27 dp tall, PNG~), SELECT/START (21 dp tall, PNG~), settings checkbox glyph (32 dp, but its frame is 48x73 dp), dialog buttons width 64 dp (fine) but height 54 dp (fine). See section 9 accessibility.

## 6. Design tokens

Values are measured facts; names are ours. Sample points are in the PNGs.

### 6.1 Colour

| Token | Value | Where sampled |
|---|---|---|
| `pv.gba.color.primary` | #FF9C00 | toolbar and status bar, library (01 at 300,120) |
| `pv.gba.color.primaryDimmedBy60` | #663E00 | same toolbar under the dialog scrim (05/12), confirms scrim alpha 0.6 |
| `pv.gba.color.surface` | #303030 | list, settings, state list, about backgrounds (01, 04, 07, 17) |
| `pv.gba.color.surfaceDimmed` | #131313 (approx #121212-#131313) | page behind a modal dialog (12, 06) |
| `pv.gba.color.dialog` | #424242 | dialog panels (05, 10, 12) |
| `pv.gba.color.drawer` | #FAFAFA | drawer background (02) |
| `pv.gba.color.drawerSelected` | #DCDCDC | drawer selected row, drawer divider (02) |
| `pv.gba.color.onDrawer` | #202020 | drawer labels (02) |
| `pv.gba.color.onDrawerIcon` | #646464 | drawer icons (02) |
| `pv.gba.color.onPrimary` | #FFFFFF | toolbar title/icons (01) |
| `pv.gba.color.onSurface` | #C1C1C1 | list item title, preference summary (01, 04) |
| `pv.gba.color.onSurfaceStrong` | #FFFFFF | preference titles, dialog text, filled slot name (04, 10, 17) |
| `pv.gba.color.onSurfaceMuted` | #888888 | list sub-info (01); #666666 for empty-slot time (17) |
| `pv.gba.color.accent` | #29B6F6 | section headers, dialog buttons, checkbox/radio, text-field underline (04, 05, 12) |
| `pv.gba.color.divider` | #494949 (on #303030), #595959 (on #424242) | state list and menu dividers |
| `pv.gba.color.scrim` | black at 0.6 alpha (derived) | drawer/dialog dimming |
| `pv.gba.color.gameBackdrop` | #000000 | in-game page and pad area (15) |
| `pv.gba.color.padFill` | #2F2F2F-#303030 | pill, A/B, d-pad fill (15) |
| `pv.gba.color.padStroke` | #959595-#9A9A8E | pill/d-pad outline, approx (15) |
| `pv.gba.color.padLabel` | #7A7A7A | L/R/A/B/SELECT/START glyphs (15) |
| `pv.gba.color.hudText` | #A9A9A9 | clock and battery text (15) |
| `pv.gba.color.actionRowIcon` | #989898 | quick save/load, FF, menu glyphs (15) |

Pad opacity (alpha of the whole overlay) is UNKNOWN; sampled values are as composited over black.

Theme variants offered by the reference Theme dialog (names only; only the orange variant was measured, others UNKNOWN): red, gray, green, orange (default), blue, red (second red), purple, pink, black. Pocketverse names (PROPOSED): `ember` (default, #FF9C00), `crimson`, `slate`, `moss`, `ocean`, `ruby`, `violet`, `rose`, `onyx`. Only `primary` changes per variant in the reference as far as observed (inferred); whether the surface colour also changes is UNKNOWN. Values for the 8 non-default variants are PROPOSED when we pick them.

### 6.2 Type scale (APPROXIMATE)

The typeface looks like a geometric sans with round letterforms. It could not be identified, and no font file is bundled by us. PROPOSED: an OFL geometric sans such as Montserrat, logged in `docs/licenses/THIRD_PARTY.md` when picked; owner to confirm (Q4). Sizes are estimated from text-box heights in the dumps (px / 1.75 gives box height in dp; sp is roughly 0.8 of line box).

| Token | Role | XML box height | Estimated size |
|---|---|---|---|
| `pv.gba.type.toolbarTitle` | toolbar title ("All", "Settings") | 38 px = 21.7 dp | ~18-20 sp |
| `pv.gba.type.itemTitle` | library title | 35 px = 20 dp | ~16 sp |
| `pv.gba.type.itemSub` | library sub-info, state time | 30-34 px = 17-19 dp | ~13-14 sp |
| `pv.gba.type.prefTitle` | preference title, dialog list item | 38 px = 21.7 dp | ~16-18 sp |
| `pv.gba.type.prefSummary` | preference summary | 34 px = 19.4 dp | ~14 sp |
| `pv.gba.type.sectionHeader` | "Quality", "Audio" (accent) | not a bare text box in XML (row 62 px) | ~14 sp (PNG~) |
| `pv.gba.type.dialogTitle` | dialog titles | 47 px = 26.9 dp | ~20 sp |
| `pv.gba.type.button` | dialog text buttons (uppercase, accent) | PNG~ | ~14 sp medium |
| `pv.gba.type.padLabel` | L/R/A/B/SELECT/START | PNG~ | A/B ~24 sp bold; pills ~16 sp (SELECT/START ~14 sp, heavy condensed look) |
| `pv.gba.type.hud` | clock/battery | 34 px = 19.4 dp | ~14-16 sp |

### 6.3 Spacing, radii, elevation, sizes

| Token | Value | Basis |
|---|---|---|
| `pv.gba.space.gutter` | 16 dp (28 px) | text left inset in toolbar, settings, drawer icons, state-name rows |
| `pv.gba.space.dialogMargin` | 26 dp (46 px) each side; dialog 358 dp wide | XML |
| `pv.gba.space.dialogInset` | 34 dp (60 px) content; title text at x=48 dp (88 px) | XML |
| `pv.gba.space.thumbGap` | 11 dp (162-144 = 18 px) between library thumb and text; 10 dp for state rows | XML |
| `pv.gba.size.statusBar` | 44 dp (77 px) | XML |
| `pv.gba.size.toolbar` | 48 dp (84 px) | XML |
| `pv.gba.size.libraryRow` | 74 dp (130 px) | XML |
| `pv.gba.size.libraryThumb` | 80x72 dp | XML |
| `pv.gba.size.drawerWidth` | 280 dp (490 px) | XML |
| `pv.gba.size.drawerRow` | 48 dp | XML |
| `pv.gba.size.listRow` (dialog/menu) | 48 dp (84 px) | XML |
| `pv.gba.size.prefRowSingle` | 55 dp (96 px pitch) | XML (04) |
| `pv.gba.size.prefHeader` | 35 dp (62 px) | XML |
| `pv.gba.size.stateRow` | 101.7 dp (178 px pitch); thumb 100x88 dp | XML |
| `pv.gba.size.dialogTitleBar` | 53 dp (93 px incl. 8 dp divider zone) | XML |
| `pv.gba.size.dialogButtonRow` | 62 dp (109 px); buttons 54 dp tall | XML |
| `pv.gba.size.toolbarIcon` | ~24 dp glyph in a 48 dp button (glyph size PNG~) | XML + PNG~ |
| `pv.gba.size.drawerIcon` | ~24 dp glyph, left at 16 dp | PNG~ |
| `pv.gba.size.pillLR` | 87x27 dp, fully rounded | PNG~ |
| `pv.gba.size.pillSelStart` | 63x21 dp, fully rounded | PNG~ |
| `pv.gba.size.padFace` | 64 dp circle | PNG~ |
| `pv.gba.size.dpadSegment` | 48 dp square, cluster 142x143 dp | PNG~ |
| `pv.gba.radius.dialog` | small, about 2-4 dp (PNG~, unmeasured) | PNG~ |
| `pv.gba.radius.pill` | full (half of height) | PNG~ |
| `pv.gba.radius.dpadSegment` | about 8 dp, outer corners of left/right flared | PNG~ |
| `pv.gba.elevation.*` | UNKNOWN. Toolbar looks flat (no visible shadow); dialogs show no obvious shadow. PROPOSED: toolbar 0, dialog 8 dp, drawer 16 dp | n/a |

## 7. Interaction rules

Observed unless tagged. Tap-only; long-press and swipe behaviours were not exercised and are UNKNOWN unless noted.

- Library row tap: launch game (observed: 08 follows tapping the row). Long-press: UNKNOWN (likely favourite/delete menu, not seen).
- Library toolbar: search (filter, inferred), folder (game-folder picker, from the research note), import file, overflow ("View", "Rescan storage"). The title shows the active list name ("All"); Favorites/History/All are set from the drawer.
- Thumbnails are black until a game has been played; the thumbnail is the game's saved state/screenshot (inferred from the state list showing the same image).
- Sub-info line: "Not played" until played, then last-played info (exact text UNKNOWN), then platform and size ("GBA 32.00 MB").
- Quick save: down-arrow glyph in the action row writes the quick-save slot immediately (observed on device: the state file appeared on tap). The slot is named "Slot1 (Quick save)". Quick load: up-arrow glyph (inferred, untested).
- Menu > Load state / Save state open the slot list. Slots: "Undo loading state" (a pseudo-slot, shown first, empty until a load happened), Slot1 (Quick save) through Slot7. Each shows a thumbnail and a timestamp or `<Empty>`. Whether Save state uses the same screen and the confirm/overwrite rule is UNKNOWN.
- Fast-forward glyph: toggles fast forward (inferred). Which speed it uses is UNKNOWN; Speed dialog sets the speed value (x0.25, x0.5, x1 default, x2, x4, x8, x16). Fast-forward and Speed relationship is Q5.
- Controller Settings: four checkboxes (Hide virtual pad, button A turbo, button B turbo, Toggle L/R). "Toggle L/R" semantics (latching shoulder buttons vs swapped) UNKNOWN. Turbo = autofire while held (inferred).
- Settings "Enable A+B between A and B": adds a combined A+B target in the gap (inferred from wording). "Dpad diagonals area size": size of the diagonal zones of the d-pad (inferred).
- Haptics: configurable only in Settings > Virtual pad > "Vibration setting" (strength or off). No haptics seen elsewhere.
- Cheats: gated by Settings > Other > "Enable cheating" (off by default). Add dialog creates entries; type "Unspecified" auto-detects. List row layout and toggles are UNKNOWN (list was empty).
- Autosave: "Load autosave state at startup" setting; autostate file is rewritten periodically during play (from the research note).
- Exit: Menu > Exit leads to a confirm dialog with elapsed play time and three choices (Save, Don't save, Cancel); Save presumably writes the autostate (inferred).
- Gamepad/keyboard (CLAUDE.md 9.4, REQUIRED for Pocketverse, not observed): every screen and dialog is fully focus-navigable with D-pad/keys; physical gamepads and keyboards are first-class; "Auto hide Virtual Pad" hides the touch pad when a gamepad is in use (observed as a setting).

## 8. Animation notes

No screen recording was made, so all motion is UNKNOWN. Not observed: drawer slide, dialog enter/exit, activity transitions, menu appear, press feedback on pad buttons, pad opacity changes, fast-forward indicator, toast/snackbar for quick save or screenshot.

PROPOSED defaults (our own, not copied):
- Drawer: slide in 250 ms standard ease-out, scrim fades to 0.6.
- Dialogs: fade + 4% scale-in, 150 ms; exit 100 ms.
- Activity push/pop: 200 ms horizontal slide 16 dp plus fade.
- Pad press: fill brightens ~15%, instant on, 80 ms off.
- Quick save: short transient label "Quick saved" (our wording) for 1 s, no sound.

## 9. Edge cases

- Rotation: landscape was NOT captured. `GameActivity` follows the activity beneath (not locked) per the manifest; Settings contains "Orientation", "Layouts (Portrait)" and "Layouts (Landscape)" entries, so the pad layout is stored per orientation. Landscape pad placement, game frame size and where the action row goes are UNKNOWN (Q1). Until the owner supplies landscape captures, Pocketverse landscape is PROPOSED: game frame full height, pad split to left/right edges with the same relative arrangement, action row as a slim overlay.
- Notches/cutouts: not captured. PROPOSED: apply window insets for status bar and cutout, keep the pad inside the safe area, never place buttons under a cutout.
- Foldables / multiple resolutions: reference setting "Save layouts by screen resolution" (summary "Recommended to enable on foldable devices.") stores one pad layout per resolution. Pocketverse must offer the same, keyed by (orientation, window size class).
- Gamepad focus: required (section 7). Focus order proposal: toolbar, list, dialogs top to bottom; in game, Menu key opens S10.
- Accessibility: observed targets under 48 dp: L/R pills (27 dp tall), SELECT/START (21 dp tall), possibly the action-row glyphs (hit areas unknown). Per CLAUDE.md 9.4 the default keeps the original look; add an accessibility toggle in Pocketverse settings that enlarges pad hit areas to at least 48 dp (visual size unchanged where possible). Deviation to be logged in PARITY.md.
- Dark/light: the reference is dark-only for the main UI (#303030) with one light surface, the drawer (#FAFAFA). Light mode: none observed. PROPOSED: no light variant.
- Text scale: UNKNOWN. PROPOSED: respect system font scale up to 130%; rows grow, pad does not.
- Long titles: library row title wraps/ellipsises: UNKNOWN. PROPOSED: single-line ellipsis for title, two lines for sub-info.
- Empty library state, scoped-storage permission prompts, import errors: not captured (UNKNOWN).

## 10. Open questions for the owner

1. Landscape: please rotate the phone and capture (or describe) the library, in-game and pad layout editor; none of it is in the reference.
2. Pad layout editor (`EditLayoutActivity`): not entered. Do you use it? What do you move and resize?
3. Quick-load glyph and fast-forward: confirm the up-arrow loads the quick slot and which speed the fast-forward glyph jumps to (and whether it is hold or toggle).
4. Font: the reference font is not identifiable. OK with an OFL geometric sans (for example Montserrat) as our default?
5. Cloud settings: the reference requires a separate companion app. Pocketverse is offline-first with no accounts; propose replacing this row with "Backup and restore" (local export/import of a game folder). Confirm, or drop the row.
6. Ads/upsell removal leaves the game frame position undefined (does it move up when the ad is absent?). Confirm you want the action row directly under the status bar.
7. Theme variants: do you want all 9 colour variants, or only the orange default plus a few? Which of the 8 other values do you want to pick yourself?
8. Library: what do "History" and "Favorites" record, and how does a game become a favourite (long-press?). Not observed.
9. Menu items "Reset" and "Screenshot": confirmation and feedback (toast, save location) are unknown. Any behaviour you want preserved?
10. Save-state slot layout: keep exactly 7 slots plus "Undo loading state" and quick-save as slot 1?

## 11. Parity checklist (stub for `docs/design/PARITY.md`)

| Reference screen | Reference behaviour | Our implementation | Intentional deviation |
|---|---|---|---|
| S1 Library | Orange toolbar with hamburger, list title, search, folder, import, overflow; 74 dp rows with black-until-played thumbnail, title, status, "GBA size" | TBD Phase 6 | Rows driven by Pocketverse library DB; all strings our own |
| S2 Drawer | 280 dp light drawer: Favorites/History/All, divider, Settings/Theme/Cloud/Help/Purchase/About | TBD Phase 6 | Purchase row removed; Cloud row replaced by backup/restore (pending Q5) |
| S3 Library overflow | "View", "Rescan storage" | TBD Phase 6 | None planned |
| S4 Settings | Flat preference list, accent section headers, checkboxes | TBD Phase 6 | Extra entries for Pocketverse-wide settings; ads/billing entries absent |
| S5 Theme dialog | 9 radio rows, CANCEL | TBD Phase 6 | Own colour names, no console names |
| S6 Cloud settings dialog | Prompts companion-app install | TBD Phase 6 | Dropped (no companion app) |
| S7 About | Icon, name, version, scoped-storage line, developer, email, privacy | TBD Phase 6 | Own icon/name/contact; licence and attributions screen added |
| S8/S9 In-game portrait | Ad row, action row (save, load, FF, menu), 3:2 game frame, pad (L/R, d-pad, A/B, SELECT/START), clock and battery | TBD Phase 6 | Ad row removed; pad hit areas can be enlarged via accessibility toggle |
| S10 In-game menu | 10-row modal list | TBD Phase 6 | "Purchase" row removed (9 rows) |
| S11 State list | Undo + Slot1 (Quick save) to Slot7 with thumbnail and time | TBD Phase 6 | Storage per game folder `state/` (CLAUDE.md 7.1), not flat |
| S12 Cheats list | Toolbar Add/Help, gated by "Enable cheating" | TBD Phase 6 | Shared `feature-cheats` UI; supports more code families (GBA: GameShark/Action Replay/Code Breaker) |
| S13 Add-cheat dialog | Name, type spinner, code field, HELP/CANCEL/OK | TBD Phase 6 | Type list extended (adds Action Replay); decode status indicator added |
| S14 Type spinner | 5 types | TBD Phase 6 | List differs as above |
| S15 Cheat help | Accepted shapes text | TBD Phase 6 | Our text |
| S16 Controller Settings | 4 toggles | TBD Phase 6 | None planned |
| S17 Speed dialog | x0.25 to x16, default x1 | TBD Phase 6 | None planned |
| S18 Exit confirm | Save / Don't save / Cancel with play time | TBD Phase 6 | None planned |
| S19 Interstitial ad | Full-screen ad | n/a | Dropped (no ads) |


---

## Addendum A (2026-11-12): landscape and layout editor, captured

Captured on the owner's phone with the system rotation temporarily set to landscape (original values 0/0 restored afterwards). Screen 1600 x 720 px, 1 dp = 1.75 px. Raw captures: `.re-workspace/ui-reference/johngbalite/L01..L07_*` (gitignored). Pad elements are custom drawn, so their numbers are eyeballed from the PNG (marked ~); the library and dialogs are measured from the XML.

### A.1 Library in landscape (observed)

- The list becomes a **grid of square tiles**. Tile thumbnail about 312 x 312 px (~178 dp) with a one-line title strip under it (title ellipsised), tile origin x=4 px.
- A small **orange dot** (~20 px) sits at the thumbnail's top-right on a game that has been played (it was absent while "Not played"). Meaning inferred: played or has saved state. Unverified.
- The thumbnail is the last screenshot of the game; unplayed games show black (Task 3).
- Toolbar is 84 px (48 dp) tall and starts at y=0: **the status bar is hidden in landscape**. Search, Game folder and Import file icons sit at the right; the overflow icon is at the far right edge. Drawer behaviour is as in portrait.

### A.2 In-game in landscape (observed)

- The game image is scaled to full height: about 1080 x 720 px (~617 x 411 dp), centred, with black bars (x 260-1340 px, about 16%-84% of the width). No advertisement row appears in landscape in this capture (ads are dropped in our app regardless).
- Control placement (px, ~): L pill x 225-377, y 268-318; R pill x 1307-1459, y 268-318 (about 150 x 50 px, 86 x 29 dp; shoulders sit about a third of the way down, not at the top corners); d-pad box x 141-405, y 389-655 (266 px, 152 dp); A circle x 1293-1404, y 389-500 (111 px, 63 dp); B circle x 1293-1404, y 543-654; between A and B an "A+B" zone (the setting "Enable A+B between A and B"); SELECT pill x 645-755, y 613-649 and START pill x 943-1053, same row, overlapping the lower part of the game image.
- The four action icons (quick save, quick load, fast-forward, menu) sit in the **top corners on the black bars**: save arrow (189, 48), load arrow (364, 48), fast-forward (1278, 48), menu (1467, 48), icon size about 42 px. All controls are drawn translucent over the picture.
- Clock bottom-left (28, 686) and battery percent bottom-right (1511, 686), same small text as portrait.
- The menu dialog is a scrolling list: in landscape only eight of the ten rows fit (rows 86 px / 49 dp tall, dialog x 347-1330), so the last rows need a scroll inside the list (a swipe that starts outside the list closes the dialog).
- **Exit confirmation (observed):** choosing Exit opens a dialog "Do you want to save changes and exit?" with the play time and three buttons: DON'T SAVE, CANCEL, SAVE. A full-screen ad followed DON'T SAVE (dropped in ours).

### A.3 Layout editor (observed, Settings > Layouts (Landscape))

- Opens full screen with a **grid background**, the game area shown as a **teal rectangle** (x 260-1340 px), every control wrapped in a thin **blue bounding box**, and the centred hint text: "Drag controls to move. Long click to change size."
- Editable items: the screen, the four action icons, L, R, d-pad, A, B, an A+B overlap zone, SELECT, START.
- Back opens a dialog with: Edit display items, Menu button size, Fixed screen position (a check item), Screen scaling mode, Reset, Cancel, Save. We cancelled: nothing was changed.
- Layouts are stored per orientation, and "Save layouts by screen resolution" makes a separate set per display size (recommended by the app for foldables).

### A.4 Resolved and still-open questions

Resolved: landscape layout of the library, the in-game screen and the layout editor; the exit-confirm dialog. Still open: the sub-dialogs of "Edit display items", "Menu button size" and "Screen scaling mode" (not opened); whether the orange dot means "has state"; the fast-forward glyph speed; the up-arrow behaviour (quick load assumed).
