# build-logic

Convention plugins shared by every module, so a module's own `build.gradle.kts` stays a few lines.

| Plugin id | Use |
| --- | --- |
| `pocketverse.jvm-library` | pure Kotlin/JVM module (unit-testable without a device) |
| `pocketverse.android-library` | Android library module |
| `pocketverse.android-application` | the `:app` shell |

Shared settings live here: compile and min SDK, Java 17, the derived `namespace` (`com.pocketverse.<group>.<name>`, from the project name).
