// SPDX-License-Identifier: GPL-3.0-only

plugins {
    id("pocketverse.jvm-library")
}

dependencies {
    // JsonObject is part of the public GameRecord (it carries keys this version does not understand).
    api(libs.kotlinx.serialization.json)
}
