# engine-api

`EngineRuntime`, `EngineLaunchRequest`, `EngineResult`, `EngineCapabilities`: the only way features talk to engines.

- **Type:** pure Kotlin/JVM library.
- **Package:** `com.pocketverse.engine.api`. The module's public API lives in `com.pocketverse.engine.api.api`; everything else is `internal`.
- **May depend on:** core-model, core-common. Enforced by `./gradlew checkModuleBoundaries`.
- **Status:** skeleton (P1-2). No code yet.
