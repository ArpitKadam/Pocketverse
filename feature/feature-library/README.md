# feature-library

Library screen and game list state.

- **Type:** Android library.
- **Package:** `com.pocketverse.feature.library`. The module's public API lives in `com.pocketverse.feature.library.api`; everything else is `internal`.
- **May depend on:** core-*, engine-api, ui-theme-api. Enforced by `./gradlew checkModuleBoundaries`.
- **Status:** first version (P1-5): `LibraryScreen` (loading, error, empty state, game list, unreadable-folder note), `LibraryViewModel` (syncs the index with the folders, exposes `LibraryUiState`, optional debug "add test game" hook), strings in `res/values/strings.xml`. Stateless screen; mode-specific looks come later through `ui-theme-api`. Tested with Compose UI tests and view-model tests on the JVM via Robolectric.
