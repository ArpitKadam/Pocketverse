# TASK.md — Pocketverse backlog

Mode: gated. One task at a time; next starts on "Go for Task n".
Phase: 0 (research and RE). Decision log: app licence = GPLv3 open source. No EXPLANATION.md (owner request).

| # | Task | Status |
| --- | --- | --- |
| 1 | Skeleton, `.gitignore`, `TASK.md`, `TOOLING.md`, read-only adb inventory | done, committed |
| 2 | Open-source alternatives matrix, licence compatibility matrix, `docs/licenses/DECISION.md` | done, committed |
| 3 | RE note + UI capture: John GBA Lite | done, committed |
| 4 | RE note + UI capture: MelonDS (upstream source first) | done, committed |
| 5 | RE note + UI capture: JoiPlay main | done, committed |
| 6 | RE note: JoiPlay Ren'Py plugin | done, committed |
| 7 | RE note: JoiPlay RPG Maker plugin (+ Ruffle runtime observation) | done, committed |
| 8 | `SYNTHESIS.md`, `CHEAT_FORMATS.md`, `UI_SPEC_GBA/NDS/PC.md`, module map revision, exit checklist | done, awaiting review |

## Task 1 acceptance

- [x] Folder skeleton under `docs/`, `research/`, `.re-workspace/`, `tools/`
- [x] `.gitignore` covers `DemoGames`, `research/`, `.re-workspace/`
- [x] Phone authorized, five packages found, versions recorded
- [x] `docs/research/re/TOOLING.md`
- [x] `docs/KNOWN_LIMITATIONS.md` seeded

Do not start Phase 1 until the Phase 0 exit checklist (CLAUDE.md §5.3) is reviewed.

## Phase 0 exit checklist (CLAUDE.md section 5.3)

- [x] Open-source comparison matrix and recommendations: `docs/research/OPEN_SOURCE_ALTERNATIVES.md`
- [x] Licence compatibility matrix and app licence: `docs/licenses/COMPATIBILITY_MATRIX.md`, `DECISION.md` (GPLv3; "only" vs "or later" still open)
- [x] Five RE notes plus synthesis: `docs/research/re/` (John GBA Lite, MelonDS, JoiPlay, Ren'Py plugin, RPG Maker plugin, `SYNTHESIS.md`)
- [x] Three UI specs written: `docs/design/UI_SPEC_GBA.md`, `UI_SPEC_NDS.md`, `UI_SPEC_PC.md` (owner review pending; `PARITY.md` is a Phase 6 deliverable)
- [x] Cheat-format research: `docs/research/CHEAT_FORMATS.md`
- [x] Module map revised: `docs/architecture/MODULE_MAP.md`
- [ ] Owner has reviewed Phase 0 (blocks Phase 1)
