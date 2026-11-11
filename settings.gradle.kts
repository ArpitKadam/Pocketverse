// SPDX-License-Identifier: GPL-3.0-only

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Pocketverse"

include(":app")
include(":core:core-model")
include(":core:core-common")
include(":core:core-storage")
include(":core:core-database")
include(":feature:feature-library")
include(":feature:feature-import")
include(":feature:feature-player")
include(":feature:feature-cheats")
include(":feature:feature-settings")
include(":engine:engine-api")
include(":engine:engine-libretro")
include(":engine:engine-mkxp")
include(":engine:engine-renpy")
include(":engine:engine-rpgmv")
include(":engine:engine-compat")
include(":cheats:cheats-core")
include(":cheats:cheats-gba")
include(":cheats:cheats-nds")
include(":cheats:cheats-script")
include(":ui:ui-theme-api")
include(":ui:ui-theme-gba")
include(":ui:ui-theme-nds")
include(":ui:ui-theme-pc")
