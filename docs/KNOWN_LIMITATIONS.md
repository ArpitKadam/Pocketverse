# Known limitations

Honest list of what does not work or cannot be verified, and why. Updated as research proceeds.

| Area | Limitation | Reason | Source |
| --- | --- | --- | --- |
| RE of installed apps | Private data dirs (`/data/data/<pkg>`) unreadable | Device is unrooted, `ro.debuggable=0` | `docs/research/re/TOOLING.md` |
| Generic Windows `.exe` | Not supported | No Wine/Box64 layer in scope (CLAUDE.md §7.2) | project spec |
| Reverse engineering tooling | No method-level decompilation of any closed app; findings stop at manifest, symbol-name, string and observed-behaviour level | JADX, apktool and Ghidra providers behind REA are not configured on the dev machine; the Windows Ghidra profile only accepts PE targets | `docs/research/re/TOOLING.md` |
| JoiPlay launch intent | RPG Maker plugin extras are inferred, not source-verified | closed plugin; names confirmed only for the Ren'Py plugin's public source | `docs/research/re/joiplay-renpy-plugin.md` |
| RPG Maker XP/VX/VX Ace on Android | no maintained, licensed Android port of mkxp-z exists; several forks carry no licence | main mkxp-z repo has no Android code; forks unlicensed; JoiPlay's plugin shell has no declared licence | `docs/research/OPEN_SOURCE_ALTERNATIVES.md`, `docs/research/re/joiplay-rpgm-plugin.md` |
| Ren'Py shared runtime | no documented way to run arbitrary games from one shared runtime in a generic launcher | official RAPT builds one app per game; JoiPlay's approach is partly proprietary | `docs/research/re/joiplay-renpy-plugin.md` |
| Live2D in Ren'Py | not supported in our baseline | Live2D Cubism Core is proprietary and needs a licence agreement | `docs/licenses/COMPATIBILITY_MATRIX.md` |
| Migration of John GBA Lite states | may be importable only as battery saves | `.jg0` is a VBA-style v10 state; mGBA acceptance is unverified | `docs/research/re/SYNTHESIS.md` section 3 |
| Migration of MelonDS states | cross-version states may be rejected | state container has a major version check; owner's state is major 13, upstream core is 14 | `docs/research/re/melonds.md` section 4.2 |
| UI reference capture | landscape not captured for John GBA Lite and MelonDS; DS screen arrangement modes taken from source, not observed; no screen recordings (animations unknown) | no change to the phone's rotation setting was made without approval; only static screenshots were taken | `docs/design/UI_SPEC_*.md` |
| Pokémon Essentials save location under JoiPlay's plugin | unconfirmed | no in-game save was made during the test run | `docs/research/re/joiplay-rpgm-plugin.md` |
| GameShark DS / Code Breaker DS | not supportable from what we have | undocumented in every source read; only Action Replay DS has an implementation in melonDS, DeSmuME and NooDS | `docs/research/CHEAT_FORMATS.md` sections 3, 8 |
| GBA cheat semantics | emulators disagree on several behaviours (IO write width, button masks, group write count, GameShark E-range, Code Breaker E add width) | mGBA, VBA-M and gpSP differ; hardware truth not verified; no encrypted-code test vectors exist upstream | `docs/research/CHEAT_FORMATS.md` section 8 |
| melonDS nested conditionals | unclear handling of nested IF inside a false IF | code reading only; looks unbalanced compared with DeSmuME | `docs/research/CHEAT_FORMATS.md` section 8 |
| melonDS-Android cheat XML | `<codes>` whitespace layout and the newline handling before the native call not found | not located in source | `docs/research/CHEAT_FORMATS.md` section 8 |
| Archive import | RAR is not supported | no suitable permissively licensed reader was adopted; the archive is refused with a clear message | `feature/feature-import` (P2-3) |
| Archive import | Password-protected 7z is implemented but untested | no way to create an encrypted 7z in the test environment; encrypted zip is tested | `feature/feature-import` (P2-3) |
| Folder import | Save files that sit inside a source folder (for example `UserData/`, `saves/`) are copied into `game/` as part of the tree | the importer does not yet split saves out; they are excluded from the game's identity hash, and moving them into `save/` is a later step | `docs/architecture/STORAGE.md` |
