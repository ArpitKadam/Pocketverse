// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.app

import com.pocketverse.core.storage.api.GameFolder
import com.pocketverse.core.storage.api.GameAlreadyInLibrary
import com.pocketverse.core.storage.api.Library
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DebugImportStubTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `adding a test game creates the full folder layout with a readable meta and the fake rom`() {
        val library = Library(tmp.newFolder("lib"))
        val game = DebugImportStub.add(library, seed = 1, now = Instant.parse("2026-11-12T10:00:00Z"))

        GameFolder.SUBFOLDERS.forEach { assertTrue(it, File(game.root, it).isDirectory) }
        assertTrue(File(game.game, "test.gba").isFile)
        val meta = game.readMeta()
        assertEquals("test.gba", meta.launch.file)
        assertEquals(game.name, "${meta.title} [${meta.id}]")
        assertEquals(listOf(game.name), library.scan().games.map { it.name })
    }

    @Test
    fun `different seeds give different games and the same seed is refused as a duplicate`() {
        val library = Library(tmp.newFolder("lib"))
        DebugImportStub.add(library, seed = 1)
        DebugImportStub.add(library, seed = 2)
        assertEquals(2, library.scan().games.size)
        try {
            DebugImportStub.add(library, seed = 1)
            error("expected GameAlreadyInLibrary")
        } catch (_: GameAlreadyInLibrary) {
        }
        // The refused attempt left nothing half-imported behind.
        assertEquals(0, library.cleanupAbandonedImports())
    }
}
