# core-common

Result types, logging facade, version-tagged JSON helpers.

- **Type:** pure Kotlin/JVM library.
- **Package:** `com.pocketverse.core.common`. The module's public API lives in `com.pocketverse.core.common.api`; everything else is `internal`.
- **May depend on:** nothing (leaf). Enforced by `./gradlew checkModuleBoundaries`.
- **Status:** Hashing done (P1-3): `Hashing.sha256Hex`, `Hashing.romHashes` (SHA-256 plus CRC-32 in one pass). Result types and a logging facade are added when a module needs them.
