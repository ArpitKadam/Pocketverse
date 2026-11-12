// SPDX-License-Identifier: GPL-3.0-only

plugins {
    id("pocketverse.android-library")
    id("pocketverse.compose")
}

android {
    testOptions {
        unitTests {
            // Robolectric (also used to run Compose UI tests on the JVM) needs the merged resources.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // The ViewModel takes a LibraryIndex and a Library, and the screen shows GameEntry rows.
    api(project(":core:core-database"))
    api(project(":core:core-model"))
    api(project(":core:core-storage"))
    api(libs.androidx.lifecycle.viewmodel)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
