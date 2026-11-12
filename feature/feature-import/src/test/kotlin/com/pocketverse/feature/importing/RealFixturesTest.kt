// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing

import com.pocketverse.core.model.api.Platform
import com.pocketverse.feature.importing.api.Detection
import com.pocketverse.feature.importing.api.FileSystemSource
import com.pocketverse.feature.importing.api.FormatDetector
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Runs the detector on the owner's own game files in the gitignored `DemoGames/` folder, to prove it works on
 * real, third-party ROMs (not only on our synthetic ones). Skipped when the files are not there. Nothing from
 * these files is copied into the repository.
 */
class RealFixturesTest {
    private val demo = File("../../DemoGames")

    @Test
    fun `a real gba rom hack is detected`() {
        val rom = File(demo, ".gba/Pokemon Mythic Legends/PK Mythic Legends Fixed v3.1.gba")
        assumeTrue(rom.isFile)
        val d = FormatDetector.detect(FileSystemSource(rom)) as Detection.Cartridge
        assertEquals(Platform.GBA, d.platform)
        assertEquals("BPRE", d.rom.gameCode) // the state file of this game names the same code
        assertEquals(rom.length(), d.rom.sizeBytes)
    }

    @Test
    fun `a real nds rom hack is detected`() {
        val rom = File(demo, ".nds/Pokemon Sacred Gold 1.05 -Complete/Pokemon Sacred Gold 1.05 -Complete.nds")
        assumeTrue(rom.isFile)
        val d = FormatDetector.detect(FileSystemSource(rom)) as Detection.Cartridge
        assertEquals(Platform.NDS, d.platform)
        assertEquals("IPKE", d.rom.gameCode)
        assertEquals("POKEMON HG", d.rom.headerTitle)
        assertEquals("Pokemon Sacred Gold 1.05 -Complete", d.suggestedTitle)
        println("real NDS warnings: ${d.warnings}")
    }
}
