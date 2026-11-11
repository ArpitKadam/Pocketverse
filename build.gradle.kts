// SPDX-License-Identifier: GPL-3.0-only

plugins {
    base
}

// --- check 1: every source file carries the licence header (docs/licenses/DECISION.md)
val spdxLine = "// SPDX-License-Identifier: GPL-3.0-only"
val checkSpdx = tasks.register("checkSpdx") {
    group = "verification"
    description = "Fails if a .kt, .kts or .java source file does not start with the GPL-3.0-only SPDX header."
    val sources = fileTree(rootDir) {
        include("**/*.kt", "**/*.kts", "**/*.java")
        exclude("**/build/**", "**/.gradle/**", "research/**", ".re-workspace/**", "DemoGames/**")
    }
    inputs.files(sources)
    doLast {
        val missing = sources.files.filter { f ->
            f.useLines { it.firstOrNull() }?.trim() != spdxLine
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing '$spdxLine' as first line in:\n" +
                    missing.joinToString("\n") { " - " + it.relativeTo(rootDir).path }
            )
        }
    }
}

// --- check 2: module boundaries (CLAUDE.md section 6, docs/architecture/MODULE_MAP.md)
//   features talk to engines only through :engine:engine-api and see only :ui:ui-theme-api;
//   core depends on nothing above it; engines, cheats and themes never depend on features.
val checkModuleBoundaries = tasks.register("checkModuleBoundaries") {
    group = "verification"
    description = "Fails if a module depends on a module its layer is not allowed to use."
    doLast {
        val forbidden: List<Triple<String, (String) -> Boolean, String>> = listOf(
            Triple(":feature:", { d -> d.startsWith(":engine:") && d != ":engine:engine-api" }, "features must use engines only through :engine:engine-api"),
            Triple(":feature:", { d -> d.startsWith(":ui:") && d != ":ui:ui-theme-api" }, "features must see only :ui:ui-theme-api, not a concrete theme"),
            Triple(":core:", { d -> d == ":app" || listOf(":feature:", ":engine:", ":cheats:", ":ui:").any(d::startsWith) }, "core must not depend on higher layers"),
            Triple(":engine:", { d -> d == ":app" || d.startsWith(":feature:") || d.startsWith(":ui:") }, "engines must not depend on features or themes"),
            Triple(":cheats:", { d -> d == ":app" || d.startsWith(":feature:") || d.startsWith(":ui:") || (d.startsWith(":engine:") && d != ":engine:engine-api") }, "cheats may use only :engine:engine-api"),
            Triple(":ui:", { d -> d == ":app" || d.startsWith(":feature:") || d.startsWith(":engine:") || d.startsWith(":cheats:") }, "themes must not depend on features, engines or cheats"),
        )
        val violations = mutableListOf<String>()
        for (p in subprojects) {
            for (cfg in p.configurations) {
                for (dep in cfg.dependencies.withType<ProjectDependency>()) {
                    for ((from, bad, why) in forbidden) {
                        if (p.path.startsWith(from) && bad(dep.path)) {
                            violations += "${p.path} -> ${dep.path} (${cfg.name}): $why"
                        }
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException("Module boundary violations:\n" + violations.distinct().joinToString("\n") { " - $it" })
        }
    }
}

tasks.named("check") {
    dependsOn(checkSpdx, checkModuleBoundaries)
}
