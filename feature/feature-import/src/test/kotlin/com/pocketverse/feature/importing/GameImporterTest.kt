// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing

import com.pocketverse.core.common.api.Hashing
import com.pocketverse.core.model.api.Platform
import com.pocketverse.core.model.api.SourceKind
import com.pocketverse.core.storage.api.Library
import com.pocketverse.feature.importing.api.ArchivePasswords
import com.pocketverse.feature.importing.api.Detection
import com.pocketverse.feature.importing.api.GameImporter
import com.pocketverse.feature.importing.api.ImportError
import com.pocketverse.feature.importing.api.ImportResult
import java.io.File
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class GameImporterTest {
    @TempDir
    lateinit var tmp: File

    private lateinit var library: Library
    private lateinit var importer: GameImporter

    @BeforeEach
    fun setUp() {
        library = Library(File(tmp, "lib"))
        importer = GameImporter(library, "0.1.0", { Instant.parse("2026-11-12T10:00:00Z") }, { Long.MAX_VALUE })
    }

    private fun leftovers() = library.root.listFiles { f -> f.name.startsWith(Library.IMPORTING_PREFIX) }!!.size

    @Test
    fun `a zipped rpg maker game is unpacked, recognised, and moved into the library`() {
        val zip = ArchiveFixtures.zip(File(tmp, "My Fan Game.zip"), ArchiveFixtures.rpgMakerXp("Fan Game v1/", "Fan Game"))
        val r = importer.import(zip) as ImportResult.Imported
        val meta = r.game.readMeta()
        assertEquals("Fan Game", meta.title)
        assertEquals(SourceKind.ARCHIVE, meta.import.sourceKind)
        assertEquals("My Fan Game.zip", meta.import.originalName)
        assertTrue(File(r.game.game, "Game.ini").isFile)
        assertFalse(File(r.game.game, "Fan Game v1").exists(), "wrapper folder stripped")
        assertEquals(0, leftovers())
    }

    @Test
    fun `a 7z and a tar gz with a game folder import the same way`() {
        val tree = ArchiveFixtures.rpgMakerXp("", "From 7z")
        val a = importer.import(ArchiveFixtures.sevenZ(File(tmp, "a.7z"), tree)) as ImportResult.Imported
        assertEquals("From 7z", a.game.readMeta().title)
        val b = importer.import(ArchiveFixtures.tar(File(tmp, "b.tar.gz"), ArchiveFixtures.rpgMakerXp("", "From tar"), gzip = true)) as ImportResult.Imported
        assertEquals("From tar", b.game.readMeta().title)
        assertEquals(2, library.scan().games.size)
    }

    @Test
    fun `a zipped rom is imported as a cartridge`() {
        val rom = Fixtures.gba(title = "ZIPROM", code = "ZZZE", size = 0x1000)
        val r = importer.import(ArchiveFixtures.zip(File(tmp, "bundle.zip"), mapOf("readme.txt" to ByteArray(3), "Zipped Rom.gba" to rom))) as ImportResult.Imported
        assertEquals(Platform.GBA, r.game.readMeta().platform)
        assertArrayEquals(rom, File(r.game.game, "Zipped Rom.gba").readBytes())
        assertEquals(SourceKind.ARCHIVE, r.game.readMeta().import.sourceKind)
    }

    @Test
    fun `two roms in one archive need a choice and then import the chosen one`() {
        val zip = ArchiveFixtures.zip(File(tmp, "pack.zip"), mapOf("a.gba" to Fixtures.gba(code = "AAAA"), "b.gba" to Fixtures.gba(code = "BBBB", title = "OTHER")))
        val need = importer.import(zip) as ImportResult.NeedsChoice
        assertEquals(setOf("a.gba", "b.gba"), need.candidates.map { it.romPath }.toSet())
        assertEquals(0, leftovers())

        val done = importer.import(zip, preferredRom = "b.gba") as ImportResult.Imported
        assertEquals("BBBB", done.game.readMeta().rom!!.gameCode)
    }

    @Test
    fun `an encrypted archive uses the password hook`() {
        val zip = ArchiveFixtures.encryptedZip(File(tmp, "locked.zip"), "pw", ArchiveFixtures.rpgMakerXp("", "Locked"))
        val denied = importer.import(zip) as ImportResult.Failed
        assertTrue(denied.error is ImportError.PasswordRequired)
        assertEquals(0, leftovers())
        val ok = importer.import(zip, passwords = ArchivePasswords { _, _ -> "pw".toCharArray() }) as ImportResult.Imported
        assertEquals("Locked", ok.game.readMeta().title)
    }

    @Test
    fun `rar, unknown content and missing files are reported without importing anything`() {
        val rar = File(tmp, "g.rar").also { it.writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00) + ByteArray(600)) }
        assertEquals(ImportError.UnsupportedArchive("RAR"), (importer.import(rar) as ImportResult.Failed).error)
        assertTrue(importer.import(File(tmp, "missing.zip")) is ImportResult.Failed)

        val junkZip = ArchiveFixtures.zip(File(tmp, "junk.zip"), mapOf("a.txt" to ByteArray(3), "b.txt" to ByteArray(3)))
        val rejected = importer.import(junkZip) as ImportResult.Rejected
        assertEquals(Detection.Reason.UNRECOGNISED, rejected.rejection.reason)
        assertEquals(0, leftovers())
        assertTrue(library.scan().games.isEmpty())
    }

    @Test
    fun `a plain folder and a plain rom file import without any unpacking`() {
        val dir = File(tmp, "RPG Folder").also { it.mkdirs() }
        ArchiveFixtures.rpgMakerXp("", "Plain Folder").forEach { (p, b) -> File(dir, p).also { it.parentFile.mkdirs() }.writeBytes(b) }
        val folder = importer.import(dir) as ImportResult.Imported
        assertEquals(SourceKind.FOLDER, folder.game.readMeta().import.sourceKind)
        assertTrue(File(dir, "Game.ini").isFile, "the user's own folder is copied, not moved")

        val rom = File(tmp, "x.gba").also { it.writeBytes(Fixtures.gba()) }
        assertTrue(importer.import(rom) is ImportResult.Imported)
    }

    /**
     * End-to-end proof on a real 2.6 GB archive with 26,778 files (the owner's Infinite Fusion zip). Slow (minutes
     * and about 3 GB of disk), so it only runs when POCKETVERSE_SLOW_TESTS=1 and the file is present.
     */
    @Test
    fun `the real 2_6 GB rpg maker archive imports end to end`() {
        assumeTrue(System.getenv("POCKETVERSE_SLOW_TESTS") == "1")
        val zip = File("../../DemoGames/.exe/Infinite Fusion/InfiniteFusionAndroid.zip")
        assumeTrue(zip.isFile)
        val started = System.nanoTime()
        val r = importer.import(zip) as ImportResult.Imported
        val seconds = (System.nanoTime() - started) / 1_000_000_000
        val meta = r.game.readMeta()
        println("real archive imported in ${seconds}s: title='${meta.title}' engine=${meta.engine} launch=${meta.launch.file} id=${meta.id}")
        assertEquals(Platform.RPGMAKER, meta.platform)
        assertTrue(File(r.game.game, "Game.ini").isFile)
        assertTrue(File(r.game.game, "Data").isDirectory)
        assertEquals(0, leftovers())
        println("manifest hash ${Hashing.sha256Hex(meta.hash.sha256.toByteArray()).take(8)} (stable id ${meta.id})")
    }
}
