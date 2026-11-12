# feature-import

Pure content-based format detection (spec 7.2) and the import pipeline, with explicit rejection reasons.

- **Type:** pure Kotlin/JVM library.
- **Package:** `com.pocketverse.feature.importing`. The module's public API lives in `com.pocketverse.feature.importing.api`; everything else is `internal`.
- **May depend on:** core-model, core-common, core-storage. Enforced by `./gradlew checkModuleBoundaries`.
- **Status:** P2-1 done: `GameSource` (file, folder, later archive and Storage Access Framework tree) and `FormatDetector`, which returns `Detection.Cartridge`, `FolderGame`, `Ambiguous` or `Rejected` with a reason code. Detection is by content: GBA header (fixed byte, ARM branch, game code, complement checksum as a warning), NDS header (game code, unit code, ARM offsets, header CRC-16 as a warning), RPG Maker XP/VX/VX Ace (`Game.ini` plus engine data or an encrypted archive), RPG Maker MV/MZ (engine JS core plus `index.html`), Ren'Py (scripts under `game/`). A single wrapper folder is stripped. The package is `importing` because `import` is a Kotlin keyword. The import pipeline itself is P2-2 and P2-3.

P2-2 done: `CartridgeImporter` (copy once while hashing into a hidden staging folder, free-space check, progress, cancellation, duplicate detection, then one rename publishes the game folder), `ImportResult` / `ImportError` (no exceptions for expected problems), and `ZipSource` (a `.zip` as a `GameSource`; unsafe entry names are dropped; password-protected archives are reported as `PasswordRequired`). Folder and archive games are P2-3.

P2-3 done: `GameImporter` (the one entry point: ROM, game folder or archive), `FolderGameImporter` (identity from `ManifestHash` before any byte is copied, copy or move into place), `ArchiveExtractor` (zip with passwords via zip4j, 7z / tar / tar.gz via Commons Compress, recognised by content; RAR is refused), `ImportLimits` (file count, per-file and total size, enforced against what the archive declares and at write time), `ArchivePasswords` (the hook the UI will use). Archives are unpacked into a hidden staging folder and the game folder is moved into place, so nothing is copied twice. Wrapper folders are stripped.
