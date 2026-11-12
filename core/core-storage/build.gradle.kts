// SPDX-License-Identifier: GPL-3.0-only

plugins {
    id("pocketverse.jvm-library")
}

dependencies {
    // GameRecord is part of this module's public API (readMeta, writeMeta, beginImport).
    api(project(":core:core-model"))
}
