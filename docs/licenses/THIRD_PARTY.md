# Third-party components

No emulator core, engine or game component is vendored yet. Entries are added when a dependency enters the build; transitive AndroidX and Kotlin libraries follow the licences of the entries below (all Apache-2.0).

| Name | Version / commit | Licence (SPDX) | Source URL | Linking | Added in |
| --- | --- | --- | --- | --- | --- |
| Gradle (wrapper, build tool) | 9.9.1 | Apache-2.0 | https://gradle.org | build tool, not shipped | P1-2 |
| Android Gradle Plugin | 9.5.1 | Apache-2.0 | https://developer.android.com/build | build tool, not shipped | P1-2 |
| Kotlin (stdlib, Gradle plugin, Compose compiler plugin) | 2.4.21 | Apache-2.0 | https://github.com/JetBrains/kotlin | stdlib linked statically into the app | P1-2 |
| AndroidX Compose (BOM, UI, Material 3) | BOM 2026.09.00 | Apache-2.0 | https://developer.android.com/jetpack/androidx | linked into the app | P1-2 |
| AndroidX Activity Compose | 1.13.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx | linked into the app | P1-2 |

Candidate components under evaluation are in `docs/research/OPEN_SOURCE_ALTERNATIVES.md` and `COMPATIBILITY_MATRIX.md`.
