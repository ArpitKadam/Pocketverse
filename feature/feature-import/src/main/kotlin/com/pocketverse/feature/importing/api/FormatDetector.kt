// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing.api

import com.pocketverse.core.model.api.EngineRef
import com.pocketverse.core.model.api.Platform
import com.pocketverse.feature.importing.CartridgeHeaders
import com.pocketverse.feature.importing.FolderGames
import com.pocketverse.feature.importing.HeaderResult

/**
 * Decides what a [GameSource] is by looking at its content (spec section 7.2): cartridge headers for
 * GBA and NDS, engine marker files for folder games. The file extension never decides; it only limits which
 * files in a folder are worth opening.
 */
object FormatDetector {
    private const val SAMPLE = 50
    private val romExtensions = setOf("gba", "agb", "nds", "dsi", "ids", "srl", "bin", "rom")
    private val archiveExtensions = setOf("zip", "7z", "rar", "tar", "gz")
    private val ignorable = setOf("__macosx", ".ds_store", "thumbs.db", "desktop.ini")

    fun detect(source: GameSource): Detection {
        val entries = source.entries()
        if (entries.isEmpty()) return Detection.Rejected(Detection.Reason.EMPTY, "The source contains no files.")
        return if (entries.size == 1) detectSingleFile(source, entries.single()) else detectTree(source, entries)
    }

    // ---- one file: sniff it whatever it is called
    private fun detectSingleFile(source: GameSource, entry: SourceEntry): Detection {
        sniff(source, entry)?.let { return cartridge(source, entry, it) }
        val ext = entry.path.substringAfterLast('.', "").lowercase()
        return when {
            ext in archiveExtensions -> Detection.Rejected(
                Detection.Reason.ARCHIVE_NOT_OPENED,
                "'${entry.path}' is an archive; it has to be opened before it can be recognised.",
                listOf(entry.path),
            )
            ext == "exe" -> Detection.Rejected(
                Detection.Reason.UNSUPPORTED_WINDOWS_PROGRAM,
                "'${entry.path}' is a Windows program. Only games built on a supported engine can be imported, not generic Windows programs.",
                listOf(entry.path),
            )
            else -> Detection.Rejected(
                Detection.Reason.UNRECOGNISED,
                "'${entry.path}' is not a GBA or NDS cartridge image: the header does not match either format.",
                listOf(entry.path),
            )
        }
    }

    // ---- a folder tree (or an unpacked archive)
    private fun detectTree(source: GameSource, entries: List<SourceEntry>): Detection {
        val fallback = source.displayName
        val folderMatches = folderMatches(source, entries, "", fallback).ifEmpty {
            wrapperPrefix(entries)?.let { prefix -> folderMatches(source, entries, prefix, prefix.trimEnd('/').substringAfterLast('/')) }.orEmpty()
        }
        if (folderMatches.size == 1) return folderMatches.single()
        if (folderMatches.size > 1) {
            return Detection.Rejected(
                Detection.Reason.CONFLICTING_ENGINES,
                "This folder looks like more than one game engine (${folderMatches.joinToString { it.engine.id }}).",
                sample(entries),
            )
        }

        val roms = entries
            .filter { it.path.substringAfterLast('.', "").lowercase() in romExtensions }
            .mapNotNull { e -> sniff(source, e)?.let { cartridge(source, e, it) } }
        if (roms.size == 1) return roms.single()
        if (roms.size > 1) return Detection.Ambiguous(roms)

        return if (entries.any { it.path.endsWith(".exe", ignoreCase = true) }) {
            Detection.Rejected(
                Detection.Reason.UNSUPPORTED_WINDOWS_PROGRAM,
                "This folder has a Windows program but none of the data files of a supported engine " +
                    "(RPG Maker, RPG Maker MV/MZ, Ren'Py). Generic Windows programs are not supported.",
                sample(entries),
            )
        } else {
            Detection.Rejected(Detection.Reason.UNRECOGNISED, "Nothing in this folder matches a supported game or cartridge format.", sample(entries))
        }
    }

    private fun folderMatches(source: GameSource, entries: List<SourceEntry>, prefix: String, fallback: String) =
        FolderGames.detect(source, entries, prefix, fallback)

    /** `Wrapper/` when every real file lives under one top-level folder (typical for a zipped game), else null. */
    private fun wrapperPrefix(entries: List<SourceEntry>): String? {
        val tops = entries
            .filter { e -> e.path.substringBefore('/').lowercase() !in ignorable }
            .map { e -> if ('/' in e.path) e.path.substringBefore('/') else null }
            .distinct()
        return tops.singleOrNull()?.let { "$it/" }
    }

    // ---- cartridges
    private fun sniff(source: GameSource, entry: SourceEntry): HeaderResult? {
        if (entry.size < 0xC0) return null
        val wanted = minOf(entry.size, CartridgeHeaders.SNIFF_BYTES.toLong()).toInt()
        val buf = ByteArray(wanted)
        val read = try {
            source.open(entry.path).use { it.readNBytes(buf, 0, wanted) }
        } catch (_: Exception) {
            return null
        }
        return CartridgeHeaders.parse(if (read == wanted) buf else buf.copyOf(read), entry.size)
    }

    private fun cartridge(source: GameSource, entry: SourceEntry, h: HeaderResult): Detection.Cartridge {
        val base = entry.path.substringAfterLast('/').substringBeforeLast('.', "").ifBlank { source.displayName }
        return Detection.Cartridge(
            platform = h.platform,
            engine = EngineRef(if (h.platform == Platform.GBA) "libretro-mgba" else "libretro-melonds"),
            romPath = entry.path,
            rom = h.rom,
            suggestedTitle = base,
            warnings = h.warnings,
        )
    }

    private fun sample(entries: List<SourceEntry>) = entries.take(SAMPLE).map { it.path }
}
