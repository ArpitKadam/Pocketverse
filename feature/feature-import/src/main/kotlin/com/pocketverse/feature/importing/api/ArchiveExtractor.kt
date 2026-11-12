// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing.api

import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

/** Asked for a password when an archive is encrypted; return null to give up. [attempt] starts at 1. */
fun interface ArchivePasswords {
    fun password(archiveName: String, attempt: Int): CharArray?
}

sealed interface ExtractResult {
    /** [root] holds the unpacked files. [skippedUnsafe] entries had names that could escape the folder and were left out. */
    data class Done(val root: File, val fileCount: Int, val skippedUnsafe: Int) : ExtractResult

    data object Cancelled : ExtractResult
    data class Failed(val error: ImportError) : ExtractResult
}

/**
 * Unpacks zip, 7z, tar and tar.gz archives into a folder, safely (no path escapes, size and count limits that a
 * lying archive cannot talk its way past, no links). The format is recognised by the file's first bytes, not
 * its name. RAR is not supported.
 */
class ArchiveExtractor(
    private val limits: ImportLimits = ImportLimits(),
    private val availableBytes: (File) -> Long = { it.usableSpace },
) {
    /** Which archive format [file] is, or null when it is not one we can open. Reads a few hundred bytes. */
    fun kindOf(file: File): String? {
        if (!file.isFile || file.length() < 6) return null
        val h = file.inputStream().use { it.readNBytes(512) }
        fun at(i: Int, vararg b: Int) = h.size > i + b.size && b.indices.all { h[i + it] == b[it].toByte() }
        return when {
            at(0, 0x50, 0x4B, 0x03, 0x04) || at(0, 0x50, 0x4B, 0x05, 0x06) -> "zip"
            at(0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> "7z"
            at(0, 0x1F, 0x8B) -> "gzip"
            h.size >= 262 && String(h, 257, 5, Charsets.ISO_8859_1) == "ustar" -> "tar"
            at(0, 0x52, 0x61, 0x72, 0x21) -> "rar"
            else -> null
        }
    }

    fun extract(
        archive: File,
        target: File,
        passwords: ArchivePasswords = ArchivePasswords { _, _ -> null },
        shouldCancel: () -> Boolean = { false },
        onProgress: (ImportProgress) -> Unit = {},
    ): ExtractResult {
        val kind = kindOf(archive)
        return try {
            when (kind) {
                "zip" -> extractZip(archive, target, passwords, shouldCancel, onProgress)
                "7z" -> extractSevenZ(archive, target, passwords, shouldCancel, onProgress)
                "gzip", "tar" -> extractTar(archive, target, shouldCancel, onProgress)
                "rar" -> ExtractResult.Failed(ImportError.UnsupportedArchive("RAR"))
                else -> ExtractResult.Failed(ImportError.UnsupportedArchive("not a supported archive"))
            }
        } catch (e: EncryptedArchiveException) {
            ExtractResult.Failed(ImportError.PasswordRequired(e.message ?: "the archive is password protected"))
        } catch (e: IOException) {
            ExtractResult.Failed(ImportError.SourceUnreadable(e.message ?: "cannot read ${archive.name}"))
        }
    }

    // ---------------------------------------------------------------- zip
    private fun extractZip(archive: File, target: File, passwords: ArchivePasswords, cancel: () -> Boolean, progress: (ImportProgress) -> Unit): ExtractResult {
        val zip = ZipFile(archive)
        if (zip.isEncrypted) {
            var attempt = 0
            while (true) {
                val pw = passwords.password(archive.name, ++attempt) ?: return ExtractResult.Failed(ImportError.PasswordRequired("'${archive.name}' needs a password"))
                zip.setPassword(pw)
                if (zipPasswordWorks(zip)) break
                if (attempt >= MAX_PASSWORD_ATTEMPTS) return ExtractResult.Failed(ImportError.PasswordRequired("wrong password for '${archive.name}'"))
            }
        }
        val headers = zip.fileHeaders.filter { !it.isDirectory }
        val total = headers.sumOf { it.uncompressedSize.coerceAtLeast(0) }
        val writer = Writer(target, archive.name, total, headers.size, cancel, progress)
        for (h in zip.fileHeaders) {
            val stop = writer.entry(h.fileName, h.isDirectory, h.uncompressedSize) { zip.getInputStream(h) }
            if (stop != null) return stop
        }
        return writer.done()
    }

    /** Reads the smallest encrypted file to the end: a wrong password fails there (check byte, MAC or CRC). */
    private fun zipPasswordWorks(zip: ZipFile): Boolean {
        val probe = zip.fileHeaders.filter { !it.isDirectory && it.isEncrypted && it.uncompressedSize in 1..PROBE_LIMIT }.minByOrNull { it.uncompressedSize }
            ?: return true
        return try {
            zip.getInputStream(probe).use { it.readAllBytes() }
            true
        } catch (_: ZipException) {
            false
        } catch (_: IOException) {
            false
        }
    }

    // ---------------------------------------------------------------- 7z
    private fun extractSevenZ(archive: File, target: File, passwords: ArchivePasswords, cancel: () -> Boolean, progress: (ImportProgress) -> Unit): ExtractResult {
        var pw: CharArray? = null
        var attempt = 0
        while (true) {
            try {
                return SevenZFile.builder().setFile(archive).apply { pw?.let { setPassword(it) } }.get().use { sz ->
                    val all = sz.entries.filter { !it.isDirectory }
                    val writer = Writer(target, archive.name, all.sumOf { it.size.coerceAtLeast(0) }, all.size, cancel, progress)
                    var entry = sz.nextEntry
                    while (entry != null) {
                        val current = entry
                        val stop = writer.entry(current.name, current.isDirectory, current.size) { sz.getInputStream(current) }
                        if (stop != null) return stop
                        entry = sz.nextEntry
                    }
                    writer.done()
                }
            } catch (e: PasswordRequiredException) {
                pw = passwords.password(archive.name, ++attempt) ?: return ExtractResult.Failed(ImportError.PasswordRequired("'${archive.name}' needs a password"))
                if (attempt > MAX_PASSWORD_ATTEMPTS) return ExtractResult.Failed(ImportError.PasswordRequired("wrong password for '${archive.name}'"))
            }
        }
    }

    // ---------------------------------------------------------------- tar, tar.gz
    private fun extractTar(archive: File, target: File, cancel: () -> Boolean, progress: (ImportProgress) -> Unit): ExtractResult {
        val raw: InputStream = BufferedInputStream(archive.inputStream())
        val stream = if (kindOf(archive) == "gzip") GZIPInputStream(raw) else raw
        TarArchiveInputStream(stream).use { tar ->
            // A tar has no index, so totals are unknown up front; the limits are enforced as entries arrive.
            val writer = Writer(target, archive.name, archive.length(), 0, cancel, progress)
            while (true) {
                val e = tar.nextEntry ?: break
                if (e.isSymbolicLink || e.isLink || (!e.isFile && !e.isDirectory)) continue
                val stop = writer.entry(e.name, e.isDirectory, e.size, ownsStream = false) { tar }
                if (stop != null) return stop
            }
            return writer.done()
        }
    }

    // ---------------------------------------------------------------- shared writing logic
    private inner class Writer(
        private val target: File,
        private val archiveName: String,
        private val declaredTotal: Long,
        private val entryTotal: Int,
        private val cancel: () -> Boolean,
        private val progress: (ImportProgress) -> Unit,
    ) {
        private val targetPath = target.canonicalPath + File.separator
        private var files = 0
        private var written = 0L
        private var skipped = 0

        /** Returns a final result to stop with, or null to carry on. [open] is called only for regular files. */
        fun entry(rawName: String, isDirectory: Boolean, declared: Long, ownsStream: Boolean = true, open: () -> InputStream): ExtractResult? {
            if (cancel()) return ExtractResult.Cancelled
            val name = ZipSource.normalise(rawName)?.takeIf { !isJunk(it) }
            if (name == null) {
                if (!isJunkName(rawName)) skipped++
                return null
            }
            val dest = File(target, name)
            if (!(dest.canonicalPath + File.separator).startsWith(targetPath)) {
                skipped++
                return null
            }
            if (isDirectory) {
                dest.mkdirs()
                return null
            }
            if (++files > limits.maxEntries) return fail(ImportError.LimitExceeded("more than ${limits.maxEntries} files"))
            if (declared > limits.maxEntryBytes) return fail(ImportError.LimitExceeded("'$name' is larger than ${limits.maxEntryBytes} bytes"))
            val cap = if (declared >= 0) declared else limits.maxEntryBytes
            if (written + cap > limits.maxTotalBytes) return fail(ImportError.LimitExceeded("more than ${limits.maxTotalBytes} bytes in total"))
            val margin = maxOf(MIN_MARGIN, (written + cap) / 50)
            val free = availableBytes(target)
            if (free < cap + margin) return fail(ImportError.InsufficientSpace(cap + margin, free))

            dest.parentFile?.mkdirs()
            var n = 0L
            try {
                val input = open()
                try {
                    dest.outputStream().use { out ->
                        val buf = ByteArray(CHUNK)
                        while (true) {
                            if (cancel()) return ExtractResult.Cancelled
                            val r = input.read(buf)
                            if (r < 0) break
                            // Never write past what the archive declared for this entry.
                            if (n + r > cap) return fail(ImportError.SourceChanged(cap, n + r))
                            out.write(buf, 0, r)
                            n += r
                            written += r
                            if (written % PROGRESS_STEP < r) report()
                        }
                    }
                } finally {
                    if (ownsStream) input.close()
                }
            } catch (e: ZipException) {
                if (e.message?.contains("password", ignoreCase = true) == true) return fail(ImportError.PasswordRequired("wrong password for '$archiveName'"))
                throw IOException(e.message, e)
            }
            if (declared >= 0 && n != declared) return fail(ImportError.SourceChanged(declared, n))
            report()
            return null
        }

        fun done(): ExtractResult {
            report()
            return ExtractResult.Done(target, files, skipped)
        }

        private fun report() = progress(ImportProgress(ImportPhase.EXTRACTING, written, declaredTotal, files, entryTotal))
        private fun fail(e: ImportError) = ExtractResult.Failed(e)
    }

    private companion object {
        const val CHUNK = 64 * 1024
        const val PROGRESS_STEP = 1024 * 1024L
        const val MIN_MARGIN = 64L * 1024 * 1024
        const val PROBE_LIMIT = 1024L * 1024
        const val MAX_PASSWORD_ATTEMPTS = 5

        fun isJunkName(raw: String): Boolean {
            val n = raw.replace('\\', '/')
            return n.substringBefore('/').equals("__MACOSX", ignoreCase = true) ||
                n.substringAfterLast('/').let { it.equals(".DS_Store", true) || it.equals("Thumbs.db", true) }
        }

        fun isJunk(normalised: String) = isJunkName(normalised)
    }
}
