// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing.api

import com.pocketverse.core.storage.api.GameFolder

/** How an import ended. Importing never throws for expected problems; each has its own case. */
sealed interface ImportResult {
    data class Imported(val game: GameFolder, val warnings: List<String>) : ImportResult

    /** The same content is already in the library; nothing was changed. */
    data class AlreadyInLibrary(val existing: GameFolder) : ImportResult

    /** The user cancelled; nothing was left behind. */
    data object Cancelled : ImportResult

    data class Failed(val error: ImportError) : ImportResult

    /** Detection said this is not importable; show [rejection] to the user. */
    data class Rejected(val rejection: Detection.Rejected) : ImportResult

    /** Several cartridges were found; ask the user which one, then import again with that path. */
    data class NeedsChoice(val candidates: List<Detection.Cartridge>) : ImportResult
}

sealed interface ImportError {
    data class InsufficientSpace(val requiredBytes: Long, val availableBytes: Long) : ImportError
    data class SourceUnreadable(val message: String) : ImportError

    /** The file was not the size detection saw: it changed or was cut off while being read. */
    data class SourceChanged(val expectedBytes: Long, val readBytes: Long) : ImportError

    data class PasswordRequired(val message: String) : ImportError
    data class Storage(val message: String) : ImportError

    /** The source is bigger or has more files than the safety limits allow ([what] says which). */
    data class LimitExceeded(val what: String) : ImportError

    /** An archive format we cannot open (for example RAR). */
    data class UnsupportedArchive(val kind: String) : ImportError
}

enum class ImportPhase { CHECKING_SPACE, EXTRACTING, COPYING, FINALIZING }

/** Bytes and files done so far in the current phase; totals are 0 when unknown. */
data class ImportProgress(
    val phase: ImportPhase,
    val bytesDone: Long,
    val bytesTotal: Long,
    val filesDone: Int = 0,
    val filesTotal: Int = 0,
)
