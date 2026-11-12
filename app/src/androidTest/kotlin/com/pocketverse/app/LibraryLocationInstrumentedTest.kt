// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a device or emulator (not on the JVM): the real app-specific external directory, the real
 * Application wiring, and the folder layout an import creates there. Not run during P1-5 (no emulator
 * configured, and nothing is installed on the owner's phone without asking).
 */
@RunWith(AndroidJUnit4::class)
class LibraryLocationInstrumentedTest {
    @Test
    fun libraryLivesInTheAppSpecificExternalDirectoryAndAcceptsAnImport() {
        val app = ApplicationProvider.getApplicationContext<PocketverseApp>()
        val library = app.library.getOrThrow()
        val expectedParent = app.getExternalFilesDir(null)!!.absolutePath
        assertEquals("$expectedParent/Pocketverse", library.root.absolutePath)
        assertTrue(library.root.resolve(".nomedia").isFile)

        val game = DebugImportStub.add(library)
        try {
            assertTrue(game.metaFile.isFile)
            assertTrue(game.game.resolve("test.gba").isFile)
            assertTrue(library.scan().games.any { it.name == game.name })
        } finally {
            game.deleteRecursively()
        }
    }
}
