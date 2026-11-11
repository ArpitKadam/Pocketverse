# core-model

Domain types shared by every module: Game, Platform, EngineType, GameRecord (the `meta.json` v1 schema), CheatEntry, SaveSlot.

- **Type:** pure Kotlin/JVM library.
- **Package:** `com.pocketverse.core.model`. The module's public API lives in `com.pocketverse.core.model.api`; everything else is `internal`.
- **May depend on:** nothing (leaf). Enforced by `./gradlew checkModuleBoundaries`.
- **Status:** skeleton (P1-2). No code yet.
