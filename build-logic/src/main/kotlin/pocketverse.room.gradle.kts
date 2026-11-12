// SPDX-License-Identifier: GPL-3.0-only

// Room with the KSP compiler. Apply on top of pocketverse.android-library.
plugins {
    id("com.google.devtools.ksp")
}

val libs = versionCatalogs.named("libs")

dependencies {
    "implementation"(libs.findLibrary("androidx-room-runtime").get())
    "ksp"(libs.findLibrary("androidx-room-compiler").get())
}
