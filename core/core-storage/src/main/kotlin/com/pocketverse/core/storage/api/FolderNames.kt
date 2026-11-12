// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.core.storage.api

/** Folder naming rules from `docs/architecture/STORAGE.md` section 5. Keeps names copyable to a PC. */
object FolderNames {
    const val MAX_TITLE_CODE_POINTS = 80

    private const val ILLEGAL = "/\\:*?\"<>|"
    private val RESERVED = setOf("CON", "PRN", "AUX", "NUL") +
        (1..9).map { "COM$it" } + (1..9).map { "LPT$it" }

    /** A title made safe for a folder name. Never empty. */
    fun sanitize(title: String): String {
        val replaced = buildString {
            title.forEach { c -> append(if (c in ILLEGAL || c.code < 32 || c.code == 127) ' ' else c) }
        }
        var t = replaced.replace(Regex("\\s+"), " ").trim(' ', '.')
        if (t.codePointCount(0, t.length) > MAX_TITLE_CODE_POINTS) {
            t = t.substring(0, t.offsetByCodePoints(0, MAX_TITLE_CODE_POINTS)).trimEnd(' ', '.')
        }
        if (t.isEmpty()) t = "Untitled"
        if (t.substringBefore('.').uppercase() in RESERVED) t += "_"
        return t
    }

    /** `<sanitised title> [<short id>]`. */
    fun folderName(title: String, shortId: String): String = "${sanitize(title)} [$shortId]"
}
