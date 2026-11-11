# TASK.md — Pocketverse backlog

Mode: gated. One task at a time; next starts on "Go for Task n".
Phase: 1 (skeleton, storage, library DB). Phase 0 closed on 2026-11-12. Decision log: app licence = GPLv3 open source. No EXPLANATION.md (owner request).

| # | Task | Status |
| --- | --- | --- |
| 1 | Skeleton, `.gitignore`, `TASK.md`, `TOOLING.md`, read-only adb inventory | done, committed |
| 2 | Open-source alternatives matrix, licence compatibility matrix, `docs/licenses/DECISION.md` | done, committed |
| 3 | RE note + UI capture: John GBA Lite | done, committed |
| 4 | RE note + UI capture: MelonDS (upstream source first) | done, committed |
| 5 | RE note + UI capture: JoiPlay main | done, committed |
| 6 | RE note: JoiPlay Ren'Py plugin | done, committed |
| 7 | RE note: JoiPlay RPG Maker plugin (+ Ruffle runtime observation) | done, committed |
| 8 | `SYNTHESIS.md`, `CHEAT_FORMATS.md`, `UI_SPEC_GBA/NDS/PC.md`, module map revision, exit checklist | done, committed |
| 9 | Phase 0 follow-ups: owner answers applied (module map into CLAUDE.md, GPL-3.0-only, NDS control placement, PC scope, landscape captures) | done, awaiting review |

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
- [x] Owner has reviewed Phase 0 (answers received 2026-11-12; Phase 1 released)

## Phase 1 tasks (skeleton, storage, library DB)

| # | Task | Status |
| --- | --- | --- |
| P1-1 | `docs/architecture/STORAGE.md` decision, `meta.json` v1 schema, folder layout spec | todo |
| P1-2 | Gradle multi-module skeleton (all modules from CLAUDE.md section 6, empty with READMEs), version catalog, builds, `ARCHITECTURE.md`, SPDX header check | todo |
| P1-3 | `core-model`, `core-common`, `core-storage`: `GameFolder` API, folder creation, `meta.json` read/write, unit tests | todo |
| P1-4 | `core-database`: Room library index, rebuild from folders, tests | todo |
| P1-5 | `feature-library` + `app` wiring: empty library screen, folder layout created on import stub, instrumented test | todo |

Do not start P1-n+1 before "Go for P1-n+1".
