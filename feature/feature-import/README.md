# feature-import

Pure content-based format detection (spec 7.2) and the import pipeline, with explicit rejection reasons.

- **Type:** pure Kotlin/JVM library.
- **Package:** `com.pocketverse.feature.importing`. The module's public API lives in `com.pocketverse.feature.importing.api`; everything else is `internal`.
- **May depend on:** core-model, core-common, core-storage. Enforced by `./gradlew checkModuleBoundaries`.
- **Status:** P2-1 done: `GameSource` (file, folder, later archive and Storage Access Framework tree) and `FormatDetector`, which returns `Detection.Cartridge`, `FolderGame`, `Ambiguous` or `Rejected` with a reason code. Detection is by content: GBA header (fixed byte, ARM branch, game code, complement checksum as a warning), NDS header (game code, unit code, ARM offsets, header CRC-16 as a warning), RPG Maker XP/VX/VX Ace (`Game.ini` plus engine data or an encrypted archive), RPG Maker MV/MZ (engine JS core plus `index.html`), Ren'Py (scripts under `game/`). A single wrapper folder is stripped. The package is `importing` because `import` is a Kotlin keyword. The import pipeline itself is P2-2 and P2-3.
