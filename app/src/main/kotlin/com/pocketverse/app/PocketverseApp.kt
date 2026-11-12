// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.app

import android.app.Application
import com.pocketverse.core.database.api.LibraryIndex
import com.pocketverse.core.storage.api.Library
import java.io.File
import java.io.IOException

/**
 * Composition root. Hand-wired on purpose: there are two collaborators, a dependency-injection framework
 * is added when there are enough to justify it.
 */
class PocketverseApp : Application() {
    /** The library, or the reason it cannot be opened (external storage missing, folder not creatable). */
    val library: Result<Library> by lazy {
        runCatching {
            val base = getExternalFilesDir(null) ?: throw IOException("external storage is not available")
            Library(File(base, LIBRARY_DIR)).also { it.cleanupAbandonedImports() }
        }
    }

    val index: LibraryIndex by lazy { LibraryIndex.open(this) }

    private companion object {
        /** Default library root under the app-specific external directory (`docs/architecture/STORAGE.md`). */
        const val LIBRARY_DIR = "Pocketverse"
    }
}
