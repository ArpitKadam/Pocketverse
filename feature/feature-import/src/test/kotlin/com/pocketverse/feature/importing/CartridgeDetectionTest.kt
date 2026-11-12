// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing

import com.pocketverse.core.model.api.Platform
import com.pocketverse.feature.importing.api.Detection
import com.pocketverse.feature.importing.api.FormatDetector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CartridgeDetectionTest {
    private fun detectFile(name: String, bytes: ByteArray) = FormatDetector.detect(MemorySource(name, name to bytes))

    @Test
    fun `crc16 matches the standard check value`() {
        assertEquals(0x4B37, Crc16.compute("123456789".toByteArray(), 0, 9))
    }

    @Test
    fun `a gba image is recognised by its header whatever the file is called`() {
        val d = detectFile("mystery.dat", Fixtures.gba(title = "MY GAME", code = "BPRE"))
        d as Detection.Cartridge
        assertEquals(Platform.GBA, d.platform)
        assertEquals("libretro-mgba", d.engine.id)
        assertEquals("BPRE", d.rom.gameCode)
        assertEquals("MY GAME", d.rom.headerTitle)
        assertEquals("USA", d.rom.region)
        assertEquals("mystery", d.suggestedTitle)
        assertEquals(0x400L, d.rom.sizeBytes)
        assertTrue(d.warnings.isEmpty())
    }

    @Test
    fun `a gba image with a wrong checksum is accepted with a warning`() {
        val d = detectFile("hack.gba", Fixtures.gba(goodChecksum = false)) as Detection.Cartridge
        assertEquals(1, d.warnings.size)
        assertTrue(d.warnings.single().contains("checksum"))
    }

    @Test
    fun `without the fixed byte a gba lookalike is rejected`() {
        val d = detectFile("x.gba", Fixtures.gba(fixedByte = 0x00))
        assertTrue(d is Detection.Rejected)
        assertEquals(Detection.Reason.UNRECOGNISED, (d as Detection.Rejected).reason)
    }

    @Test
    fun `an nds image with a good header crc has no warnings`() {
        val d = detectFile("game.nds", Fixtures.nds(title = "TEST NDS", code = "IPKE")) as Detection.Cartridge
        assertEquals(Platform.NDS, d.platform)
        assertEquals("libretro-melonds", d.engine.id)
        assertEquals("IPKE", d.rom.gameCode)
        assertEquals(0, d.rom.unitCode)
        assertEquals(false, d.rom.dsiEnhanced)
        assertTrue(d.warnings.isEmpty())
    }

    @Test
    fun `an nds image with a wrong header crc is accepted with a warning`() {
        val d = detectFile("hack.nds", Fixtures.nds(goodCrc = false)) as Detection.Cartridge
        assertTrue(d.warnings.single().contains("CRC"))
    }

    @Test
    fun `dsi enhanced and dsi only titles are flagged`() {
        val enhanced = detectFile("a.nds", Fixtures.nds(unitCode = 2)) as Detection.Cartridge
        assertEquals(true, enhanced.rom.dsiEnhanced)
        val dsiOnly = detectFile("b.nds", Fixtures.nds(unitCode = 3)) as Detection.Cartridge
        assertTrue(dsiOnly.warnings.any { it.contains("DSi-exclusive") })
    }

    @Test
    fun `an unknown unit code is not an nds header`() {
        assertTrue(detectFile("x.nds", Fixtures.nds(unitCode = 7)) is Detection.Rejected)
    }

    @Test
    fun `garbage and truncated files are rejected, never crash`() {
        assertTrue(detectFile("noise.bin", ByteArray(4096) { (it * 31 + 7).toByte() }) is Detection.Rejected)
        assertTrue(detectFile("tiny.gba", ByteArray(100)) is Detection.Rejected)
        assertTrue(detectFile("zeros.nds", ByteArray(0x800)) is Detection.Rejected)
    }

    @Test
    fun `a windows program, an archive and an empty source get their own reasons`() {
        assertEquals(Detection.Reason.UNSUPPORTED_WINDOWS_PROGRAM, (detectFile("Setup.EXE", ByteArray(500)) as Detection.Rejected).reason)
        assertEquals(Detection.Reason.ARCHIVE_NOT_OPENED, (detectFile("game.zip", ByteArray(500)) as Detection.Rejected).reason)
        assertEquals(Detection.Reason.EMPTY, (FormatDetector.detect(MemorySource("nothing")) as Detection.Rejected).reason)
    }

    @Test
    fun `one rom among other files in a folder is found`() {
        val src = MemorySource(
            "dump",
            "readme.txt" to Fixtures.text("hello"),
            "Roms/Cool Game (USA).gba" to Fixtures.gba(),
        )
        val d = FormatDetector.detect(src) as Detection.Cartridge
        assertEquals("Roms/Cool Game (USA).gba", d.romPath)
        assertEquals("Cool Game (USA)", d.suggestedTitle)
    }

    @Test
    fun `two valid roms are reported as ambiguous, with both candidates`() {
        val src = MemorySource("pack", "a.gba" to Fixtures.gba(code = "AAAA"), "b.nds" to Fixtures.nds(code = "BBBB"))
        val d = FormatDetector.detect(src)
        assertTrue(d is Detection.Ambiguous)
        assertEquals(setOf("AAAA", "BBBB"), (d as Detection.Ambiguous).candidates.map { it.rom.gameCode }.toSet())
    }

    @Test
    fun `inside a folder only rom-like extensions are opened, a rom named txt is not found`() {
        val src = MemorySource("x", "notes.txt" to Fixtures.gba(), "other.txt" to Fixtures.text("x"))
        assertFalse(FormatDetector.detect(src) is Detection.Cartridge)
        assertNull((FormatDetector.detect(src) as? Detection.Cartridge))
    }
}
