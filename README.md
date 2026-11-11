# Pocketverse

One Android app for `.gba`, `.nds` and `.exe`-style (RPG Maker / Ren'Py) games: one library, one import flow, one save/state/cheat system, three switchable UI modes.

Status: Phase 1 (skeleton, storage, library index). Phase 0 research is complete (see `docs/`).

Licence: GPL-3.0-only (see `LICENSE` and `docs/licenses/DECISION.md`).

Build: `./gradlew :app:assembleDebug`, checks: `./gradlew check`. Needs JDK 17 and an Android SDK (put its path in `local.properties` as `sdk.dir=`). Architecture: `ARCHITECTURE.md`.

Layout today:
- `CLAUDE.md` — project spec and phase plan
- `ARCHITECTURE.md` — how the code is built now
- `app/`, `core/`, `feature/`, `engine/`, `cheats/`, `ui/` — Gradle modules, each with a `README.md`
- `TASK.md` — active backlog
- `docs/research/` — open-source comparison, RE notes (`re/`), cheat formats
- `docs/design/`, `docs/licenses/`, `docs/architecture/`, `docs/cheats/` — filled in later phases
- `tools/` — dev scripts, each documented
- `research/`, `.re-workspace/`, `DemoGames/` — gitignored working material
