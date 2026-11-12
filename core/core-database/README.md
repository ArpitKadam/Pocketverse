# core-database

Room library index and play stats. A cache only: rebuildable from the `meta.json` files in the library.

- **Type:** Android library.
- **Package:** `com.pocketverse.core.database`. The module's public API lives in `com.pocketverse.core.database.api`; everything else is `internal`.
- **May depend on:** core-model, core-common, core-storage. Enforced by `./gradlew checkModuleBoundaries`.
- **Status:** done (P1-4): `LibraryIndex` (`sync`, `games`, `search`, `recordPlay`) over a Room table `games` with one row per game folder. The table is a cache: `sync` reads only new or changed `meta.json` files (by modified time and size) and drops rows whose folder is gone; a Room version bump drops and rebuilds the table, so there are no migrations. Tests use Robolectric (JUnit 4); everything else in the project uses JUnit 5.
