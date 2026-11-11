// SPDX-License-Identifier: GPL-3.0-only

plugins {
    id("com.android.library")
}

android {
    namespace = "com.pocketverse." + project.name.replaceFirst("-", ".").replace("-", "_")
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
