// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing.api

import com.pocketverse.core.model.api.ContentHash
import com.pocketverse.core.model.api.GameRecord
import com.pocketverse.core.model.api.HashMethod
import com.pocketverse.core.model.api.ImportInfo
import com.pocketverse.core.model.api.LaunchSpec
import com.pocketverse.core.model.api.Platform
import com.pocketverse.core.storage.api.GameAlreadyInLibrary
import com.pocketverse.core.storage.api.Library
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.CRC32

/**
 * Imports one cartridge game (GBA or NDS) into the [library]: copies the ROM once while hashing it, then
 * publishes the finished game folder in a single step (spec section 7.3). Blocking; run it off the main thread.
 *
 * @param appVersion written to `meta.json` (`import.appVersion`).
 * @param availableBytes free space on the library's storage; replaceable for tests.
 */
class CartridgeImporter(
    private val library: Library,
    private val appVersion: String,
    private val clock: () -> Instant = Instant::now,
    private val availableBytes: () -> Long = { library.root.usableSpace },
) {
    /**
     * @param shouldCancel polled about every [CHUNK] bytes; true stops the import and removes everything written.
     * @param onProgress called on the calling thread, about every 1 MiB and at the end of each phase.
     */
    fun import(
        source: GameSource,
        detection: Detection.Cartridge,
        shouldCancel: () -> Boolean = { false },
        onProgress: (ImportProgress) -> Unit = {},
    ): ImportResult {
        val size = detection.rom.sizeBytes

        onProgress(ImportProgress(ImportPhase.CHECKING_SPACE, 0, 0))
        val required = size + maxOf(MIN_MARGIN, size / 100)
        val available = availableBytes()
        if (available < required) return ImportResult.Failed(ImportError.InsufficientSpace(required, available))

        val staging = try {
            library.createStagingDir()
        } catch (e: IOException) {
            return ImportResult.Failed(ImportError.Storage(e.message ?: "cannot create a working folder"))
        }
        try {
            val staged = File(staging, "rom.tmp")
            val hashes = when (val copied = copyAndHash(source, detection.romPath, staged, size, shouldCancel, onProgress)) {
                is Copy.Done -> copied
                Copy.Cancelled -> return ImportResult.Cancelled
                is Copy.Failed -> return ImportResult.Failed(copied.error)
            }

            onProgress(ImportProgress(ImportPhase.FINALIZING, size, size))
            val fileName = safeFileName(detection.romPath.substringAfterLast('/'), detection.platform)
            val record = GameRecord(
                id = hashes.sha256.take(8),
                title = detection.suggestedTitle.ifBlank { detection.rom.headerTitle }.ifBlank { "Untitled" },
                platform = detection.platform,
                engine = detection.engine,
                launch = LaunchSpec(fileName),
                hash = ContentHash(hashes.sha256, HashMethod.ROM, hashes.crc32),
                import = ImportInfo(clock(), source.displayName, source.kind, appVersion),
                rom = detection.rom,
            )
            val pending = try {
                library.beginImport(record)
            } catch (e: GameAlreadyInLibrary) {
                return ImportResult.AlreadyInLibrary(e.existing)
            }
            try {
                Files.move(staged.toPath(), File(pending.folder.game, fileName).toPath(), StandardCopyOption.ATOMIC_MOVE)
                val game = pending.commit()
                return ImportResult.Imported(game, detection.warnings)
            } catch (e: Throwable) {
                pending.abort()
                throw e
            }
        } catch (e: IOException) {
            return ImportResult.Failed(ImportError.Storage(e.message ?: e::class.java.simpleName))
        } finally {
            staging.deleteRecursively()
        }
    }

    private sealed interface Copy {
        class Done(val sha256: String, val crc32: String) : Copy
        data object Cancelled : Copy
        class Failed(val error: ImportError) : Copy
    }

    private fun copyAndHash(
        source: GameSource,
        path: String,
        target: File,
        expected: Long,
        shouldCancel: () -> Boolean,
        onProgress: (ImportProgress) -> Unit,
    ): Copy {
        val sha = MessageDigest.getInstance("SHA-256")
        val crc = CRC32()
        val buf = ByteArray(CHUNK)
        var done = 0L
        var lastReported = 0L
        try {
            source.open(path).use { input ->
                target.outputStream().use { out ->
                    while (true) {
                        if (shouldCancel()) return Copy.Cancelled
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        sha.update(buf, 0, n)
                        crc.update(buf, 0, n)
                        done += n
                        if (done - lastReported >= PROGRESS_STEP) {
                            lastReported = done
                            onProgress(ImportProgress(ImportPhase.COPYING, done, expected))
                        }
                    }
                }
            }
        } catch (e: EncryptedArchiveException) {
            return Copy.Failed(ImportError.PasswordRequired(e.message ?: "the archive is password protected"))
        } catch (e: IOException) {
            return Copy.Failed(ImportError.SourceUnreadable(e.message ?: "cannot read the source"))
        }
        if (done != expected) return Copy.Failed(ImportError.SourceChanged(expected, done))
        onProgress(ImportProgress(ImportPhase.COPYING, done, expected))
        return Copy.Done(sha.digest().toHex(), "%08X".format(crc.value))
    }

    companion object {
        const val CHUNK = 64 * 1024
        private const val PROGRESS_STEP = 1024 * 1024L
        private const val MIN_MARGIN = 8L * 1024 * 1024

        /** The ROM's own file name made safe to store; falls back to `rom.gba` or `rom.nds`. */
        internal fun safeFileName(name: String, platform: Platform): String {
            val cleaned = name.map { c -> if (c in "/\\:*?\"<>|" || c.code < 32 || c.code == 127) '_' else c }.joinToString("").trim().trim('.')
            val fallback = if (platform == Platform.NDS) "rom.nds" else "rom.gba"
            return cleaned.take(200).ifBlank { fallback }
        }

        private fun ByteArray.toHex(): String {
            val sb = StringBuilder(size * 2)
            for (b in this) sb.append("0123456789abcdef"[(b.toInt() shr 4) and 0xF]).append("0123456789abcdef"[b.toInt() and 0xF])
            return sb.toString()
        }
    }
}
