# core-storage

`GameFolder` API, library location resolution (real path vs SAF import/export), per-game folder manager. Pure JVM so it is unit-testable without a device. See `docs/architecture/STORAGE.md`.

- **Type:** pure Kotlin/JVM library.
- **Package:** `com.pocketverse.core.storage`. The module's public API lives in `com.pocketverse.core.storage.api`; everything else is `internal`.
- **May depend on:** core-model, core-common. Enforced by `./gradlew checkModuleBoundaries`.
- **Status:** Done (P1-3): `Library` (root, scan, import with `beginImport` / `commit` / `abort`, cleanup of abandoned imports), `GameFolder` (typed folder accessors, atomic `meta.json` read/write), `FolderNames`. `MetaJson` and `AtomicFiles` are internal. Hash-manifest identity for folder games and SAF import arrive in Phase 2.
