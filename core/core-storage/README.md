# core-storage

`GameFolder` API, library location resolution (real path vs SAF import/export), per-game folder manager. Pure JVM so it is unit-testable without a device. See `docs/architecture/STORAGE.md`.

- **Type:** pure Kotlin/JVM library.
- **Package:** `com.pocketverse.core.storage`. The module's public API lives in `com.pocketverse.core.storage.api`; everything else is `internal`.
- **May depend on:** core-model, core-common. Enforced by `./gradlew checkModuleBoundaries`.
- **Status:** skeleton (P1-2). No code yet.
