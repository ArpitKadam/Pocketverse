// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.library.importing

import android.content.Context
import com.pocketverse.core.storage.api.Library
import com.pocketverse.feature.importing.api.ArchivePasswords
import com.pocketverse.feature.importing.api.GameImporter
import com.pocketverse.feature.importing.api.ImportError
import com.pocketverse.feature.importing.api.ImportPhase
import com.pocketverse.feature.importing.api.ImportProgress
import com.pocketverse.feature.importing.api.ImportResult
import java.io.File
import java.io.IOException

/**
 * Imports what the Storage Access Framework picked. An archive is first copied into the library's hidden
 * staging folder (the zip and 7z readers need a real file), a ROM or a folder is read in place through its
 * content URIs. Nothing here is covered by JVM unit tests: it needs a real document provider, so it is
 * checked on a device.
 */
class AndroidImportRunner(
    context: Context,
    private val library: Library,
    appVersion: String,
) : ImportRunner {
    private val resolver = context.applicationContext.contentResolver
    private val importer = GameImporter(library, appVersion)

    override fun run(
        request: ImportRequest,
        passwords: ArchivePasswords,
        preferredRom: String?,
        shouldCancel: () -> Boolean,
        onProgress: (ImportProgress) -> Unit,
    ): ImportResult = try {
        when (request) {
            is ImportRequest.Folder -> importer.importSource(TreeSource(DocumentsTree(resolver, request.uri)), preferredRom, shouldCancel, onProgress)
            is ImportRequest.File -> runFile(request, passwords, preferredRom, shouldCancel, onProgress)
        }
    } catch (e: SourceLimitException) {
        ImportResult.Failed(ImportError.LimitExceeded(e.message ?: "too large"))
    } catch (e: SecurityException) {
        ImportResult.Failed(ImportError.SourceUnreadable("permission to read the selection was lost"))
    } catch (e: IOException) {
        ImportResult.Failed(ImportError.SourceUnreadable(e.message ?: "cannot read the selection"))
    }

    /** First bytes of the stream, enough to tell an archive from a ROM. (`readNBytes` needs API 33; minSdk is 26.) */
    private fun readHeader(input: java.io.InputStream): ByteArray {
        val buffer = ByteArray(512)
        var filled = 0
        while (filled < buffer.size) {
            val n = input.read(buffer, filled, buffer.size - filled)
            if (n < 0) break
            filled += n
        }
        return buffer.copyOf(filled)
    }

    private fun runFile(
        request: ImportRequest.File,
        passwords: ArchivePasswords,
        preferredRom: String?,
        shouldCancel: () -> Boolean,
        onProgress: (ImportProgress) -> Unit,
    ): ImportResult {
        val picked = describe(resolver, request.uri)
        val header = (resolver.openInputStream(request.uri) ?: throw IOException("cannot open ${picked.name}")).use { readHeader(it) }

        // A ROM of known size is read straight from its provider. Archives (random access needed) and files of
        // unknown size are copied into staging first.
        if (!importer.isArchive(header) && picked.size >= 0) {
            return importer.importSource(UriFileSource(resolver, request.uri, picked.name, picked.size), preferredRom, shouldCancel, onProgress)
        }
        val staging = library.createStagingDir()
        try {
            val staged = File(staging, safeName(picked.name))
            if (!copyToStaging(request, staged, picked.size, shouldCancel, onProgress)) return ImportResult.Cancelled
            return importer.import(staged, passwords, preferredRom, shouldCancel, onProgress)
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Copies the picked file into [target]; false when cancelled. */
    private fun copyToStaging(request: ImportRequest.File, target: File, size: Long, shouldCancel: () -> Boolean, onProgress: (ImportProgress) -> Unit): Boolean {
        val free = library.root.usableSpace
        if (size > 0 && free < size) throw IOException("not enough free space to read the file (needs $size bytes, $free available)")
        var done = 0L
        var lastReported = 0L
        val buf = ByteArray(64 * 1024)
        (resolver.openInputStream(request.uri) ?: throw IOException("cannot open the file")).use { input ->
            target.outputStream().use { out ->
                while (true) {
                    if (shouldCancel()) return false
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (done - lastReported >= 1024 * 1024) {
                        lastReported = done
                        onProgress(ImportProgress(ImportPhase.READING_SOURCE, done, maxOf(size, 0)))
                    }
                }
            }
        }
        return true
    }

    private fun safeName(name: String): String =
        name.map { c -> if (c in "/\\:*?\"<>|" || c.code < 32) '_' else c }.joinToString("").trim().trim('.').take(200).ifBlank { "picked" }
}
