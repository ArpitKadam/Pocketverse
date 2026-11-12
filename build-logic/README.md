# build-logic

Convention plugins shared by every module, so a module's own `build.gradle.kts` stays a few lines.

| Plugin id | Use |
| --- | --- |
| `pocketverse.jvm-library` | pure Kotlin/JVM module (unit-testable without a device) |
| `pocketverse.android-library` | Android library module |
| `pocketverse.android-application` | the `:app` shell |
| `pocketverse.room` | Room with the KSP compiler (on top of `pocketverse.android-library`) |
| `pocketverse.compose` | add Jetpack Compose to an Android module (applied on top of the two Android plugins) |

Shared settings live here: compile and min SDK, Java 17, the derived `namespace` (`com.pocketverse.<group>.<name>`, from the project name).
