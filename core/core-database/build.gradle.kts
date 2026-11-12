// SPDX-License-Identifier: GPL-3.0-only

plugins {
    id("pocketverse.android-library")
    id("pocketverse.room")
}

android {
    testOptions {
        unitTests {
            // Robolectric needs the merged resources to start a fake Android environment.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // LibraryIndex.sync takes a Library; GameEntry exposes Platform.
    api(project(":core:core-model"))
    api(project(":core:core-storage"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
