// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing.api

import java.io.File
import java.io.InputStream

/** A file inside a [GameSource]; [path] is relative to the source root, `/` separated, never starts with `/`. */
data class SourceEntry(val path: String, val size: Long)

/**
 * Something the user wants to import: one file, a folder, or (later) an archive or a Storage Access Framework
 * tree. The detector and the import pipeline only see this interface, so they are testable with no device.
 */
interface GameSource {
    /** The name the user knows it by (file or folder name). */
    val displayName: String

    /** Every regular file, relative paths. A single-file source has exactly one entry. */
    fun entries(): List<SourceEntry>

    /** Opens the file at [path] (one of the [entries]). The caller closes the stream. */
    fun open(path: String): InputStream
}

/** A file or a folder on the local filesystem. */
class FileSystemSource(private val root: File) : GameSource {
    override val displayName: String get() = root.name

    override fun entries(): List<SourceEntry> =
        if (root.isFile) {
            listOf(SourceEntry(root.name, root.length()))
        } else {
            root.walkTopDown()
                .filter { it.isFile }
                .map { SourceEntry(it.relativeTo(root).invariantSeparatorsPath, it.length()) }
                .toList()
        }

    override fun open(path: String): InputStream = if (root.isFile) root.inputStream() else File(root, path).inputStream()
}
