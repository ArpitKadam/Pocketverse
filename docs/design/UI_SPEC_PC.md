# UI spec: PC mode (reference: JoiPlay and its RPG Maker / Ren'Py plugin overlays)

Status: DRAFT for owner review, 2026-11-12. Written in our own words. Phase 0 deliverable (CLAUDE.md section 9.3). Nothing here is binding on Phase 6 until the owner approves it.

Evidence labels used throughout:

- **Observed**: measured or seen in the owner's own captures (raw screenshots and `uiautomator` dumps stay in the gitignored `.re-workspace/ui-reference/`; none are embedded or copied here).
- **Source**: read in JoiPlay's public MIT libraries (`joipad`, `commons`; structure only, no code copied).
- **Inferred**: concluded from observations, not confirmed.
- **PROPOSED**: our own default; not a measurement of the reference.
- **Unknown**: not captured or not determinable.

Capture device: 720 x 1600 px at 280 dpi, so 1 dp = 1.75 px. Library and settings were captured in portrait; game screens in landscape 1600 x 720 px. dp = px / 1.75.

---

## 1. Purpose, scope, legal line

**Purpose.** PC mode is the Pocketverse UI used for `.exe`-style games: RPG Maker XP/VX/VX Ace/MV/MZ and Ren'Py (CLAUDE.md 9.1). It recreates the look, layout and feel of JoiPlay's library and settings and of the in-game overlay shown by the JoiPlay RPG Maker and Ren'Py plugins, so the owner gets the familiar launcher.

**In scope.** Library, add-game wizard, archive import, filter, add and overflow menus, game card menu, delete dialog, settings, the in-game overlay (pad, top bar, bottom handle, close confirmation), the plugin info screens and the permission dialog flow (the last only as something we plan to avoid).

**Out of scope here.** GBA and NDS modes (separate specs). Per-engine cheat internals (`docs/research/re/joiplay-rpgm-plugin.md` section 6 and the cheats module docs). Storage design (`docs/architecture/STORAGE.md`).

**Legal line (CLAUDE.md 2.1 and 9.2).**

- We copy layout, flow, interaction and measured values. We do not copy artwork. No JoiPlay icon, logo, launcher flame mark, plugin icon, bitmap, skin, screenshot, or font file ships in Pocketverse. Every glyph and illustration is redrawn originally (Compose vectors) or taken from a freely licensed set recorded in `docs/licenses/THIRD_PARTY.md`.
- The app name "JoiPlay" and the plugin names never appear in the product. Our strings follow the same structure and tone but are written fresh. The strings quoted in this document are descriptions of the reference, not text to reuse.
- Deliberately **not reproduced**: advertising and ad consent flows; the Patreon/supporter build identity and any upsell; the online compatibility list and the online hub (`Compatibility List` overflow entry, hub deep link); billing and licence checks of any kind. Pocketverse is offline-first (CLAUDE.md 1).
- Colour values are facts and are recorded below under our own token names with the `pv.pc.` prefix.

---

## 2. Screen inventory

Numbers in brackets are the reference capture names, for the owner's side-by-side review.

### 2.1 Library (portrait, landscape) [joiplay/01, 12, 19; renpy/06, 15]

- Top app bar, 64 dp high, left-aligned title (our own product name in place of the reference name), three actions at the right: **Add** (plus), **Filter** (sort-lines icon), **More** (three dots).
- Body: a grid of game cards. Observed default layout: one card size, about 166 x 223 dp (ratio 3:4), icon-as-cover with a translucent info panel at the bottom. Empty library: a blank dark body with no illustration and no empty-state text (observed; PROPOSED improvement in 9.6).
- **Layouts** (overflow, Layout submenu): Grid small, Grid large, List. The names come from the RE note; the submenu itself was **not captured** and only the default grid was measured. Small, large and list metrics are Unknown (open question 3).
- Card contents: cover image (the game icon, centre-filled), three-dot button top-right, info panel with game title (bold), version, and detected type text such as "RPG Maker (mkxp-z)" or "Ren'Py".
- Cards respond to tap (launch) and to the three-dot button (card menu, 2.9). Long-press behaviour is Unknown (7.1).

### 2.2 Add menu [joiplay/02]

A popup anchored under the Add action, four rows: **Add Game**, **Install Package** (JGP), **Import RGA File**, **Import Archive File**. Our scope: Add Game and Import Archive File are in. Install Package and Import RGA File are tied to JoiPlay-specific container and patch formats; see open question 5.

### 2.3 Add-game wizard [joiplay/pick*, 09-12; renpy/03-06]

Chain of centred dialogs (the reference builds them with a stock Material dialog library, so they look like a family):

1. **Choose launch file**: message explaining that the user must pick the file that starts the game (EXE, SH, PY or HTML), buttons CANCEL / CHOOSE. CHOOSE opens an in-app folder browser dialog (2.4).
2. **Name**: outlined text field, pre-filled (observed: the launch-file folder name, or the internal id when nothing better exists), helper text "enter a name", buttons CANCEL / NEXT.
3. **Version**: outlined text field, helper text "enter version", buttons CANCEL / NEXT. The card later shows "1", so the default version string is "1" (observed on the card; the field was not pre-filled in the dump, it showed the hint text only).
4. **Icon (optional)**: explanation text (PNG, JPG, ICO, WEBP accepted), a square preview of the current icon (placeholder art when none), buttons CHOOSE (left, neutral) / CANCEL / NEXT.
5. Game added; library refreshes. No confirmation toast was captured (Unknown).

Note the Ren'Py capture shows the same flow; the version step was captured only in the RPG Maker run, but the wizard has no per-engine branch (Inferred).

### 2.4 Folder browser / file picker dialog [joiplay/07, pick*]

A tall dialog whose title is the current folder name ("Internal Storage" at the root), a scrolling list of rows (icon + name, folders first as captured, then files such as the `.zip`), buttons CANCEL / CHOOSE. Tapping a row descends; tapping the first row ascends (the row named for the parent; its exact label is Unknown). Rows are 52 dp tall. This is a stand-in for the Android document picker; see open question 6 for what we use.

### 2.5 Import-archive progress [joiplay/08]

A small dialog with one line of text ("Extracting game files...") and a thin horizontal progress bar. Not cancelable in the capture (no buttons). Whether the bar is determinate is Unknown (only one frame captured). A 2.6 GB archive took about 11 minutes on the owner's phone (RE note), so progress feedback matters: PROPOSED to add a cancel button and a percentage.
After extraction the flow continues at wizard step 2 (name; default name was the internal id because the archive had no usable title).

### 2.6 Filter dialog [joiplay/03]

Centred dialog, 347 dp wide: outlined search field with an end dropdown arrow (label "Search"), a **Type** row (current value "All"; opens a list), a **Sort** row (current value "Unsorted"; sort keys per RE note: name, date, popularity), checkbox "Set as default sorting.", two pill buttons **Clear** and **Filter**. The type and sort pick lists were not captured.

### 2.7 Overflow menu [joiplay/04]

Seven rows: **Layout** (submenu arrow), Compatibility List, Settings, Backup/Restore, Remove Missing Games, Help, About. We reproduce Layout, Settings, Backup/Restore, Remove Missing Games, Help and About. Compatibility List is not reproduced (1).

### 2.8 Settings [joiplay/05, 06]

Back-arrow toolbar titled "Settings". Below: a **Game Selection** outlined dropdown (current value "All": global; or one chosen game: per-game override), then **seven collapsible groups**, all collapsed on entry, in this order: App Settings, Gamepad Settings, Ren'Py Settings, HTML Settings, RPG Settings, P.Essentials Settings, Ruffle Settings. Footer: **Reset to Default** and **Clear Runtime Packages** (tonal pills, centred), plus a **save** floating button at the bottom-right. Row content seen: only the P.Essentials group was expanded in the captures (four switch rows with descriptions). The contents of the other six groups come from the RE note and are not visually captured. Details in 4.3 and section 7.

### 2.9 Game card menu [joiplay/18, 20, 21]

A popup list anchored to the card's three-dot button. Each row has a bold title and a one-line grey description. Observed order from three scroll positions: **Patch** (apply a patch file), **Export** (as a package file), **Decrypt Files**, **Unpack/Repack Scripts**, **Optimize Maps**, **Edit** (change game information), **Delete**, then **Create Shortcut**, **Open Folder**, **About**, **Settings** (per-game), **Cancel** ("Close this dialog"). The list scrolls; whether another row sits between Delete and Create Shortcut is Unknown. The first five rows are RPG Maker XP-family tools (Task 7); which rows a Ren'Py card shows was not captured (open question 4). Our "Play" is the card tap itself; the menu has no Play row (observed: Patch is first).

### 2.10 Delete dialog [joiplay/22; renpy/16]

Message "Do you want to remove <title> from launcher?", a checkbox "Delete '<folder>' folder." (unchecked by default; the folder text is the internal id for the archive-imported game and the folder name for the Ren'Py game), text buttons NO / YES. This is the pattern CLAUDE.md 7.1 wants: remove from library vs delete folder. PROPOSED addition: a third option to export `save/` first.

### 2.11 In-game screen: RPG Maker plugin [joiplay/13-17]

Landscape only (see 9.5). Full-screen black game surface with a Windows-keyboard-style overlay of ordinary Android views on top:

- Top corners of the pad area: **ENTER** and **F2** at the left, **F8** and **ESCAPE** at the right (observed labels). The mapping from the model names (L, CL, CR, R) is in 5.2.
- **D-pad** (8-way, left), a plus-shaped cross with a centre dot.
- **Action cluster** (right): six round keys in a 3 x 2 layout labelled Z, X, CTRL_LEFT, SHIFT_LEFT, Q, R (observed labels are the raw key names, in small type on the CTRL and SHIFT keys).
- A small **rotate/refresh** icon button at the bottom-right corner.
- **Top handle** (tab at top-centre) toggles a **top bar** with six icon buttons: close, rotate, volume, keyboard, fast-forward, settings.
- **Bottom handle** (tab at bottom-centre) hides and shows the whole pad.
- Tapping **close** opens a confirm dialog: title "Close", text "Do you want to close the game?", outlined **No** and filled **Yes** buttons.
- The engine's own game image is drawn behind; in the capture the 640 x 480-class game image sits top-left with black margins (RE note). Scaling and alignment come from engine settings, not from this overlay.

### 2.12 In-game screen: Ren'Py plugin [renpy/12-14]

Same overlay and handles. Differences (observed): the **top bar's fifth icon is a "cheats" (magic wand) icon instead of fast-forward**; and the pad starts **collapsed** (frame 12 shows only the two handles; frame 14 shows the pad after the bottom handle is tapped). The pad is the same RPG-style pad, labelled with keyboard keys (observed), not Ren'Py-specific. The game surface is the engine's own UI (a text box in the capture).

### 2.13 Plugin info / launcher screens [joiplay-plugins/cyou.joiplay.rpgm]

Each plugin APK shows an info page when started on its own: app icon (rounded square, 70 dp), name, version, a paragraph saying it is a plugin and not a standalone app, a notice listing the upstream engines and licences with links, the full licence text in a scroll area, a **Hide icon from the launcher** checkbox, and two buttons: **Close** and **Save Logs**. Surface colour #333333, buttons white with dark text and small corner radius. Pocketverse has no separate plugin APKs (the engines run inside the app), so this screen becomes our **About and licences** page (PROPOSED in 4.5): same structure, notices per engine, a Save Logs button, no "hide icon".

### 2.14 Permission dialog flow [renpy/07-11]

On the first launch of a plugin game the reference shows, in order: (a) Android's own runtime permission prompts (music/audio, photos and videos) from the system permission controller (not JoiPlay's design; the system draws them); (b) a plugin-drawn alert "Permission: needs all files access ... please enable this permission to continue." with **Close** (outlined) and **OK** (filled); (c) the system "All files access" settings page with a toggle; (d) the game starts. Pocketverse **plans to avoid this flow** by running engines inside the app on app-owned storage (RE note, JoiPlay lesson 5; final decision in `STORAGE.md`). If a future choice forces a storage permission, PROPOSED: one in-app explanation dialog in the same look as the close-confirm dialog, then the system page.

---

## 3. Navigation graph

```text
                         +-------------------+
        app start -----> |      LIBRARY      | <-----------------------------+
                         +-------------------+                               |
   Add(+)   Filter   More(:)    card tap     card (:)                        |
     |        |        |           |            |                            |
     v        v        v           v            v                            |
 ADD MENU  FILTER    OVERFLOW   LAUNCH       CARD MENU                       |
  |   |    DIALOG     |  |  |      |          | | | | | |                    |
  |   |      |        |  |  |      v          | | | | | +-- Cancel --------+
  |   |   Clear/      |  |  |  (storage perm  | | | | +---- About (dialog) --+
  |   |   Filter -----+--|--|-- flow: AVOIDED)| | | +------ Settings(game) -> SETTINGS
  |   |                  |  |      |          | | +-------- Open Folder / Create Shortcut
  |   |                  |  |      v          | +---------- Delete -> DELETE DIALOG -> LIBRARY
  |   |                  |  |  IN-GAME        +------------ Edit -> name/version/icon dialogs
  |   |                  |  |  (RPGM or Ren'Py)             Patch/Export/Decrypt/Unpack/Optimize
  |   |                  |  |    |  |   |  |
  |   |                  |  |    |  |   |  +-- top handle -> TOP BAR -> close -> CLOSE CONFIRM
  |   |                  |  |    |  |   |                              |   Yes -> LIBRARY
  |   |                  |  |    |  |   |                              |   No  -> IN-GAME
  |   |                  |  |    |  |   |                              +-- settings -> (Unknown)
  |   |                  |  |    |  |   +-- bottom handle -> hide/show pad
  |   |                  |  |    |  +-- cheats / F8 -> in-game cheat scene or console
  |   |                  |  |    +-- rotate -> toggles portrait/landscape
  |   |                  |  +-- Settings ----------------------------> SETTINGS (global)
  |   |                  +---- Layout -> Grid small | Grid large | List
  |   |                  +---- Backup/Restore, Remove Missing, Help, About
  |   |
  |   +-- Import Archive File -> FOLDER BROWSER -> PROGRESS -> NAME -> VERSION -> ICON -> LIBRARY
  +------ Add Game -> CHOOSE-FILE msg -> FOLDER BROWSER -> NAME -> VERSION -> ICON -> LIBRARY

 SETTINGS: back arrow -> LIBRARY. Game Selection dropdown (All | <game>) re-targets every row.
           save FAB writes; Reset to Default; Clear Runtime Packages.
```

---

## 4. Wireframes (ASCII)

Not to scale; see section 5 for dimensions.

### 4.1 Library, portrait

```text
+------------------------------------------+
| (status bar 44 dp)                       |
+------------------------------------------+
| <App name>                  +    =    :  |  toolbar 64 dp
+------------------------------------------+
|  +-----------+  +-----------+            |
|  |       (:) |  |           |            |
|  |  cover    |  |           |            |  grid, 2 columns (Inferred)
|  |  image    |  |           |            |  card 166 x 223 dp
|  | +-------+ |  |           |            |
|  | |Title  | |  |           |            |
|  | |1      | |  |           |            |
|  | |RPG Mk.| |  |           |            |
|  | +-------+ |  |           |            |
|  +-----------+  +-----------+            |
|                                          |
|              (gesture bar 24 dp)          |
+------------------------------------------+
```

### 4.2 Library, landscape

```text
+-cutout-+------------------------------------------------------------+
| 44 dp  | <App name>                                  +    =    :    |
| inset  +------------------------------------------------------------+
| black  | +-------+                                                  |
|        | | cover |   grid fills width; column count Unknown         |
|        | +-------+                                                  |
+--------+------------------------------------------------------------+
```

The left 44 dp strip is the display cutout side; the app insets its content (observed in every landscape capture).

### 4.3 Add-game wizard dialogs (each is a separate 347 dp dialog, shown one at a time)

```text
 Step 1                         Step 2                       Step 3
+----------------------------+ +--------------------------+ +--------------------------+
| Pick the file that starts  | | +----------------------+ | | +----------------------+ |
| the game (EXE, SH, PY,     | | | Game name         v  | | | | Version           v  | |
| HTML are supported).       | | +----------------------+ | | +----------------------+ |
|                            | |   Enter a name.          | |   Enter a version.       |
|        CANCEL    CHOOSE    | |        CANCEL     NEXT   | |        CANCEL     NEXT   |
+----------------------------+ +--------------------------+ +--------------------------+

 Step 4 (icon, optional)                Import progress
+------------------------------------+ +---------------------------------+
| Choose an image for the game icon. | |        Extracting game files... |
| PNG, JPG, ICO, WEBP. (Optional)    | | ============-------------------- |
|                                    | +---------------------------------+
|              +------+              |
|              | icon |  83 dp       |
|              +------+              |
| CHOOSE          CANCEL     NEXT    |
+------------------------------------+
```

### 4.4 Settings, portrait (groups collapsed, then P.Essentials expanded)

```text
+------------------------------------------+
| <-  Settings                             |  toolbar
|  +------------------------------------+  |
|  | Game Selection                     |  |  outlined dropdown, 62 dp
|  | All                              v |  |
|  +------------------------------------+  |
|                                          |
|  App Settings                          v |  group header, pitch 72.6 dp
|  Gamepad Settings                      v |
|  Ren'Py Settings                       v |
|  HTML Settings                         v |
|  RPG Settings                          v |
|  P.Essentials Settings                 ^ |  (expanded)
|     P.Essentials Fixes           [ ON  ] |  title + 2-line description + switch
|     Input Overrides              [ off ] |
|     Tilemap Fix ...              [ ON  ] |
|     Disable Autotile Animation   [ ON  ] |
|  Ruffle Settings                       v |
|                                          |
|          ( Reset to Default )            |  tonal pill 48 dp
|      ( Clear Runtime Packages )   [save] |  FAB 56 dp, bottom-right
+------------------------------------------+
```

### 4.5 In-game, landscape, RPG Maker, top bar open (percent of the 1523 x 720 content area)

```text
 x: 0%                                    48%                                   100%
   +---------------------------------------[^]-------------------------------------+ y 0%
   |                                  [X][R][V][K][>>][*]   top bar 36%..64% x,     |    11%..19% y
   |  game image (engine draws; margins black)                                      |
   |                                                                                |
   | [ENTER][F2]                                                      [F8][ESCAPE]  |    42%..50%
   |   +--+                                                              (Z) (X)    |
   | +-+--+-+                                                          (CTRL)(SHIFT)|
   | | d-pad|  156 dp square                                             (Q) (R)    |    56%..94%
   | +-+--+-+                                                                  (o)  |    rotate 90%..98%
   |   +--+                                                                         |
   +---------------------------------------[v]-------------------------------------+ y 100%
```

`X`=close, `R`=rotate, `V`=volume, `K`=keyboard, `>>`=fast-forward (RPG Maker) or a magic-wand cheats icon (Ren'Py), `*`=settings gear.

### 4.6 Game card menu (landscape, anchored under the card)

```text
 +-------+
 |  (:)  |--+------------------------------------------------------------+
 | card  |  | Patch                                                      |
 |       |  | Apply a RGA patch to the game.                             |
 +-------+  |                                                            |
            | Export                                                     |
            | Export the game as a JGP file.                             |
            | Decrypt Files / Unpack/Repack Scripts / Optimize Maps      |
            | Edit / Delete / Create Shortcut / Open Folder / About      |
            | Settings / Cancel                    (list scrolls)        |
            +------------------------------------------------------------+
```

---

## 5. Control map

dp = px / 1.75. Percent: library and settings are percent of the 720 x 1600 portrait screen; in-game values are percent of the **content area 1523 x 720 px (870 x 411 dp)**, because the screen has a 77 px (44 dp) cutout inset on the left, so x origin = 77 px. All bounds are the visible bounds in the `uiautomator` dumps. The reference's touch slop beyond the visible bounds is Unknown.

### 5.1 Library and dialogs (portrait)

| Element | x, y (dp) | w x h (dp) | Percent (left, top, w, h) | Notes |
| --- | --- | --- | --- | --- |
| Status bar | 0, 0 | 411 x 44 | 0, 0, 100, 4.8 | system |
| Toolbar | 0, 44 | 411 x 64 | 0, 4.8, 100, 7.0 | |
| Title text | 16, 61 | 70 x 30 | 3.9, 6.6, 16.9, 3.3 | left inset 16 dp |
| Add button | 267, 52 | 56 x 48 | 64.9, 5.7, 13.6, 5.2 | |
| Filter button | 323, 52 | 48 x 48 | 78.5, 5.7, 11.7, 5.2 | |
| More button | 371, 52 | 41 x 48 | 90.1, 5.7, 9.9, 5.2 | touches the right edge |
| Grid view | 15, 123 | 381 x 751 | 3.8, 13.5, 92.5, 82.2 | |
| Card | 18, 126 | 166 x 223 | 4.4, 13.8, 40.4, 24.4 | ratio 3:4; grid width 666 px = 2 x 333 px, so 2 columns (Inferred) |
| Card three-dot | 164, 131 | 15 x 26 | 39.9, 14.4, 3.8, 2.9 | below 48 dp, see 9.4 |
| Card title / version / type | 29, 294 / 312 / 326 | 94 x 18 / 3 x 14 / 97 x 14 | | three lines, text inset 10 dp from the card edge |
| Gesture bar | 0, 890 | 411 x 24 | 0, 97.4, 100, 2.6 | system |
| Add popup | 215, 106 | 196 x 48 per row | 52.4, 11.6, 47.6, 5.2 | 4 rows |
| Overflow popup | 190, 106 | 217 x 48 per row | 46.2, 11.6, 52.8, 5.2 | 7 rows, text inset 16 dp |
| Filter dialog | 32, 317 | 347 x 301 | 7.8, 34.6, 84.4, 32.9 | margin 32 dp each side |
| Filter: search field | 42, 341 | 327 x 61 | 10.3, 37.2, 79.4, 6.7 | |
| Filter: Type / Sort rows | 42, 402 / 450 | 327 x 48 | | value button 88 dp wide, right-aligned |
| Filter: checkbox row | 42, 498 | 183 x 48 | | |
| Filter: Clear / Filter | 86, 546 / 238, 546 | 88 x 48 | 20.8, 59.7 / 57.8, 59.7 | pills |
| Wizard name dialog | 32, 199 | 347 x 190 | 7.8, 21.8, 84.4, 20.8 | |
| Wizard version dialog | 32, 372 | 347 x 190 | 7.8, 40.7, 84.4, 20.8 | |
| Wizard icon dialog | 32, 310 | 347 x 315 | 7.8, 33.9, 84.4, 34.4 | |
| Icon preview | 164, 450 | 83 x 83 | 39.9, 49.2, 20.3, 9.1 | |
| Folder browser | 32, 96 | 347 x 742 | 7.8, 10.5, 84.4, 81.2 | rows 52 dp, folder icon 36 dp |
| Progress dialog | 32, 415 | 347 x 103 | 7.8, 45.4, 84.4, 11.3 | bar 4 dp thick |
| Delete dialog (landscape) | 257, 118 | 356 x 175 | | centred on the content area; text buttons 64 x 52 dp |

### 5.2 Settings (portrait)

| Element | x, y (dp) | w x h (dp) | Percent | Notes |
| --- | --- | --- | --- | --- |
| Toolbar | 0, 44 | 411 x 64 | | back arrow touch area 56 x 56 dp |
| Game Selection dropdown | 21, 129 | 370 x 62 | 5.0, 14.1, 90, 6.8 | outlined, floating label |
| Group header | 21, 231 | 370 x 31 | 5.0, 25.3, 90, 3.4 | pitch **72.6 dp** (127 px) between headers; chevron touch 31 x 31 dp at the right end |
| Group header y (all collapsed) | 231, 304, 376, 449, 522, 594, 667 | | | seven groups |
| Row: switch | right-aligned, 64 x 32 | | | M3-style switch |
| Row title / description | left 21 dp | title 18 dp tall box, description 12-13 dp per line | | two-line descriptions seen |
| Row pitch | | 45 dp (one-line) / 58 dp (two-line) | | |
| Reset to Default | 127, 729 | 157 x 48 | 31.0, 79.8, 38.1, 5.2 | tonal pill |
| Clear Runtime Packages | 101, 798 | 209 x 48 | 24.6, 87.2, 50.7, 5.2 | |
| Save FAB | 335, 814 | 56 x 56 | 81.4, 89.0, 13.6, 6.1 | 21 dp from right and bottom edges; it covers the last row when a group is expanded (frame 06) |

### 5.3 In-game (content area 1523 x 720 px; x from the content left edge)

| Element | x, y (dp) | w x h (dp) | Left %, top % | w %, h % | Notes |
| --- | --- | --- | --- | --- | --- |
| Top handle | 419, 0 | 31 x 23 | 48.2, 0 | 3.6, 5.7 | centred tab, rounded bottom |
| Bottom handle | 419, 388 | 31 x 23 | 48.2, 94.3 | 3.6, 5.7 | centred tab, rounded top |
| Top-bar container | 0, 0 | 870 x 125 | 0, 0 | 100, 30.3 | transparent hit area |
| Top-bar button row | 310, 46 | 250 x 31 | 35.6, 11.2 | 28.8, 7.6 | six buttons, 31 dp each, 10.4 dp gap |
| Close | 315, 46 | 31 x 31 | 36.2, 11.2 | 3.6, 7.6 | order left to right: close, rotate, volume, keyboard, fast-forward (RPG Maker) or cheats (Ren'Py), settings |
| Rotate | 357, 46 | 31 x 31 | 41.0, 11.2 | | |
| Volume | 398, 46 | 31 x 31 | 45.8, 11.2 | | |
| Keyboard | 440, 46 | 31 x 31 | 50.6, 11.2 | | |
| Fast-forward / cheats | 482, 46 | 31 x 31 | 55.4, 11.2 | | |
| Settings gear | 523, 46 | 31 x 31 | 60.1, 11.2 | | |
| Pad container | 0, 125 | 870 x 287 | 0, 30.3 | 100, 69.7 | |
| ENTER (model L) | 10, 171 | 62 x 31 | 1.2, 41.7 | 7.2, 7.6 | rounded-rect key |
| F2 (model CL) | 83, 182 | 42 x 21 | 9.5, 44.3 | 4.8, 5.0 | smaller key |
| F8 (model CR) | 746, 182 | 42 x 21 | 85.7, 44.3 | 4.8, 5.0 | |
| ESCAPE (model R) | 798, 171 | 62 x 31 | 91.7, 41.7 | 7.2, 7.6 | |
| D-pad | 26, 229 | 156 x 156 | 3.0, 55.7 | 17.9, 37.9 | plus-shaped; arm width about 59 dp |
| Action cluster box | 688, 229 | 156 x 156 | 79.1, 55.7 | 17.9, 37.9 | |
| Z | 713, 235 | 47 x 47 | 81.9, 57.2 | 5.4, 11.4 | round; model X |
| X | 772, 235 | 47 x 47 | 88.7, 57.2 | | model A |
| CTRL_LEFT | 694, 283 | 47 x 47 | 79.8, 68.9 | | model Y |
| SHIFT_LEFT | 791, 283 | 47 x 47 | 90.9, 68.9 | | model B |
| Q | 713, 332 | 47 x 47 | 81.9, 80.7 | | model Z |
| R | 772, 332 | 47 x 47 | 88.7, 80.7 | | model C |
| Rotate / refresh corner icon | 831, 372 | 31 x 31 | 95.5, 90.4 | 3.6, 7.6 | |
| Close dialog | 169, 109 | 533 x 193 | 19.4, 26.5 | 61.3, 46.9 | centred; buttons 247 x 48 dp, gap 8 dp |

Model-to-label mapping (Source: the public `GamePad` model has eight remappable key codes `a, b, c, x, y, z, l, r`, plus opacity and scale (both default 100); the dump view ids are `lButton, clButton, crButton, rButton, xButton, yButton, zButton, aButton, bButton, cButton, switchButton`). Observed assignment for the RPG Maker pad: l=ENTER, r=ESCAPE, x=Z, a=X, y=CTRL_LEFT, b=SHIFT_LEFT, z=Q, c=R; CL=F2 and CR=F8 are fixed keys (no model field). That is Inferred from the position-label pairs; the public model does not state the mapping. Default key assignments per engine type are Unknown beyond these labels.

Pad scale and opacity (Source): the model carries `btnScale` and `btnOpacity`, both 0-100 style integers with default 100. The observed colours match a 70% opacity (see 6.1), so the installed default opacity is probably 70 (Inferred).

---

## 6. Design tokens

All colour values are sampled with PIL from the captures (Observed unless stated). Our names are `pv.pc.*`. Sample point is in screenshot pixels, (x, y). Where a point landed on anti-aliased text I used a nearby clean pixel.

### 6.1 Colour

Library and settings (dark theme as captured; which of the five themes was active is Unknown, see 6.5):

| Token | Value | Sample point (capture) | Use |
| --- | --- | --- | --- |
| `pv.pc.color.surface` | `#1B1B1B` | (360, 800) joiplay/01 | app bar, library body, dialogs, menus, popups |
| `pv.pc.color.scrim` | `#0B0B0B` | (360, 1400) joiplay/03 | surface under a dialog (surface dimmed about 60%) |
| `pv.pc.color.onSurface` | `#E2E2E2` | (516, 133) plus icon, 01; menu text 02 | titles, icons, menu text |
| `pv.pc.color.primary` | `#C6C6C6` | (190, 998) joiplay/03 | filled pill buttons, switch track (on), cover art tint, text-field outline in wizard |
| `pv.pc.color.onPrimary` | `#202020` | (975, 468) joiplay/22 style | text on primary pills (label colour of Clear/Filter looked about the same; approximate) |
| `pv.pc.color.container` | `#464646` | (240, 1300) joiplay/05 | tonal buttons (Reset, Clear Runtime Packages), save FAB, card background, progress track |
| `pv.pc.color.containerHigh` | `#565656` | (250, 545) renpy/06 | card info panel |
| `pv.pc.color.outline` | `#909090` | (36, 285) joiplay/05 | dropdown outline, switch (off) |
| `pv.pc.color.thumbOn` | `#303030` | (656, 1193) joiplay/06 | switch thumb, on |
| `pv.pc.color.cardText` | `#C6C6C6` title, `#919191` type line | (58, 531), (60, 583) renpy/06 | text over the info panel |
| `pv.pc.color.pageBlack` | `#000000` | (800, 400) joiplay/14 | game surface, landscape cutout strip |

The grey wash seen over the grid in two RPG Maker captures (`#828282` at (500, 900), 12_after_add and 19) did **not** appear in the Ren'Py capture of the same screen (`#1B1B1B`). It is treated as a capture artefact (a transient overlay), not a design value.

In-game pad and bars (Observed, matching the public `joipad` colour resources, which are Source):

| Token | Value | Sample point | Note |
| --- | --- | --- | --- |
| `pv.pc.color.pad.key` | `#242424` | (130, 345) joiplay/14 | key, circle, d-pad face |
| `pv.pc.color.pad.keyEdge` | `#303030` | (97, 320) | 1-2 dp border |
| `pv.pc.color.pad.label` | `#B3B3B3` | brightest pixel in ENTER label | key legend and icon glyphs |
| `pv.pc.color.pad.dpadDot` | `#4F4F4F` | (259, 538) | d-pad centre dot |
| `pv.pc.color.pad.handle` | `#242424` | (838, 30) | top and bottom handle tabs, same face as keys |

Source colours in `joipad`: primary `#333333`, primary dark `#2A2A2A`, primary light `#444444`, accent `#CCCCCC`, text `#FFFFFF`, translucent `#80303030`, keyboard overlay `#AA303030`, bar `#70348564`. Our observed pad values are exactly 70% of #333333 (face), #444444 (edge) and #FFFFFF (legend) over black, so the pad is drawn at **70% opacity** (Inferred). PROPOSED token: `pv.pc.pad.opacity.default` = 0.70.

Plugin dialogs (close confirm, permission alert):

| Token | Value | Sample point | Use |
| --- | --- | --- | --- |
| `pv.pc.color.dialog.surface` | `#363940` | (500, 220) joiplay/17 | close-confirm and permission-alert surface (blue-grey, **not** the library grey) |
| `pv.pc.color.dialog.accent` | `#A6C8FF` | (900, 440) | filled button (Yes, OK), outlined button outline and text tint |
| `pv.pc.color.dialog.onAccent` | `#003060` | (1062, 461) | text on the filled button |
| `pv.pc.color.dialog.text` | near white | visual | title and message |
| `pv.pc.color.sysDialog` (reference only) | `#3C4043` surface, `#D5E3FF` buttons | (500, 150), (420, 380) renpy/07 | Android's own permission prompt; not ours to reproduce |

Plugin info screen: surface `#333333` at (360, 900); buttons `#FFFFFF`; link colour near `#D5E3FF`; plugin icon plate `#448A56` (not reproduced: it is the reference's artwork colour).

### 6.2 Typography (APPROXIMATE, from text-box heights in the XML)

The reference face looks like a geometric sans (Montserrat-like) in both the library and the plugin dialogs. That is a visual impression only; the font file was not examined and its licence is Unknown. PROPOSED: Montserrat (SIL OFL 1.1) bundled once, recorded in `THIRD_PARTY.md`, with the system sans as fallback.

| Token | Approx. size | Weight | Basis (box height px / dp) |
| --- | --- | --- | --- |
| `pv.pc.type.appBarTitle` | 22 sp | regular | title box 53 px = 30 dp |
| `pv.pc.type.groupHeader` | 17 sp | bold | box 38 px = 22 dp |
| `pv.pc.type.menuItem` | 16 sp | regular | box 37 px = 21 dp |
| `pv.pc.type.menuTitle` (card menu) | 16 sp | bold | box 38 px = 22 dp |
| `pv.pc.type.menuDescription` | 12 sp | regular | box 24 px = 14 dp |
| `pv.pc.type.rowTitle` (settings) | 16 sp | regular | box 32 px = 18 dp |
| `pv.pc.type.rowDescription` | 12 sp | regular | 23 px per line |
| `pv.pc.type.cardTitle` | 16 sp | bold | box 32 px = 18 dp |
| `pv.pc.type.cardMeta` | 12 sp | regular | box 24 px = 14 dp |
| `pv.pc.type.dialogTitle` (plugin alert) | 24 sp | bold | box 58 px = 33 dp |
| `pv.pc.type.dialogBody` | 16 sp | regular | text box 34-35 px |
| `pv.pc.type.button` | 14 sp | medium | dialog buttons |
| `pv.pc.type.keyLegend` | 12-14 sp bold (small keys), 8 sp (CTRL_LEFT, SHIFT_LEFT) | | measured from screenshot, rough |

### 6.3 Spacing

| Token | Value (dp) | Basis |
| --- | --- | --- |
| `pv.pc.space.screenInset` | 16 | title left inset, menu text inset |
| `pv.pc.space.settingsInset` | 20.6 (use 20) | settings and dropdown left/right |
| `pv.pc.space.gridInset` | 15 | grid view side padding |
| `pv.pc.space.cardGap` | about 3 | card inset in its grid cell (5 px) |
| `pv.pc.space.dialogMargin` | 32 | dialog distance from screen edges (portrait) |
| `pv.pc.space.dialogPad` | 13 | field inset inside a wizard dialog |
| `pv.pc.size.rowMenu` | 48 | menu row height |
| `pv.pc.size.rowPicker` | 52 | folder browser row |
| `pv.pc.size.toolbar` | 64 | |
| `pv.pc.size.groupPitch` | 72.6 | settings header spacing |
| `pv.pc.size.padGapTopbar` | 10.4 | between top-bar buttons |

### 6.4 Radii, elevation, icon sizes

| Token | Value | Basis |
| --- | --- | --- |
| `pv.pc.radius.dialog` | 20 dp | measured on the filter dialog (35 px) |
| `pv.pc.radius.alert` | 24 dp | close-confirm and permission alert (42 px) |
| `pv.pc.radius.pill` | full (24 dp on a 48 dp button) | |
| `pv.pc.radius.fab` | 16 dp | 28 px, matches a medium FAB |
| `pv.pc.radius.card` | about 8 dp | rough, 12-14 px |
| `pv.pc.radius.panel` | about 8 dp | info panel, rough |
| `pv.pc.radius.padKey` | about 4-6 dp (rect keys); full circle (action keys) | rough |
| `pv.pc.radius.handle` | about 12 dp on the two outer corners | rough |
| `pv.pc.elevation.menu` | soft shadow visible under popups; value Unknown. PROPOSED 3 dp | |
| `pv.pc.elevation.dialog` | soft shadow visible; value Unknown. PROPOSED 6 dp | |
| `pv.pc.icon.toolbar` | 24 dp glyph in a 48 dp target | |
| `pv.pc.icon.topbar` | about 20 dp glyph in a 31 dp square | |
| `pv.pc.icon.chevron` | 24 dp in a 31 dp target | |
| `pv.pc.icon.pickerFolder` | 36 dp | |
| `pv.pc.icon.pluginApp` | 70 dp | |
| `pv.pc.icon.cardPreview` | 83 dp | |

### 6.5 Themes and primary colour (feature, not values)

JoiPlay's App Settings offers **five themes**: System, Wallpaper, Day, Night, Amoled, and a **user-chosen primary colour** (RE note section 7; the App group itself was not expanded in the captures). PC mode records this as a feature:

- `pv.pc.theme` in {system, wallpaper, day, night, amoled}.
- `pv.pc.color.primary` is user-overridable.
- Observed values correspond to **one** dark theme with a neutral grey primary. Which setting it was is Unknown. Day (light) and Amoled (true black, probably `#000000` surfaces) values are Unknown and must be captured by changing the setting on the device before Phase 6 (open question 7).
- The plugin dialogs use a blue accent (`#A6C8FF`) while the library uses grey: Inferred that the plugin follows the system dynamic colour (the values are typical Material You blue tones), not JoiPlay's own choice. Unverified.
- PROPOSED: our theme switch applies one palette to the launcher, the in-game overlay and every dialog so they never disagree.

---

## 7. Interaction rules

### 7.1 Library, cards and menus

- Tap a card: launch (Observed, the reference launches the game's plugin). Tap the three-dot button: card menu. **Long-press on a card is Unknown** (not tested); PROPOSED: long-press opens the same card menu.
- Add: opens the four-row popup. Filter: opens the filter dialog; Clear resets the fields, Filter applies, the checkbox stores the sort as the default.
- Overflow: Layout is a submenu (arrow at the right) that swaps the grid layout immediately (small/large/list, Source: RE note).
- Remove Missing Games: scans the library and removes entries whose folders are gone (RE note; no confirmation text captured).

### 7.2 Game types and detection

The type line on the card is the detected engine (Observed: "RPG Maker (mkxp-z)", "Ren'Py"), chosen without asking the user. Detection looks at the folder behind the chosen launch file (RE notes: RPG Maker XP/VX/VX Ace by `Game.ini` + RGSS data; Ren'Py by `renpy/` and `lib/` folders; the reference silently refused a Ren'Py folder lacking them). Our rule (CLAUDE.md 7.2): detect by content, never by `.exe`, and always explain a refusal (the reference shows nothing, which we do not copy). Engine choice for XP-family games between "RPG Maker XP/VX/VX Ace" and "RPG Maker (mkxp-z)" is a visible detail (the latter is what Pokemon Essentials v19+ needs).

### 7.3 Per-game settings override

The Game Selection dropdown in Settings switches the whole screen between **All** (global) and **one game**. A game-specific file (`configuration.json`, observed with a single `cheats` key) travels with the game folder. The card menu's **Settings** item opens the same screen pre-targeted to that game. How the screen visually distinguishes "inherited" from "overridden" is Unknown; PROPOSED: show the global value dimmed with an "overridden" marker when a game differs. Our file is `meta.json` (CLAUDE.md 7.1).

### 7.4 In-game overlay

- **Handles.** Tapping the top handle shows or hides the top bar; the chevron on the handle flips with the state. The bottom handle shows or hides the whole pad. RPG Maker starts with the pad visible; Ren'Py starts with it hidden (Observed). Handles stay on top of everything.
- **Pad keys** send key down on touch and key up on release (Source `joipad`), so holding works; they use a tiny nudge animation on press (Source: a 2% translate).
- **D-pad** is 8-way: touch position decides the direction; diagonals are held as two directions (Source structure; the "diagonal movement" setting lives in Gamepad Settings).
- **Keyboard button** swaps the button pad for a full on-screen keyboard overlay (Source: a translucent `#AA303030` keyboard layer; not captured visually, open question 8).
- **Fast-forward** (RPG Maker XP/VX/VX Ace family only, Source): one tap toggles a held fast-forward key and tints the icon green while on. The speed comes from Settings (RPG group, "fast-forward speed").
- **Cheats**: visible only when the "Enable Cheats" setting is on for the game (Source; the Ren'Py capture shows it, the RPG Maker game had cheats off). The icon is a magic wand on the top bar. In RPG Maker the pad also has a permanent **F8** key that opens the in-game cheat scene when cheats are on (RE note section 6). In Ren'Py the cheats button reaches the developer console (RE note, mechanism Unproven). Our `cheats-script` module should own what these buttons do; this spec only fixes where they sit.
- **Rotate** (top bar icon and the corner icon): toggles the requested orientation between landscape and portrait (Source). The corner icon's behaviour is Inferred to be the same.
- **Volume** and **Settings gear**: no handler exists in the public pad source, and the installed build is newer, so what they open is Unknown (open question 9). PROPOSED: volume opens a master-volume slider popup; the gear opens the per-game settings screen in an overlay.
- **Close**: asks "Do you want to close the game?" with No/Yes. Yes ends the engine and returns to the library. Android Back is not shown to do the same (Unknown).

### 7.5 Orientation and shared state

Plugin games are locked to landscape (manifest, RE notes). The reference's library followed the orientation last chosen in-game, i.e. rotation state is shared across the task (Observed: library stayed landscape after the in-game rotate). PROPOSED: Pocketverse treats the game view and the library as separate: the library follows the system/auto-rotate unless the user pins it; see open question 10.

---

## 8. Animation notes

| Item | Status |
| --- | --- |
| Pad key press feedback | Source: translate 2% x/y out and back. Duration Unknown. |
| Handle chevron flip | Observed as an icon swap; any animation Unknown |
| Top bar show/hide | Source sets visibility directly (no animation in the public source); the installed build may differ. Unknown. |
| Dialog open/close, popup open | Unknown (stock Material behaviour assumed) |
| Library to settings transition | Unknown |
| Progress bar | one frame only |
| **PROPOSED defaults** | popup 120 ms fade + scale from the anchor; dialog 200 ms fade with 8 dp rise; top bar slide 180 ms; pad show/hide fade 150 ms; handle chevron rotate 150 ms; settings group expand 200 ms with chevron rotate; all disabled when the system "remove animations" setting is on |

A `screenrecord` pass for these is not part of the current captures (open question 11).

---

## 9. Edge cases

### 9.1 Rotation
- Library and settings work in both orientations (Observed library; settings only portrait captured). Landscape library keeps the 64 dp toolbar but shrinks the status strip (42 px = 24 dp); the card keeps the same dp size.
- In-game: landscape locked; the rotate action in the reference requests portrait. The portrait pad layout was not captured. Unknown; PROPOSED: in portrait the engine surface sits top-aligned (4:3 area) with the pad below, using the same keys.

### 9.2 Notches and cutouts
Observed: a 44 dp left inset in landscape, content starts after it in the library and the pad (the black strip is outside the content). Rules: never place controls inside the cutout; honour `displayCutout` and system-bar insets; keep the pad's 1.2% outer margin inside the safe area, not from the physical edge.

### 9.3 Foldables and tablets
Not captured. PROPOSED: library grid columns from width classes; the pad anchors to the engine surface's safe area; on large screens the game image is letterboxed and the pad floats over the margins; fold posture changes must not restart the engine.

### 9.4 Gamepad and keyboard focus
The reference is touch-first; physical-controller support appears as the "Input Overrides: enables input overrides to support controllers" option (Observed text). Focus behaviour in the library is Unknown. Requirement from CLAUDE.md 9.4: physical gamepads and keyboards are first-class. PROPOSED: a visible focus ring on cards, toolbar actions, menu rows and dialog buttons; D-pad moves focus; A confirms, B cancels, Start opens the in-game top bar; the pad overlay hides itself while a physical controller is active (matches the Gamepad Settings "hide pad" option).

### 9.5 Accessibility, targets under 48 dp
Measured visible sizes below 48 dp: card three-dot 15 x 26 dp; handles 31 x 23 dp; top-bar buttons 31 x 31 dp; rotate corner icon 31 dp; F2 and F8 keys 42 x 21 dp; d-pad arm about 59 dp wide (fine); action keys 47 dp (borderline). The reference's invisible touch slop is Unknown. Per CLAUDE.md 9.4 we keep the original look by default and offer an **Accessibility: large controls** toggle that enlarges the hit areas to 48 dp without changing the drawing, plus a text-scale pass for the 12 sp lines. Deviation to log in PARITY.md.

### 9.6 Other
- Empty library: the reference shows nothing. PROPOSED: a plain empty state ("No games yet" with an Add action) drawn in tokens; log as a deviation.
- Settings FAB hides the last row when a group is expanded (frame 06). PROPOSED: add bottom padding of 80 dp so the last row can scroll clear; deviation.
- Very long titles on cards: truncation behaviour Unknown. PROPOSED: two lines, then ellipsis.
- Large imports: Android may kill a long extraction when the screen turns off; the reference registers a foreground service for extraction (RE note). We should too.

---

## 10. Open questions for the owner

1. **Approve scope of PC mode.** The reference bundles seven settings groups, including HTML (MV/MZ via WebView) and Ruffle (Flash). Do we show only groups for engines we ship (App, Gamepad, Ren'Py, RPG, P.Essentials) and add HTML when `engine-rpgmv` exists? Flash is not in our scope today.
2. **Which theme was active** on the capture phone (System, Wallpaper or Night)? The measured grey could be a dynamic-colour result. Please set Day and Amoled once and let us capture them, or tell us you do not need them.
3. **Layouts.** Capture the Layout submenu (Grid small, Grid large, List) and the Type and Sort lists? Today only the default grid is measured.
4. **Card menu for Ren'Py** and for MV/MZ games: which rows appear? Only the RPG Maker XP row set was captured.
5. **Install Package (JGP) and Import RGA File.** These are JoiPlay formats. Do you have packages in these formats that you want to import? If not, we omit both and keep Add Game and Import Archive File.
6. **File picking.** The reference uses its own folder browser. We would use the Android document picker plus copy-in (STORAGE.md decision pending). Are you fine with the system picker look instead of the in-app browser look?
7. **Per-game "inherit vs override" display.** The reference does not show which rows differ from global. Acceptable to add the dimmed-with-marker display (PROPOSED)?
8. **On-screen keyboard overlay and portrait pad** were not captured. Do you want them captured on the phone (low risk, read-only screenshots)?
9. **What do the top bar volume and gear buttons open?** Not determined from public source. Do you remember, or should we capture them?
10. **Rotation scope.** The reference shares rotation across the whole app (library follows the game). Do you want the library to stay in its own orientation setting?
11. **Animations.** Do you want a screen recording pass for transitions, or are the PROPOSED defaults in section 8 acceptable?
12. **Fonts.** OK to bundle an OFL geometric sans (Montserrat is the closest look-alike) for PC mode?

---

## 11. Parity checklist stub (CLAUDE.md 9.5)

Filled in Phase 6 and merged into `docs/design/PARITY.md`. "Reference" is the behaviour in words (see sections above).

| Reference screen | Reference behaviour | Our implementation | Intentional deviation |
| --- | --- | --- | --- |
| Library (grid) | app bar with Add, Filter, More; 3:4 cards, info panel with title, version, type; tap launches | TBD Phase 6 | empty state added (PROPOSED) |
| Library (layouts) | Grid small, Grid large, List | TBD Phase 6 | metrics Unknown |
| Add menu | Add Game, Install Package, Import RGA, Import Archive | TBD Phase 6 | JGP and RGA entries only if owner wants them |
| Add-game wizard | choose file, name, version, icon (optional) | TBD Phase 6 | refusals explained in words |
| Archive import progress | text + thin progress bar, no cancel | TBD Phase 6 | cancel and percent added (PROPOSED) |
| Folder browser | title = folder, rows, CANCEL/CHOOSE | TBD Phase 6 | system picker instead (open question 6) |
| Filter dialog | search, type, sort, default checkbox, Clear/Filter | TBD Phase 6 | none planned |
| Overflow menu | Layout, Compatibility List, Settings, Backup/Restore, Remove Missing, Help, About | TBD Phase 6 | Compatibility List omitted (legal line) |
| Settings | per-game dropdown, 7 collapsible groups, Reset, Clear Runtime Packages, save FAB | TBD Phase 6 | groups limited to shipped engines; FAB no longer covers last row |
| Game card menu | titled rows with descriptions, scrolling; tools per engine | TBD Phase 6 | rows per engine (open question 4) |
| Delete dialog | remove from library, optional folder delete | TBD Phase 6 | export-saves option (PROPOSED) |
| In-game overlay, RPG Maker | pad (ENTER, F2, F8, ESCAPE, d-pad, Z/X/CTRL/SHIFT/Q/R), top bar, handles, rotate corner icon | TBD Phase 6 | large-controls toggle; pad opacity setting kept |
| In-game overlay, Ren'Py | same, cheats icon in place of fast-forward, pad starts hidden | TBD Phase 6 | none planned |
| Close confirmation | "Do you want to close the game?" No / Yes | TBD Phase 6 | none |
| Plugin info screens | icon, notices, licences, hide-icon, Close, Save Logs | TBD Phase 6 | becomes About and licences page; hide-icon dropped |
| Permission dialog flow | system prompts + plugin alert + all-files page | TBD Phase 6 | avoided by design; fallback is one in-app dialog |


---

## Addendum A (2026-11-12): scope decisions for PC mode ("full experience")

The owner asked for the full JoiPlay-style experience and left the scoping to us. Decisions, with reasons:

| Feature | Decision | Reason |
| --- | --- | --- |
| Add Game (point at a launch file) and Import Archive (zip, 7z, rar, tar, password prompt) | **Keep**, Phase 2 | core import flow; spec 7.2 detection runs on the result |
| Install Package (JoiPlay `.jgp`) | **Keep as our own package format first**: export and import of a Pocketverse game package (a zip of the whole game folder including `meta.json`, saves, states, cheats). Importing JoiPlay `.jgp` files is best effort and later; the container format is undocumented and not reverse engineered | same "move a game with its saves and settings" feature without depending on an undocumented format |
| Import RGA File (patch) | **Replace with "Apply patch archive"**: overlay the archive's files over the game folder after taking a backup (JoiPlay's own documentation describes patches as copy-over files). `.rga` itself is deferred because its format is unknown | behaviour first, format later |
| Game card menu | **Keep every item**: Patch, Export, Edit (name, version, icon, type), Delete (optional folder deletion), Create Shortcut, Open Folder, About, Settings, plus engine items Decrypt Files (RGSS archives), Unpack/Repack Scripts, Optimize Maps (RPG Maker), Decompile Scripts (Ren'Py), Edit Plugins (MV/MZ) | full experience; engine items arrive with their engines (Phase 5) |
| Settings groups | **Show App, Gamepad, Ren'Py, HTML (MV/MZ), RPG and P.Essentials.** Show Ruffle only if a Flash engine is installed | each group is hidden when its engine pack is not installed |
| Game Selection dropdown (global vs per game) | **Keep** | matches our global default plus per-game override (spec 9.1) |
| Backup/Restore | **Keep**, as a backup of the library folder and settings rather than a private database | our library is folder-based (SYNTHESIS section 2) |
| Remove Missing Games | **Keep** (rescan library) | our index is rebuildable |
| Compatibility List (online ratings, account) and the online hub deep link | **Dropped** | offline-first, no accounts (CLAUDE.md goal 5) |
| Cloud sync, supporter builds, ads, update prompts | **Dropped** | not reproduced by rule |
| Lock screen password, content filter | **Keep, later** (Phase 6) | cheap and low risk |
| Themes System, Wallpaper, Day, Night, Amoled plus user primary colour | **Keep** | declared feature of PC mode; values designed by us (Day and Amoled values were never captured) |

Orientation: plugin games are landscape-locked; the JoiPlay library adapts to both orientations (Task 5). Rotation chosen inside a game persisted to the library in JoiPlay; in Pocketverse the library follows its own orientation setting and a game's rotation must not leak back (deliberate deviation).

Open question 3 in section 10 is answered by this addendum. Still open: the unread submenus listed in section 10 item 2.
