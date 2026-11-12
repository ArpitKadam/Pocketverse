// SPDX-License-Identifier: GPL-3.0-only

plugins {
    id("pocketverse.jvm-library")
}

dependencies {
    // Detection results carry core-model types (RomInfo, EngineRef, Platform).
    api(project(":core:core-model"))
    implementation(project(":core:core-common"))
    api(project(":core:core-storage"))

    // Archives: zip (with passwords) via zip4j; 7z, tar and gzip via Commons Compress (7z needs xz).
    implementation(libs.zip4j)
    implementation(libs.commons.compress)
    implementation(libs.xz)
}
