# engine-libretro

Host for libretro cores (GBA via mGBA, NDS via melonDS).

- **Type:** Android library.
- **Package:** `com.pocketverse.engine.libretro`. The module's public API lives in `com.pocketverse.engine.libretro.api`; everything else is `internal`.
- **May depend on:** engine-api, core-*. Enforced by `./gradlew checkModuleBoundaries`.
- **Status:** skeleton (P1-2). No code yet.
