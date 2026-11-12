// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing

import com.pocketverse.core.model.api.HashMethod
import com.pocketverse.core.model.api.Platform
import com.pocketverse.core.model.api.SourceKind
import com.pocketverse.core.storage.api.Library
import com.pocketverse.feature.importing.api.Detection
import com.pocketverse.feature.importing.api.FileSystemSource
import com.pocketverse.feature.importing.api.FolderGameImporter
import com.pocketverse.feature.importing.api.FormatDetector
import com.pocketverse.feature.importing.api.GameSource
import com.pocketverse.feature.importing.api.ImportError
import com.pocketverse.feature.importing.api.ImportLimits
import com.pocketverse.feature.importing.api.ImportPhase
import com.pocketverse.feature.importing.api.ImportResult
import com.pocketverse.feature.importing.api.ManifestHash
import com.pocketverse.feature.importing.api.SourceEntry
import java.io.File
import java.io.InputStream
import java.time.Instant
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FolderImportTest {
    @TempDir
    lateinit var tmp: File

    private lateinit var library: Library
    private val now = Instant.parse("2026-11-12T10:00:00Z")

    @BeforeEach
    fun setUp() {
        library = Library(File(tmp, "lib"))
    }

    private fun importer(available: Long = Long.MAX_VALUE, limits: ImportLimits = ImportLimits()) = FolderGameImporter(library, "0.1.0", { now }, { available }, limits)
    private fun detect(s: GameSource) = FormatDetector.detect(s) as Detection.FolderGame
    private fun leftovers() = library.root.listFiles { f -> f.name.startsWith(Library.IMPORTING_PREFIX) }!!.size
    private fun game(prefix: String = "") = MemorySource("My Download", ArchiveFixtures.rpgMakerXp(prefix, "Folder Game"))

    // ------------------------------------------------------------------ manifest hash
    @Test
    fun `manifest hash ignores order and case and generated files but notices real changes`() {
        val base = listOf(SourceEntry("Game.ini", 5), SourceEntry("Data/A.rxdata", 9))
        val h = ManifestHash.of(base)
        assertEquals(h, ManifestHash.of(base.reversed()))
        assertEquals(h, ManifestHash.of(listOf(SourceEntry("GAME.INI", 5), SourceEntry("data/a.rxdata", 9))))
        assertEquals(h, ManifestHash.of(base + listOf(SourceEntry("logs.txt", 99), SourceEntry("UserData/AppData/save", 1), SourceEntry("saves/x", 1), SourceEntry("cache/y", 1), SourceEntry(".online_path_cache", 3))))
        assertNotEquals(h, ManifestHash.of(listOf(SourceEntry("Game.ini", 6), SourceEntry("Data/A.rxdata", 9))))
        assertNotEquals(h, ManifestHash.of(base + SourceEntry("extra.txt", 1)))
        assertEquals(64, h.length)
    }

    // ------------------------------------------------------------------ copy
    @Test
    fun `a folder game is copied whole, with the wrapper stripped, and described by meta json`() {
        val src = game("Wrapper/")
        val detection = detect(src)
        assertEquals("Wrapper/", detection.rootPrefix)
        val progress = mutableListOf<ImportPhase>()

        val result = importer().import(src, detection, onProgress = { progress += it.phase }) as ImportResult.Imported

        val g = result.game
        assertTrue(File(g.game, "Game.ini").isFile)
        assertArrayEquals(ByteArray(40) { it.toByte() }, File(g.game, "Data/Scripts.rxdata").readBytes())
        assertFalse(File(g.game, "Wrapper").exists())
        val meta = g.readMeta()
        assertEquals("Folder Game", meta.title)
        assertEquals(Platform.RPGMAKER, meta.platform)
        assertEquals("mkxp-z", meta.engine.id)
        assertEquals("rgss1", meta.engine.variant)
        assertEquals("Game.ini", meta.launch.file)
        assertEquals(HashMethod.MANIFEST, meta.hash.method)
        assertEquals(SourceKind.FILE, meta.import.sourceKind)
        assertEquals("rgss1", meta.raw["x"]!!.jsonObject["detection"]!!.jsonObject["hints"]!!.jsonArray[0].jsonPrimitive.content)
        assertEquals(0, leftovers())
        assertTrue(ImportPhase.COPYING in progress && ImportPhase.FINALIZING in progress)
    }

    @Test
    fun `a duplicate is found from the file list alone, before any file is opened`() {
        importer().import(game(), detect(game())) as ImportResult.Imported
        var opened = 0
        val again = object : GameSource by game() {
            override fun open(path: String): InputStream { opened++; return game().open(path) }
        }
        val r = importer().import(again, detect(game()))
        assertTrue(r is ImportResult.AlreadyInLibrary)
        assertEquals(0, opened)
        assertEquals(0, leftovers())
    }

    @Test
    fun `cancel, no space, limits and a lying source leave nothing behind`() {
        val src = game()
        val d = detect(src)

        assertEquals(ImportResult.Cancelled, importer().import(src, d, shouldCancel = { true }))
        assertTrue((importer(available = 10).import(src, d) as ImportResult.Failed).error is ImportError.InsufficientSpace)
        assertTrue((importer(limits = ImportLimits(maxEntries = 2)).import(src, d) as ImportResult.Failed).error is ImportError.LimitExceeded)
        assertTrue((importer(limits = ImportLimits(maxEntryBytes = 5)).import(src, d) as ImportResult.Failed).error is ImportError.LimitExceeded)

        val lying = object : GameSource by src {
            // Declares 10 bytes for Game.exe but streams 1 MiB.
            override fun open(path: String): InputStream = if (path == "Game.exe") ByteArray(1024 * 1024).inputStream() else src.open(path)
        }
        val r = importer().import(lying, d) as ImportResult.Failed
        assertTrue(r.error is ImportError.SourceChanged)
        assertEquals(0, leftovers())
        assertTrue(library.scan().games.isEmpty())
    }

    @Test
    fun `a name that would escape the game folder is refused`() {
        val src = MemorySource("x", "Game.ini" to ByteArray(1), "Data/a.rxdata" to ByteArray(1), "../outside.txt" to ByteArray(1))
        val d = Detection.FolderGame(Platform.RPGMAKER, com.pocketverse.core.model.api.EngineRef("mkxp-z"), "", "Game.ini", "x")
        val r = importer().import(src, d) as ImportResult.Failed
        assertTrue(r.error is ImportError.SourceUnreadable)
        assertEquals(0, leftovers())
    }

    // ------------------------------------------------------------------ adopt (move instead of copy)
    @Test
    fun `an unpacked folder is moved into place without copying`() {
        val unpacked = File(tmp, "unpacked").also { it.mkdirs() }
        ArchiveFixtures.rpgMakerXp("Wrapper/").forEach { (p, b) -> File(unpacked, p).also { it.parentFile.mkdirs() }.writeBytes(b) }
        val src = FileSystemSource(unpacked)
        val d = detect(src)

        val r = importer().import(src, d, adoptFrom = unpacked) as ImportResult.Imported

        assertTrue(File(r.game.game, "Game.ini").isFile)
        assertFalse(File(unpacked, "Wrapper").exists(), "the folder was moved, not copied")
        assertEquals(0, leftovers())
    }
}
