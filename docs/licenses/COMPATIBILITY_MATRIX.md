# Licence compatibility matrix

Task 2, 2026-11-12. Question: can each candidate be linked into a GPLv3 application? Facts below are from the cloned sources' LICENSE files and file headers unless marked unverified. This is an engineering reading, not legal advice; get a legal review before a public release.

Rule applied: GPLv3 can absorb GPLv3, GPLv2-or-later, MPL-2.0, LGPL, MIT, BSD and Apache-2.0 code. It cannot absorb GPLv2-only code.

| Component | Licence | Evidence | Linkable into GPLv3 app | Notes |
| --- | --- | --- | --- | --- |
| mGBA | MPL-2.0 | `LICENSE`, file headers | yes | MPL file-level copyleft: modified mGBA files stay MPL and must be published; compatible with GPLv3 via MPL section 3.3. |
| gpSP | GPL-2.0-or-later | `cpu.cc`, `cheats.c` headers say "version 2 ... or (at your option) any later version" | yes, as GPLv3 | All files not sampled. |
| VBA-M | GPL-2.0, probably or-later | only sampled headers (`draw_text.cpp`) say "or later"; `LICENSE` is GPLv2 text | **unverified** | Check every linked file before use. |
| melonDS | GPL-3.0 | `LICENSE` | yes | |
| melonDS-android + lib | GPL-3.0 | `LICENSE`; lib repo GPL-3.0 by API | yes | Submodules oboe and faad2 (listed in `.gitmodules`) need individual licence checks; not done in this pass. |
| NooDS | GPL-3.0 | `LICENSE` | yes | |
| DeSmuME | GPL-2.0-or-later | `NDSSystem.cpp` header | yes, as GPLv3 | |
| RetroArch / libretro.h | GPL-3.0 app; `libretro.h` has its own permissive MIT-style header | `libretro.h` header (agent-read, not re-verified) | yes | Linking a core makes the combination follow the core's licence. |
| LibretroDroid | GPL-3.0 | API | yes | |
| LemuroidCores | **no licence set** | API | **no** | Cannot reuse the packaged binaries; build cores ourselves. |
| Lemuroid | GPL-3.0 | `COPYING` | yes, reference or reuse | |
| mkxp-z | GPL-2.0-or-later; default build adds OpenSSL (Apache-2.0) so binaries are effectively GPLv3 | README line 23, `src/main.cpp` header | yes, as GPLv3 | Build with OpenSSL on and ship as GPLv3, which matches our choice. |
| EasyRPG Player | GPL-3.0 | README | yes | |
| Ren'Py | MIT, with parts derived from LGPL code | `sphinx/source/license.rst` | yes | LGPL obligations: allow relinking/replacement of the LGPL parts and ship notices. Per-dependency list needed at integration time. |
| Ruffle | MIT OR Apache-2.0 | `LICENSE.md` | yes | |
| Pegasus | GPL-3.0-or-later | `LICENSE.md` | yes, reference only | |
| Android mkxp-z forks (thehatkid, BookerRues9, vrcxz, pif-android) | none set | GitHub API | **no** | No licence means all rights reserved. Do not vendor. |
| Essentia | GPL-2.0 per GitHub field | API | **unverified** | If GPL-2.0-only it cannot be merged into a GPLv3 app. Check headers before reuse. |
| cellicom/rpgmaker-to-android | MIT | API | yes | Brand new, unproven. |
| joiplay/commons, joiplay/joipad | MIT | `LICENSE` files (Task 6) | yes | JoiPlay's shared plugin-contract models and on-screen pad. Reference only; our contract is our own design. |
| joiplay/renpy (Ren'Py fork) | Ren'Py terms: MIT plus LGPL-derived parts | upstream `license.rst`; fork has no licence field | yes, with LGPL duties | Closest public reference for embedding Ren'Py on Android. |
| joiplay/mkxp (mkxp fork with JoiPlay bindings) | GPL-2.0 per GitHub; sampled headers say v2 or later | `src/main.cpp`, `src/config.cpp` headers (Task 7) | yes by headers, but 2022 snapshot, Ruby 1.8 only | Behaviour reference. Start our port from mkxp-z. |
| joiplay/android-mkxp (Android plugin shell) | **none declared** (plugin notice claims GPLv2) | no LICENSE file, no GitHub licence field (Task 7) | **no** | Do not vendor; reference only. |
| ruffle-rs/ruffle-android | `LICENSE.md` present (GitHub: NOASSERTION); Ruffle itself is MIT OR Apache-2.0 | GitHub API (Task 7), file not read in full | likely yes | Clean reference if a Flash engine is ever added. Read `LICENSE.md` before use. |
| Live2D Cubism Core (`libLive2DCubismCore.so`, shipped inside the JoiPlay Ren'Py plugin) | Live2D proprietary licence | seen as a binary in the plugin APK (Task 6) | **no** | Do not bundle without a Live2D agreement. Not needed for our baseline. |

## Hazards to watch

1. GPLv2-only code anywhere in the dependency tree breaks the GPLv3 plan. Re-run this check at every new vendoring.
2. mkxp-z with OpenSSL turned on needs the binary to be GPLv3. Our choice already satisfies this.
3. Ren'Py's LGPL parts add relinking duties even though the rest is MIT.
4. No bundled BIOS or game files. Prefer open BIOS replacements (melonDS ships `freebios/`; gpSP ships `bios/` which needs its origin checked before use).
5. Clean-room rule: closed apps (John GBA Lite, JoiPlay and plugins) are never a code or asset source.
