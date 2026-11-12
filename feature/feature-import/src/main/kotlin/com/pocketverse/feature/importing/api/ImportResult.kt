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
}

sealed interface ImportError {
    data class InsufficientSpace(val requiredBytes: Long, val availableBytes: Long) : ImportError
    data class SourceUnreadable(val message: String) : ImportError

    /** The file was not the size detection saw: it changed or was cut off while being read. */
    data class SourceChanged(val expectedBytes: Long, val readBytes: Long) : ImportError

    data class PasswordRequired(val message: String) : ImportError
    data class Storage(val message: String) : ImportError
}

enum class ImportPhase { CHECKING_SPACE, COPYING, FINALIZING }

/** [bytesTotal] is the size of the file being copied; both are 0 outside [ImportPhase.COPYING]. */
data class ImportProgress(val phase: ImportPhase, val bytesDone: Long, val bytesTotal: Long)
