// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.library.api

import com.pocketverse.core.database.api.GameEntry

/**
 * Everything the library screen shows. [loading] is true until the first sync with the folders finishes, so
 * an empty list is never shown while games may still be on their way.
 */
data class LibraryUiState(
    val loading: Boolean = true,
    val games: List<GameEntry> = emptyList(),
    /** Folders in the library that could not be read (broken or too-new `meta.json`). */
    val unreadableFolders: Int = 0,
    val error: String? = null,
)
