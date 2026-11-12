// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing.api

import com.pocketverse.core.model.api.SourceKind
import com.pocketverse.core.storage.api.Library
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * The one entry point for importing something the user picked from local storage: a ROM, a game folder, or an
 * archive (zip, 7z, tar, tar.gz). It unpacks archives safely, recognises the content, and hands over to the
 * right importer. Blocking; run it off the main thread.
 */
class GameImporter(
    private val library: Library,
    private val appVersion: String,
    private val clock: () -> Instant = Instant::now,
    private val availableBytes: () -> Long = { library.root.usableSpace },
    private val limits: ImportLimits = ImportLimits(),
) {
    private val extractor = ArchiveExtractor(limits, availableBytes = { availableBytes() })
    private val cartridges = CartridgeImporter(library, appVersion, clock, availableBytes)
    private val folders = FolderGameImporter(library, appVersion, clock, availableBytes, limits)

    /**
     * @param passwords asked when an archive is encrypted; the default gives up at once.
     * @param preferredRom when the source holds several cartridges, the [Detection.Cartridge.romPath] the user
     * chose; without it the result is [ImportResult.NeedsChoice].
     */
    fun import(
        input: File,
        passwords: ArchivePasswords = ArchivePasswords { _, _ -> null },
        preferredRom: String? = null,
        shouldCancel: () -> Boolean = { false },
        onProgress: (ImportProgress) -> Unit = {},
    ): ImportResult {
        if (!input.exists()) return ImportResult.Failed(ImportError.SourceUnreadable("'${input.name}' does not exist"))

        val isArchive = input.isFile && extractor.kindOf(input) != null
        if (!isArchive) return importFrom(FileSystemSource(input), null, preferredRom, shouldCancel, onProgress)

        val staging = try {
            library.createStagingDir()
        } catch (e: IOException) {
            return ImportResult.Failed(ImportError.Storage(e.message ?: "cannot create a working folder"))
        }
        try {
            val unpacked = File(staging, "unpacked").also { it.mkdir() }
            when (val r = extractor.extract(input, unpacked, passwords, shouldCancel, onProgress)) {
                is ExtractResult.Cancelled -> return ImportResult.Cancelled
                is ExtractResult.Failed -> return ImportResult.Failed(r.error)
                is ExtractResult.Done -> Unit
            }
            val source = LabelledSource(FileSystemSource(unpacked), input.name, SourceKind.ARCHIVE)
            return importFrom(source, unpacked, preferredRom, shouldCancel, onProgress)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun importFrom(
        source: GameSource,
        unpackedRoot: File?,
        preferredRom: String?,
        shouldCancel: () -> Boolean,
        onProgress: (ImportProgress) -> Unit,
    ): ImportResult = when (val detection = FormatDetector.detect(source)) {
        is Detection.Rejected -> ImportResult.Rejected(detection)
        is Detection.Ambiguous -> {
            val chosen = detection.candidates.firstOrNull { it.romPath == preferredRom }
            if (chosen == null) ImportResult.NeedsChoice(detection.candidates) else cartridges.import(source, chosen, shouldCancel, onProgress)
        }
        is Detection.Cartridge -> cartridges.import(source, detection, shouldCancel, onProgress)
        is Detection.FolderGame -> folders.import(source, detection, adoptFrom = unpackedRoot, shouldCancel = shouldCancel, onProgress = onProgress)
    }

    /** Same files, different label: an unpacked archive is shown to the user (and recorded) as the archive. */
    private class LabelledSource(private val inner: GameSource, override val displayName: String, override val kind: SourceKind) : GameSource {
        override fun entries() = inner.entries()
        override fun open(path: String) = inner.open(path)
    }
}
