// SPDX-License-Identifier: GPL-3.0-only

plugins {
    id("pocketverse.android-application")
    id("pocketverse.compose")
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(project(":core:core-common"))
    implementation(project(":core:core-storage"))
    implementation(project(":core:core-database"))
    implementation(project(":feature:feature-library"))
    implementation(project(":feature:feature-import"))
    implementation(project(":feature:feature-player"))
    implementation(project(":feature:feature-cheats"))
    implementation(project(":feature:feature-settings"))
    implementation(project(":engine:engine-api"))
    implementation(project(":engine:engine-libretro"))
    implementation(project(":engine:engine-mkxp"))
    implementation(project(":engine:engine-renpy"))
    implementation(project(":engine:engine-rpgmv"))
    implementation(project(":engine:engine-compat"))
    implementation(project(":cheats:cheats-core"))
    implementation(project(":cheats:cheats-gba"))
    implementation(project(":cheats:cheats-nds"))
    implementation(project(":cheats:cheats-script"))
    implementation(project(":ui:ui-theme-api"))
    implementation(project(":ui:ui-theme-gba"))
    implementation(project(":ui:ui-theme-nds"))
    implementation(project(":ui:ui-theme-pc"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
