// SPDX-License-Identifier: GPL-3.0-only

plugins {
    id("com.android.application")
}

android {
    namespace = "com.pocketverse.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pocketverse.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
