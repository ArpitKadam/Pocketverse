// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing

import com.pocketverse.core.common.api.Hashing
import com.pocketverse.core.model.api.Platform
import com.pocketverse.core.model.api.SourceKind
import com.pocketverse.core.storage.api.Library
import com.pocketverse.feature.importing.api.CartridgeImporter
import com.pocketverse.feature.importing.api.Detection
import com.pocketverse.feature.importing.api.FileSystemSource
import com.pocketverse.feature.importing.api.FormatDetector
import com.pocketverse.feature.importing.api.GameSource
import com.pocketverse.feature.importing.api.ImportError
import com.pocketverse.feature.importing.api.ImportPhase
import com.pocketverse.feature.importing.api.ImportProgress
import com.pocketverse.feature.importing.api.ImportResult
import com.pocketverse.feature.importing.api.SourceEntry
import com.pocketverse.feature.importing.api.ZipSource
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CartridgeImporterTest {
    @TempDir
    lateinit var tmp: File

    private lateinit var library: Library
    private val now = Instant.parse("2026-11-12T10:00:00Z")

    @BeforeEach
    fun setUp() {
        library = Library(File(tmp, "lib"))
    }

    private fun importer(available: Long = Long.MAX_VALUE) = CartridgeImporter(library, "0.1.0", { now }, { available })

    private fun detect(source: GameSource) = FormatDetector.detect(source) as Detection.Cartridge

    private fun leftovers() = library.root.listFiles { f -> f.name.startsWith(Library.IMPORTING_PREFIX) }!!.size

    @Test
    fun `a gba rom is copied byte for byte, hashed, and described in meta json`() {
        val bytes = Fixtures.gba(title = "MY GAME", code = "BPRE", size = 0x4000)
        val source = MemorySource("My Game (USA).gba", "My Game (USA).gba" to bytes)
        val progress = mutableListOf<ImportProgress>()

        val result = importer().import(source, detect(source), onProgress = { progress += it }) as ImportResult.Imported

        val game = result.game
        assertArrayEquals(bytes, File(game.game, "My Game (USA).gba").readBytes())
        val meta = game.readMeta()
        val expected = Hashing.romHashes(bytes.inputStream())
        assertEquals(expected.sha256, meta.hash.sha256)
        assertEquals(expected.crc32, meta.hash.crc32)
        assertEquals(expected.sha256.take(8), meta.id)
        assertEquals("My Game (USA)", meta.title)
        assertEquals(Platform.GBA, meta.platform)
        assertEquals("My Game (USA).gba", meta.launch.file)
        assertEquals("libretro-mgba", meta.engine.id)
        assertEquals("BPRE", meta.rom!!.gameCode)
        assertEquals(now, meta.import.importedAt)
        assertEquals("0.1.0", meta.import.appVersion)
        assertEquals(SourceKind.FILE, meta.import.sourceKind)
        assertEquals("My Game (USA) [${meta.id}]", game.name)
        assertEquals(0, leftovers())
        assertEquals(listOf(ImportPhase.CHECKING_SPACE, ImportPhase.COPYING, ImportPhase.FINALIZING), progress.map { it.phase }.distinct())
        assertEquals(bytes.size.toLong(), progress.last { it.phase == ImportPhase.COPYING }.bytesDone)
    }

    @Test
    fun `an nds rom imports with its engine and a header warning passes through`() {
        val bytes = Fixtures.nds(goodCrc = false)
        val source = MemorySource("hack.nds", "hack.nds" to bytes)
        val result = importer().import(source, detect(source)) as ImportResult.Imported
        assertEquals("libretro-melonds", result.game.readMeta().engine.id)
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun `importing the same rom twice reports AlreadyInLibrary and leaves no scratch behind`() {
        val source = MemorySource("a.gba", "a.gba" to Fixtures.gba())
        val first = importer().import(source, detect(source)) as ImportResult.Imported
        val second = importer().import(MemorySource("renamed.gba", "renamed.gba" to Fixtures.gba()), detect(MemorySource("renamed.gba", "renamed.gba" to Fixtures.gba())))
        assertTrue(second is ImportResult.AlreadyInLibrary)
        assertEquals(first.game.name, (second as ImportResult.AlreadyInLibrary).existing.name)
        assertEquals(0, leftovers())
        assertEquals(1, library.scan().games.size)
    }

    @Test
    fun `cancelling in the middle of a copy removes everything`() {
        val big = Fixtures.gba(size = 3 * 1024 * 1024)
        val source = MemorySource("big.gba", "big.gba" to big)
        var cancel = false
        val result = importer().import(
            source,
            detect(source),
            shouldCancel = { cancel },
            onProgress = { if (it.phase == ImportPhase.COPYING && it.bytesDone > 0) cancel = true },
        )
        assertEquals(ImportResult.Cancelled, result)
        assertEquals(0, leftovers())
        assertTrue(library.scan().games.isEmpty())
    }

    @Test
    fun `not enough free space fails before anything is written`() {
        val source = MemorySource("a.gba", "a.gba" to Fixtures.gba())
        val result = importer(available = 100).import(source, detect(source)) as ImportResult.Failed
        val err = result.error as ImportError.InsufficientSpace
        assertEquals(100, err.availableBytes)
        assertTrue(err.requiredBytes > 0x400)
        assertEquals(0, leftovers())
    }

    @Test
    fun `a source that cannot be read fails cleanly`() {
        val bytes = Fixtures.gba()
        val broken = object : GameSource {
            override val displayName = "broken.gba"
            override fun entries() = listOf(SourceEntry("broken.gba", bytes.size.toLong()))
            override fun open(path: String): InputStream = object : InputStream() {
                override fun read(): Int = throw IOException("card removed")
                override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("card removed")
            }
        }
        val detection = Detection.Cartridge(Platform.GBA, com.pocketverse.core.model.api.EngineRef("libretro-mgba"), "broken.gba", FormatDetector.detect(MemorySource("x", "x.gba" to bytes)).let { (it as Detection.Cartridge).rom }, "broken")
        val result = importer().import(broken, detection) as ImportResult.Failed
        assertEquals(ImportError.SourceUnreadable("card removed"), result.error)
        assertEquals(0, leftovers())
    }

    @Test
    fun `a file that is shorter than detection saw is reported as changed`() {
        val bytes = Fixtures.gba(size = 0x800)
        val shrinking = object : GameSource {
            override val displayName = "s.gba"
            override fun entries() = listOf(SourceEntry("s.gba", bytes.size.toLong()))
            override fun open(path: String): InputStream = bytes.copyOf(0x400).inputStream()
        }
        val detection = detect(MemorySource("s.gba", "s.gba" to bytes))
        val result = importer().import(shrinking, detection) as ImportResult.Failed
        assertEquals(ImportError.SourceChanged(0x800, 0x400), result.error)
        assertEquals(0, leftovers())
        assertTrue(library.scan().games.isEmpty())
    }

    @Test
    fun `a source that streams more than it declared is stopped, not written to disk`() {
        val bytes = Fixtures.gba(size = 0x800)
        var served = 0L
        val lying = object : GameSource {
            override val displayName = "bomb.gba"
            override fun entries() = listOf(SourceEntry("bomb.gba", bytes.size.toLong()))
            // The header is genuine, then an endless stream of data after it.
            override fun open(path: String): InputStream = object : InputStream() {
                private var pos = 0
                override fun read(): Int = throw UnsupportedOperationException()
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val n = minOf(len, 64 * 1024)
                    for (i in 0 until n) b[off + i] = if (pos + i < bytes.size) bytes[pos + i] else 0
                    pos += n
                    served += n
                    return n
                }
            }
        }
        val detection = detect(MemorySource("bomb.gba", "bomb.gba" to bytes))
        val result = importer().import(lying, detection) as ImportResult.Failed
        val err = result.error as ImportError.SourceChanged
        assertEquals(0x800L, err.expectedBytes)
        assertTrue(err.readBytes > 0x800)
        assertTrue(served <= 128 * 1024, "stopped after one extra chunk, served $served")
        assertEquals(0, leftovers())
        assertTrue(library.scan().games.isEmpty())
    }

    @Test
    fun `awkward file names are made safe`() {
        assertEquals("a_b_.gba", CartridgeImporter.safeFileName("a:b?.gba", Platform.GBA))
        assertEquals("rom.nds", CartridgeImporter.safeFileName("...", Platform.NDS))
        assertEquals("rom.gba", CartridgeImporter.safeFileName("", Platform.GBA))
    }

    @Test
    fun `a rom inside a zip is imported and the source is recorded as an archive`() {
        val bytes = Fixtures.gba(title = "ZIPPED", size = 0x2000)
        val zip = File(tmp, "bundle.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("readme.txt")); z.write("hi".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("roms/Zipped Game.gba")); z.write(bytes); z.closeEntry()
            z.putNextEntry(ZipEntry("../evil.gba")); z.write(Fixtures.gba(code = "EVIL")); z.closeEntry()
        }
        ZipSource(zip).use { source ->
            assertEquals(setOf("readme.txt", "roms/Zipped Game.gba"), source.entries().map { it.path }.toSet())
            val result = importer().import(source, detect(source)) as ImportResult.Imported
            assertArrayEquals(bytes, File(result.game.game, "Zipped Game.gba").readBytes())
            val meta = result.game.readMeta()
            assertEquals(SourceKind.ARCHIVE, meta.import.sourceKind)
            assertEquals("bundle.zip", meta.import.originalName)
            assertEquals("Zipped Game", meta.title)
        }
    }

    @Test
    fun `the real nds rom is imported intact when the owner's files are present`() {
        val rom = File("../../DemoGames/.nds/Pokemon Sacred Gold 1.05 -Complete/Pokemon Sacred Gold 1.05 -Complete.nds")
        assumeTrue(rom.isFile)
        val source = FileSystemSource(rom)
        val result = importer().import(source, detect(source)) as ImportResult.Imported
        val copy = File(result.game.game, rom.name)
        assertEquals(rom.length(), copy.length())
        val expected = rom.inputStream().use { Hashing.romHashes(it) }
        assertEquals(expected.sha256, result.game.readMeta().hash.sha256)
        assertFalse(File(result.game.root, "rom.tmp").exists())
    }
}
