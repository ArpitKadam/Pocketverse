# engine-compat

Shared compatibility data for script engines: patch-list schema, path-case index, Win32/ENV shim spec.

- **Type:** pure Kotlin/JVM library.
- **Package:** `com.pocketverse.engine.compat`. The module's public API lives in `com.pocketverse.engine.compat.api`; everything else is `internal`.
- **May depend on:** core-model, core-common. Enforced by `./gradlew checkModuleBoundaries`.
- **Status:** skeleton (P1-2). No code yet.
