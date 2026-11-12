// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.core.storage.api

import com.pocketverse.core.model.api.ContentHash
import com.pocketverse.core.model.api.EngineRef
import com.pocketverse.core.model.api.GameRecord
import com.pocketverse.core.model.api.HashMethod
import com.pocketverse.core.model.api.ImportInfo
import com.pocketverse.core.model.api.LaunchSpec
import com.pocketverse.core.model.api.Platform
import com.pocketverse.core.model.api.RomInfo
import com.pocketverse.core.model.api.SourceKind
import com.pocketverse.core.storage.AtomicFiles
import java.io.File
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

class LibraryTest {
    @TempDir
    lateinit var tmp: File

    private fun record(title: String = "Example GBA Game", shaPrefix: String = "3fa94c1e", id: String = shaPrefix) = GameRecord(
        id = id,
        title = title,
        platform = Platform.GBA,
        engine = EngineRef("libretro-mgba"),
        launch = LaunchSpec("example.gba"),
        hash = ContentHash(shaPrefix + "0".repeat(64 - shaPrefix.length), HashMethod.ROM),
        import = ImportInfo(Instant.parse("2026-11-12T10:00:00Z"), "example.gba", SourceKind.FILE, "0.1.0"),
        rom = RomInfo("AXXE", "EXAMPLE", 16),
    )

    @Test
    fun `creating a library makes the root and a nomedia marker`() {
        val lib = Library(File(tmp, "lib"))
        assertTrue(File(lib.root, ".nomedia").isFile)
    }

    @Test
    fun `commit publishes a complete folder with every subfolder and a readable meta`() {
        val lib = Library(tmp)
        val pending = lib.beginImport(record())
        File(pending.folder.game, "example.gba").writeText("rom")
        val game = pending.commit()

        assertEquals("Example GBA Game [3fa94c1e]", game.name)
        GameFolder.SUBFOLDERS.forEach { assertTrue(File(game.root, it).isDirectory, it) }
        assertEquals("rom", File(game.game, "example.gba").readText())
        assertEquals("Example GBA Game", game.readMeta().title)
        assertEquals(listOf(game.name), lib.scan().games.map { it.folder.name })
    }

    @Test
    fun `nothing is visible before commit and abort leaves nothing behind`() {
        val lib = Library(tmp)
        val pending = lib.beginImport(record())
        assertTrue(lib.scan().games.isEmpty())
        assertEquals(1, tmp.listFiles { f -> f.name.startsWith(Library.IMPORTING_PREFIX) }!!.size)
        pending.abort()
        assertEquals(0, tmp.listFiles { f -> f.name.startsWith(Library.IMPORTING_PREFIX) }!!.size)
    }

    @Test
    fun `importing the same content twice is refused with the existing folder`() {
        val lib = Library(tmp)
        lib.beginImport(record()).commit()
        val e = assertThrows<GameAlreadyInLibrary> { lib.beginImport(record(title = "Same ROM, new title")) }
        assertEquals("Example GBA Game [3fa94c1e]", e.existing.name)
    }

    @Test
    fun `a short-id collision between different content gets a 12 character id`() {
        val lib = Library(tmp)
        lib.beginImport(record(shaPrefix = "3fa94c1e")).commit()
        // Different content whose hash starts with the same 8 characters, same title.
        val other = record(shaPrefix = "3fa94c1e").let { it.copy(hash = ContentHash("3fa94c1e" + "1".repeat(56), HashMethod.ROM)) }
        val game = lib.beginImport(other).commit()
        assertEquals("Example GBA Game [3fa94c1e1111]", game.name)
        assertEquals("3fa94c1e1111", game.readMeta().id)
        assertEquals(2, lib.scan().games.size)
    }

    @Test
    fun `scan reports a broken folder instead of failing and ignores hidden folders`() {
        val lib = Library(tmp)
        lib.beginImport(record()).commit()
        File(tmp, "Broken [deadbeef]").mkdir()
        File(File(tmp, "Broken2 [cafebabe]").also { it.mkdir() }, "meta.json").writeText("{ nope")
        File(tmp, ".hidden").mkdir()

        val scan = lib.scan()
        assertEquals(1, scan.games.size)
        assertEquals(setOf("Broken [deadbeef]", "Broken2 [cafebabe]"), scan.problems.map { it.folderName }.toSet())
    }

    @Test
    fun `cleanup removes abandoned imports and reports how many`() {
        val lib = Library(tmp)
        lib.beginImport(record(shaPrefix = "aaaaaaaa"))
        lib.beginImport(record(shaPrefix = "bbbbbbbb"))
        assertEquals(2, lib.cleanupAbandonedImports())
        assertEquals(0, lib.cleanupAbandonedImports())
    }

    @Test
    fun `a staging dir is hidden, inside the library, and removed by cleanup`() {
        val lib = Library(tmp)
        val staging = lib.createStagingDir()
        assertTrue(staging.isDirectory)
        assertEquals(tmp.canonicalFile, staging.parentFile.canonicalFile)
        assertTrue(staging.name.startsWith(Library.IMPORTING_PREFIX))
        assertTrue(lib.scan().games.isEmpty())
        assertEquals(1, lib.cleanupAbandonedImports())
        assertFalse(staging.exists())
    }

    @Test
    fun `writeMeta replaces the file atomically and leaves no temp file`() {
        val lib = Library(tmp)
        val game = lib.beginImport(record()).commit()
        game.writeMeta(game.readMeta().copy(title = "Renamed"))
        assertEquals("Renamed", game.readMeta().title)
        assertFalse(File(game.root, "meta.json.tmp").exists())
    }

    @Test
    fun `atomic write replaces existing content`() {
        val f = File(tmp, "a.txt")
        AtomicFiles.write(f, "one".toByteArray())
        AtomicFiles.write(f, "two".toByteArray())
        assertEquals("two", f.readText())
        assertFalse(File(tmp, "a.txt.tmp").exists())
    }

    @Test
    fun `delete removes the named game, refuses unsafe names and unknown folders`() {
        val lib = Library(tmp)
        val game = lib.beginImport(record()).commit()
        assertFalse(lib.delete("Nope [00000000]"))
        listOf("", ".", "..", "../x", "a/b", "a\\b").forEach { bad ->
            org.junit.jupiter.api.assertThrows<IllegalArgumentException>("name=$bad") { lib.delete(bad) }
        }
        assertTrue(lib.delete(game.name))
        assertFalse(game.root.exists())
        assertTrue(lib.scan().games.isEmpty())
        assertEquals(0, tmp.listFiles { f -> f.name.startsWith(Library.DELETING_PREFIX) }!!.size)
    }

    @Test
    fun `a half-finished deletion is finished by cleanup and never listed`() {
        val lib = Library(tmp)
        val leftover = File(tmp, "${Library.DELETING_PREFIX}abc").also { it.mkdir() }
        File(leftover, "game").mkdir()
        assertTrue(lib.scan().games.isEmpty())
        assertEquals(1, lib.cleanupAbandonedImports())
        assertFalse(leftover.exists())
    }

    @Test
    fun `deleteRecursively removes the whole game folder`() {
        val lib = Library(tmp)
        val game = lib.beginImport(record()).commit()
        File(game.save, "x.sav").writeText("s")
        assertTrue(game.deleteRecursively())
        assertTrue(lib.scan().games.isEmpty())
    }
}
