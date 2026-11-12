// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing.api

import com.pocketverse.core.common.api.Hashing

/**
 * Identity of a folder-style game (`docs/architecture/STORAGE.md` section 6): SHA-256 of one line per file,
 * `<lowercase relative path>\0<size>\n`, sorted by path. No file contents are read, so it is cheap and the same
 * for any copy of the tree. Files an engine writes while playing are left out so playing never changes the id.
 */
object ManifestHash {
    private val excludedNames = setOf("logs.txt", ".online_path_cache", ".nosearch", ".nomedia")
    private val excludedFolders = listOf("userdata/", "saves/", "cache/")

    /** [entries] must be relative to the game root. */
    fun of(entries: List<SourceEntry>): String {
        val lines = entries
            .map { it.path.lowercase() to it.size }
            .filterNot { (path, _) -> path in excludedNames || excludedFolders.any { path.startsWith(it) } }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .joinToString("") { (path, size) -> "$path\u0000$size\n" }
        return Hashing.sha256Hex(lines.toByteArray(Charsets.UTF_8))
    }
}
