# core-model

Domain types shared by every module: Game, Platform, EngineType, GameRecord (the `meta.json` v1 schema), CheatEntry, SaveSlot.

- **Type:** pure Kotlin/JVM library.
- **Package:** `com.pocketverse.core.model`. The module's public API lives in `com.pocketverse.core.model.api`; everything else is `internal`.
- **May depend on:** nothing (leaf). Enforced by `./gradlew checkModuleBoundaries`.
- **Status:** Types done (P1-3): `Platform`, `GameRecord` and its parts (the `meta.json` v1 model). Public API in `com.pocketverse.core.model.api`.
