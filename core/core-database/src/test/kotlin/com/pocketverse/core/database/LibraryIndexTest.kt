// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pocketverse.core.database.api.LibraryIndex
import com.pocketverse.core.model.api.ContentHash
import com.pocketverse.core.model.api.EngineRef
import com.pocketverse.core.model.api.GameRecord
import com.pocketverse.core.model.api.HashMethod
import com.pocketverse.core.model.api.ImportInfo
import com.pocketverse.core.model.api.LaunchSpec
import com.pocketverse.core.model.api.Platform
import com.pocketverse.core.model.api.RomInfo
import com.pocketverse.core.model.api.SourceKind
import com.pocketverse.core.storage.api.GameFolder
import com.pocketverse.core.storage.api.Library
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryIndexTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var library: Library
    private lateinit var index: LibraryIndex

    @Before
    fun setUp() {
        library = Library(tmp.newFolder("lib"))
        index = LibraryIndex.inMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() = index.close()

    private fun import(title: String, platform: Platform = Platform.GBA, prefix: String): GameFolder {
        val record = GameRecord(
            id = prefix,
            title = title,
            platform = platform,
            engine = EngineRef("libretro-mgba"),
            launch = LaunchSpec("x.bin"),
            hash = ContentHash(prefix + "0".repeat(64 - prefix.length), HashMethod.ROM),
            import = ImportInfo(Instant.parse("2026-11-12T10:00:00Z"), "x.bin", SourceKind.FILE, "0.1.0"),
            rom = RomInfo("AAAA", "T", 1),
        )
        return library.beginImport(record).commit()
    }

    private fun titles(flowTitles: List<com.pocketverse.core.database.api.GameEntry>) = flowTitles.map { it.title }

    @Test
    fun `first sync adds every game and lists them sorted by title ignoring case`() = runBlocking {
        import("banana", prefix = "bbbbbbbb")
        import("Apple", prefix = "aaaaaaaa")
        import("cherry", prefix = "cccccccc")

        val report = index.sync(library)

        assertEquals(3, report.added)
        assertEquals(0, report.updated)
        assertEquals(0, report.removed)
        assertEquals(listOf("Apple", "banana", "cherry"), titles(index.games().first()))
    }

    @Test
    fun `a second sync with nothing changed touches nothing`() = runBlocking {
        import("Apple", prefix = "aaaaaaaa")
        index.sync(library)
        val again = index.sync(library)
        assertEquals(0, again.added + again.updated + again.removed)
    }

    @Test
    fun `a rewritten meta json updates the row and a deleted folder removes it`() = runBlocking {
        val apple = import("Apple", prefix = "aaaaaaaa")
        val pear = import("Pear", prefix = "99999999")
        index.sync(library)

        apple.writeMeta(apple.readMeta().copy(title = "Apple Renamed"))
        pear.deleteRecursively()
        val report = index.sync(library)

        assertEquals(1, report.updated)
        assertEquals(1, report.removed)
        assertEquals(listOf("Apple Renamed"), titles(index.games().first()))
    }

    @Test
    fun `search is case insensitive, substring based and treats percent and underscore literally`() = runBlocking {
        import("Fire Red 100%", prefix = "aaaaaaaa")
        import("Leaf_Green", prefix = "bbbbbbbb")
        import("Emerald", prefix = "cccccccc")
        index.sync(library)

        assertEquals(listOf("Fire Red 100%"), titles(index.search("RED").first()))
        assertEquals(listOf("Fire Red 100%"), titles(index.search("100%").first()))
        assertEquals(listOf("Leaf_Green"), titles(index.search("f_g").first()))
        assertEquals(emptyList<String>(), titles(index.search("%").first().filter { it.title == "Emerald" }))
        assertEquals(3, index.search("   ").first().size)
    }

    @Test
    fun `platform filter returns only that platform`() = runBlocking {
        import("A", Platform.GBA, "aaaaaaaa")
        import("B", Platform.NDS, "bbbbbbbb")
        index.sync(library)
        assertEquals(listOf("B"), titles(index.games(Platform.NDS).first()))
    }

    @Test
    fun `recording a play writes meta json first and updates the row`() = runBlocking {
        val game = import("Apple", prefix = "aaaaaaaa")
        index.sync(library)

        val at = Instant.parse("2026-11-13T08:30:00Z")
        index.recordPlay(game, 600, at)
        index.recordPlay(game, 60, at.plusSeconds(3600))

        val stats = game.readMeta().stats!!
        assertEquals(660, stats.playTimeSeconds)
        assertEquals(2, stats.launchCount)
        val row = index.games().first().single()
        assertEquals(660, row.playTimeSeconds)
        assertEquals(at.plusSeconds(3600), row.lastPlayedAt)
        // The row already matches the file, so a sync has nothing to do.
        val report = index.sync(library)
        assertEquals(0, report.updated)
    }

    @Test
    fun `the index is rebuildable from the folders alone`() = runBlocking {
        import("Apple", prefix = "aaaaaaaa")
        import("Pear", prefix = "99999999")
        index.sync(library)
        val before = index.games().first()

        val fresh = LibraryIndex.inMemory(ApplicationProvider.getApplicationContext<Context>())
        try {
            fresh.sync(library)
            assertEquals(before, fresh.games().first())
        } finally {
            fresh.close()
        }
    }

    @Test
    fun `a broken folder is reported by sync and does not stop the others`() = runBlocking {
        import("Apple", prefix = "aaaaaaaa")
        File(library.root, "Bad [deadbeef]").mkdir()
        val report = index.sync(library)
        assertEquals(1, report.added)
        assertTrue(report.problems.any { it.folderName == "Bad [deadbeef]" })
    }
}
