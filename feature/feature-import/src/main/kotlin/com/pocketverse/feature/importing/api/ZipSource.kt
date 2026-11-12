// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing.api

import com.pocketverse.core.model.api.SourceKind
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** The archive needs a password; the standard library cannot read it. */
class EncryptedArchiveException(message: String) : IOException(message)

/**
 * A `.zip` file seen as a [GameSource]. Entries with unsafe names (absolute, drive letters, `..`) are dropped
 * so nothing built on [entries] can write outside its target folder. Close it when done.
 */
class ZipSource(private val file: File) : GameSource, Closeable {
    private val zip = ZipFile(file)

    override val displayName: String get() = file.name

    override val kind: SourceKind get() = SourceKind.ARCHIVE

    override fun entries(): List<SourceEntry> = zip.entries().asSequence()
        .filter { !it.isDirectory }
        .mapNotNull { e -> normalise(e.name)?.let { SourceEntry(it, e.size.coerceAtLeast(0)) } }
        .toList()

    override fun open(path: String): InputStream {
        val entry = zip.entries().asSequence().firstOrNull { !it.isDirectory && normalise(it.name) == path }
            ?: throw IOException("'$path' is not in ${file.name}")
        return try {
            zip.getInputStream(entry)
        } catch (e: ZipException) {
            if (e.message?.contains("encrypted", ignoreCase = true) == true) {
                throw EncryptedArchiveException("'${file.name}' is password protected")
            }
            throw e
        }
    }

    override fun close() = zip.close()

    companion object {
        /** The entry name with `/` separators and no leading `./`, or null when it could escape its folder. */
        internal fun normalise(name: String): String? {
            val n = name.replace('\\', '/').removePrefix("./")
            if (n.isEmpty() || n.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(n)) return null
            if (n.split('/').any { it == ".." }) return null
            return n
        }
    }
}
