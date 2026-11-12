// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing.api

import com.pocketverse.core.model.api.EngineRef
import com.pocketverse.core.model.api.Platform
import com.pocketverse.core.model.api.RomInfo

/** What the detector decided about a [GameSource]. Never throws for a bad source: it returns [Rejected]. */
sealed interface Detection {
    /** A cartridge image (GBA or NDS), recognised by its header, not by its file name. */
    data class Cartridge(
        val platform: Platform,
        val engine: EngineRef,
        /** Path of the ROM inside the source. */
        val romPath: String,
        val rom: RomInfo,
        /** Display title: the file name without extension (the header title is kept in [rom]). */
        val suggestedTitle: String,
        /** Problems that did not stop the import (for example a header checksum a hack left wrong). */
        val warnings: List<String> = emptyList(),
    ) : Detection

    /** A game that lives in a folder tree: RPG Maker, RPG Maker MV/MZ or Ren'Py. */
    data class FolderGame(
        val platform: Platform,
        val engine: EngineRef,
        /** Path inside the source of the folder that holds the game (`""` for the source root, else `Wrapper/`). */
        val rootPrefix: String,
        /** Launch file relative to the game root. Ren'Py games without a launcher script use `game`. */
        val launchFile: String,
        val suggestedTitle: String,
        /** Non-binding facts such as `pokemon-essentials`. */
        val hints: Set<String> = emptySet(),
        val warnings: List<String> = emptyList(),
    ) : Detection

    /** Several valid cartridges in one source; the caller must ask the user which to import. */
    data class Ambiguous(val candidates: List<Cartridge>) : Detection

    /** Not importable. [message] is plain English for logs; the UI maps [reason] to its own wording. */
    data class Rejected(val reason: Reason, val message: String, val fileSample: List<String> = emptyList()) : Detection

    enum class Reason {
        /** No files at all. */
        EMPTY,

        /** The source is an archive that must be opened first. */
        ARCHIVE_NOT_OPENED,

        /** A Windows program with no recognisable engine data: not supported (no Wine layer). */
        UNSUPPORTED_WINDOWS_PROGRAM,

        /** Nothing recognisable. */
        UNRECOGNISED,

        /** More than one engine matched the same folder. */
        CONFLICTING_ENGINES,
    }
}
