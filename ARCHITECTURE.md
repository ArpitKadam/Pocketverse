# Architecture

How Pocketverse is built **now**. Updated in place when structure changes. Why things are shaped this way: `docs/architecture/MODULE_MAP.md`, `docs/architecture/STORAGE.md`, `docs/research/re/SYNTHESIS.md`. Current state: Phase 1, P1-4 done (library index).

## Build

- Gradle 9.9.1 (wrapper), Android Gradle Plugin 9.5.1, Kotlin 2.4.21, JDK 17, Compose BOM 2026.09.00. Versions live in `gradle/libs.versions.toml` only.
- Convention plugins in `build-logic/` (`pocketverse.jvm-library`, `pocketverse.android-library`, `pocketverse.android-application`) hold the shared SDK levels (compileSdk 37, minSdk 26), Java 17 and the derived namespace `com.pocketverse.<group>.<name>`.
- Tests: JUnit 5 in the pure Kotlin modules; JUnit 4 with Robolectric in Android modules that need a fake Android environment (`core-database`).
- `./gradlew :app:assembleDebug` builds the app; `./gradlew check` runs the unit tests plus two repository checks (below).
- `local.properties` (SDK path) is per machine and gitignored.

## Modules

Layout is the revised tree in `CLAUDE.md` section 6: `app`, `core/*`, `feature/*`, `engine/*`, `cheats/*`, `ui/*`. Each module has its own `README.md` stating purpose, type, allowed dependencies and status.

- **Pure Kotlin/JVM** (unit-testable with no device): `core-model`, `core-common`, `core-storage`, `feature-import`, `engine-api`, `engine-compat`, `cheats-core`, `cheats-gba`, `cheats-nds`, `cheats-script`.
- **Android libraries:** `core-database`, `feature-library`, `feature-player`, `feature-cheats`, `feature-settings`, `engine-libretro`, `engine-mkxp`, `engine-renpy`, `engine-rpgmv`, `ui-theme-api`, `ui-theme-gba`, `ui-theme-nds`, `ui-theme-pc`.
- **`app`:** the composition root; depends on every module.
- Public API of a module lives in its `api` package; everything else is `internal`.

## What exists in code

- **`core-model`:** `Platform`, `GameRecord` and its parts (`EngineRef`, `ContentHash`, `RomInfo`, `ImportInfo`, `UiPrefs`, `PlayStats`, typed `SettingValue`). `GameRecord.raw` carries the JSON it was read from so unknown keys survive a rewrite.
- **`core-common`:** `Hashing` (SHA-256, CRC-32).
- **`core-storage`:** `Library` (root, scan, transactional import: `beginImport` to a hidden `.importing-*` folder, then `commit` renames it into place, or `abort`), `GameFolder` (the only place that builds paths inside a game folder; atomic `meta.json` read/write), `FolderNames` (sanitising and naming rules), `MetaException`. `MetaJson` and `AtomicFiles` are internal.
- **`core-database`:** `LibraryIndex` over one Room table. `meta.json` is the source of truth; the table only lets the library be listed, sorted and searched without opening every folder. `sync(library)` reuses `Library.scan()` and updates only rows whose `meta.json` changed; a stale schema is dropped and rebuilt (`fallbackToDestructiveMigration`), which is safe because nothing in it is not in a folder. Play statistics are written to `meta.json` first, then the row.
- Everything else is still an empty module with a README.

## Invariants (enforced by `./gradlew check`)

1. **`checkModuleBoundaries`:** features reach engines only through `:engine:engine-api` and see only `:ui:ui-theme-api`; `core` depends on nothing above it; engines, cheats and themes never depend on features; cheats use only `engine-api`.
2. **`checkSpdx`:** every `.kt`, `.kts` and `.java` file starts with `// SPDX-License-Identifier: GPL-3.0-only` (`docs/licenses/DECISION.md`).

## Storage and data

- Library layout, naming, hashing and write rules: `docs/architecture/STORAGE.md`.
- Per-game `meta.json` v1: `docs/architecture/META_SCHEMA_V1.md` and `.json`.
- The Room database (`core-database`) is an index only and is rebuilt from the game folders.

## Directory map

```text
app/  core/  feature/  engine/  cheats/  ui/      Gradle modules
build-logic/                                       convention plugins
docs/                                              research, design, licences, architecture
tools/                                             dev scripts
research/  .re-workspace/  DemoGames/              gitignored working material
```
