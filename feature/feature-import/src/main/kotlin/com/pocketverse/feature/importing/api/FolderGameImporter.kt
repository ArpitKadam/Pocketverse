// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing.api

import com.pocketverse.core.model.api.ContentHash
import com.pocketverse.core.model.api.GameRecord
import com.pocketverse.core.model.api.HashMethod
import com.pocketverse.core.model.api.ImportInfo
import com.pocketverse.core.model.api.LaunchSpec
import com.pocketverse.core.storage.api.GameAlreadyInLibrary
import com.pocketverse.core.storage.api.Library
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Imports one folder-style game (RPG Maker, RPG Maker MV/MZ, Ren'Py) into the [library]. The identity comes from
 * the file list alone ([ManifestHash]), so a duplicate is found before a single byte is copied; the tree is then
 * copied into a hidden folder and published with one rename (spec section 7.3).
 */
class FolderGameImporter(
    private val library: Library,
    private val appVersion: String,
    private val clock: () -> Instant = Instant::now,
    private val availableBytes: () -> Long = { library.root.usableSpace },
    private val limits: ImportLimits = ImportLimits(),
) {
    /**
     * @param adoptFrom when the files already sit in a folder this importer owns on the same filesystem (an
     * unpacked archive), that folder is moved into place instead of copied. It must be the folder that contains
     * [Detection.FolderGame.rootPrefix]; whatever it holds outside the game root is discarded with it.
     */
    fun import(
        source: GameSource,
        detection: Detection.FolderGame,
        adoptFrom: File? = null,
        shouldCancel: () -> Boolean = { false },
        onProgress: (ImportProgress) -> Unit = {},
    ): ImportResult {
        onProgress(ImportProgress(ImportPhase.CHECKING_SPACE, 0, 0))
        val prefix = detection.rootPrefix
        val files = source.entries()
            .filter { it.path.startsWith(prefix) }
            .map { SourceEntry(it.path.substring(prefix.length), it.size) }
            .filterNot { isJunk(it.path) }
        if (files.any { ZipSource.normalise(it.path) != it.path }) {
            return ImportResult.Failed(ImportError.SourceUnreadable("the source contains file names that could escape the game folder"))
        }
        // Cheapest checks first, and nothing is built that grows with an attacker-chosen number of entries
        // before the count is known to be within the limit.
        if (files.size > limits.maxEntries) return ImportResult.Failed(ImportError.LimitExceeded("more than ${limits.maxEntries} files"))
        folderLimitError(files)?.let { return ImportResult.Failed(it) }
        val total = files.sumOf { it.size }
        when {
            files.any { it.size > limits.maxEntryBytes } -> return ImportResult.Failed(ImportError.LimitExceeded("a file is larger than ${limits.maxEntryBytes} bytes"))
            total > limits.maxTotalBytes -> return ImportResult.Failed(ImportError.LimitExceeded("more than ${limits.maxTotalBytes} bytes in total"))
        }
        if (adoptFrom == null) {
            val required = total + maxOf(MIN_MARGIN, total / 50)
            val free = availableBytes()
            if (free < required) return ImportResult.Failed(ImportError.InsufficientSpace(required, free))
        }

        val hash = ManifestHash.of(files)
        val record = GameRecord(
            id = hash.take(8),
            title = detection.suggestedTitle.ifBlank { source.displayName }.ifBlank { "Untitled" },
            platform = detection.platform,
            engine = detection.engine,
            launch = LaunchSpec(detection.launchFile),
            hash = ContentHash(hash, HashMethod.MANIFEST),
            import = ImportInfo(clock(), source.displayName, source.kind, appVersion),
            raw = hintsJson(detection.hints),
        )
        val pending = try {
            library.beginImport(record)
        } catch (e: GameAlreadyInLibrary) {
            return ImportResult.AlreadyInLibrary(e.existing)
        } catch (e: IOException) {
            return ImportResult.Failed(ImportError.Storage(e.message ?: "cannot create the game folder"))
        }
        try {
            val outcome = if (adoptFrom != null) adopt(adoptFrom, prefix, pending.folder.game) else copyTree(source, prefix, files, pending.folder.game, total, shouldCancel, onProgress)
            if (outcome != null) {
                pending.abort()
                return outcome
            }
            onProgress(ImportProgress(ImportPhase.FINALIZING, total, total, files.size, files.size))
            return ImportResult.Imported(pending.commit(), detection.warnings)
        } catch (e: IOException) {
            pending.abort()
            return ImportResult.Failed(ImportError.Storage(e.message ?: e::class.java.simpleName))
        } catch (e: Throwable) {
            pending.abort()
            throw e
        }
    }

    private class Folder {
        val children = HashMap<String, Folder>()
    }

    /**
     * Path shape and folder-count limits. Folders are counted in a tree of path segments, one node per distinct
     * folder, so memory is bounded by the folders that are allowed, not by depth times files; counting stops at the limit.
     */
    private fun folderLimitError(files: List<SourceEntry>): ImportError? {
        val root = Folder()
        var folders = 0
        for (f in files) {
            if (f.path.length > MAX_PATH_LENGTH) return pathError()
            val parts = f.path.split('/')
            if (parts.size > MAX_DEPTH || parts.any { it.length > MAX_NAME_LENGTH }) return pathError()
            var node = root
            for (i in 0 until parts.size - 1) {
                node = node.children.getOrPut(parts[i]) { folders++; Folder() }
                if (files.size + folders > limits.maxEntries) return ImportError.LimitExceeded("more than ${limits.maxEntries} files and folders")
            }
        }
        return null
    }

    private fun pathError() = ImportError.LimitExceeded("a path is deeper than $MAX_DEPTH folders or has a name longer than $MAX_NAME_LENGTH characters")

    private fun adopt(from: File, prefix: String, gameDir: File): ImportResult? {
        val root = if (prefix.isEmpty()) from else File(from, prefix.trimEnd('/'))
        if (!root.isDirectory) return ImportResult.Failed(ImportError.Storage("unpacked game folder is missing"))
        // beginImport made an empty game/; a rename needs the target not to exist.
        if (!gameDir.delete()) throw IOException("cannot replace ${gameDir.path}")
        Files.move(root.toPath(), gameDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
        return null
    }

    /** Copies every file; returns null on success or the result to stop with. */
    private fun copyTree(
        source: GameSource,
        prefix: String,
        files: List<SourceEntry>,
        gameDir: File,
        total: Long,
        shouldCancel: () -> Boolean,
        onProgress: (ImportProgress) -> Unit,
    ): ImportResult? {
        val rootPath = gameDir.canonicalPath + File.separator
        var done = 0L
        var lastReported = 0L
        val buf = ByteArray(CHUNK)
        for ((index, entry) in files.withIndex()) {
            val dest = File(gameDir, entry.path)
            if (!(dest.canonicalPath + File.separator).startsWith(rootPath)) {
                return ImportResult.Failed(ImportError.SourceUnreadable("'${entry.path}' would be written outside the game folder"))
            }
            dest.parentFile?.mkdirs()
            var n = 0L
            try {
                source.open(prefix + entry.path).use { input ->
                    dest.outputStream().use { out ->
                        while (true) {
                            if (shouldCancel()) return ImportResult.Cancelled
                            val r = input.read(buf)
                            if (r < 0) break
                            // Never write past the size the source declared for this file.
                            if (n + r > entry.size) return ImportResult.Failed(ImportError.SourceChanged(entry.size, n + r))
                            out.write(buf, 0, r)
                            n += r
                            done += r
                            if (done - lastReported >= PROGRESS_STEP) {
                                lastReported = done
                                onProgress(ImportProgress(ImportPhase.COPYING, done, total, index, files.size))
                            }
                        }
                    }
                }
            } catch (e: EncryptedArchiveException) {
                return ImportResult.Failed(ImportError.PasswordRequired(e.message ?: "password protected"))
            } catch (e: IOException) {
                return ImportResult.Failed(ImportError.SourceUnreadable(e.message ?: "cannot read '${entry.path}'"))
            }
            if (n != entry.size) return ImportResult.Failed(ImportError.SourceChanged(entry.size, n))
        }
        onProgress(ImportProgress(ImportPhase.COPYING, done, total, files.size, files.size))
        return null
    }

    private fun hintsJson(hints: Set<String>): JsonObject =
        if (hints.isEmpty()) JsonObject(emptyMap())
        else JsonObject(mapOf("x" to JsonObject(mapOf("detection" to JsonObject(mapOf("hints" to JsonArray(hints.sorted().map { JsonPrimitive(it) })))))))

    private companion object {
        const val CHUNK = 64 * 1024
        const val PROGRESS_STEP = 1024 * 1024L
        const val MIN_MARGIN = 64L * 1024 * 1024
        const val MAX_DEPTH = 64
        const val MAX_NAME_LENGTH = 255
        const val MAX_PATH_LENGTH = 4096

        fun isJunk(path: String): Boolean =
            path.substringBefore('/').equals("__MACOSX", ignoreCase = true) ||
                path.substringAfterLast('/').let { it.equals(".DS_Store", true) || it.equals("Thumbs.db", true) }
    }
}
