// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing

import com.pocketverse.core.model.api.Platform
import com.pocketverse.feature.importing.api.Detection
import com.pocketverse.feature.importing.api.FormatDetector
import com.pocketverse.feature.importing.api.FileSystemSource
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FolderGameDetectionTest {
    private val none = ByteArray(0)
    private fun src(name: String, vararg files: Pair<String, ByteArray>) = MemorySource(name, *files)
    private fun folder(d: Detection) = d as Detection.FolderGame

    @Test
    fun `rpg maker xp is recognised by Game ini plus rxdata and the title comes from the ini`() {
        val d = folder(FormatDetector.detect(src("dl", "Game.ini" to Fixtures.text("[Game]\nLibrary=RGSS104E.dll\nTitle=My Game\n"), "Game.exe" to none, "Data/Scripts.rxdata" to none)))
        assertEquals(Platform.RPGMAKER, d.platform)
        assertEquals("mkxp-z", d.engine.id)
        assertEquals("rgss1", d.engine.variant)
        assertEquals("Game.ini", d.launchFile)
        assertEquals("My Game", d.suggestedTitle)
        assertEquals("", d.rootPrefix)
    }

    @Test
    fun `vx and vx ace and encrypted archives pick the right generation`() {
        fun variant(vararg f: String) = folder(FormatDetector.detect(MemorySource("x", f.associateWith { none }))).engine.variant
        assertEquals("rgss2", variant("Game.ini", "Data/Actors.rvdata"))
        assertEquals("rgss3", variant("Game.ini", "Data/Actors.rvdata2"))
        assertEquals("rgss3", variant("Game.ini", "Game.rgss3a"))
        assertEquals("rgss2", variant("Game.ini", "Game.rgss2a"))
        assertEquals("rgss1", variant("Game.ini", "Game.rgssad"))
    }

    @Test
    fun `file names are matched without regard to case`() {
        val d = folder(FormatDetector.detect(src("x", "GAME.INI" to none, "DATA/Map001.RXDATA" to none)))
        assertEquals("GAME.INI", d.launchFile)
    }

    @Test
    fun `pokemon essentials is hinted when PBS data is present`() {
        val d = folder(FormatDetector.detect(src("fangame", "Game.ini" to none, "Data/Scripts.rxdata" to none, "PBS/pokemon.txt" to none)))
        assertTrue("pokemon-essentials" in d.hints)
    }

    @Test
    fun `a Game ini and an exe alone are not enough and get the windows program reason`() {
        val d = FormatDetector.detect(src("x", "Game.ini" to none, "Game.exe" to none, "readme.txt" to none)) as Detection.Rejected
        assertEquals(Detection.Reason.UNSUPPORTED_WINDOWS_PROGRAM, d.reason)
    }

    @Test
    fun `a single wrapper folder is stripped and ignorable junk does not hide it`() {
        val d = folder(
            FormatDetector.detect(
                src(
                    "download.zip",
                    "Pokemon Fan Game/Game.ini" to Fixtures.text("Title=Fan Game"),
                    "Pokemon Fan Game/Data/Scripts.rxdata" to none,
                    "__MACOSX/Pokemon Fan Game/._Game.ini" to none,
                    ".DS_Store" to none,
                ),
            ),
        )
        assertEquals("Pokemon Fan Game/", d.rootPrefix)
        assertEquals("Fan Game", d.suggestedTitle)
        assertEquals("Game.ini", d.launchFile)
    }

    @Test
    fun `the wrapper folder name is the fallback title`() {
        val d = folder(FormatDetector.detect(src("x.zip", "Cool Game/Game.ini" to none, "Cool Game/Data/a.rvdata2" to none)))
        assertEquals("Cool Game", d.suggestedTitle)
    }

    @Test
    fun `rpg maker mv with a www folder and mz at the root`() {
        val mv = folder(FormatDetector.detect(src("mv", "www/index.html" to none, "www/js/rpg_core.js" to none, "www/data/System.json" to Fixtures.text("{\"gameTitle\":\"Web Quest\"}"), "package.json" to none)))
        assertEquals(Platform.RPGMAKER_MV, mv.platform)
        assertEquals("rpgmv", mv.engine.id)
        assertEquals("mv", mv.engine.variant)
        assertEquals("www/index.html", mv.launchFile)
        assertEquals("Web Quest", mv.suggestedTitle)

        val mz = folder(FormatDetector.detect(src("mz", "index.html" to none, "js/rmmz_core.js" to none, "data/System.json" to Fixtures.text("{ \"gameTitle\": \"Zed\" }"))))
        assertEquals("mz", mz.engine.variant)
        assertEquals("index.html", mz.launchFile)
        assertEquals("Zed", mz.suggestedTitle)
    }

    @Test
    fun `renpy is recognised by scripts under game, the launcher and title are picked up`() {
        val bare = folder(FormatDetector.detect(src("vn", "game/script.rpy" to none, "game/gui.rpy" to none)))
        assertEquals(Platform.RENPY, bare.platform)
        assertEquals("game", bare.launchFile)

        val full = folder(
            FormatDetector.detect(
                src(
                    "vn",
                    "MyVN.py" to none, "MyVN.sh" to none, "renpy/__init__.py" to none, "lib/x" to none,
                    "game/options.rpy" to Fixtures.text("define config.name = _(\"My Visual Novel\")\n"),
                    "game/script.rpyc" to none,
                ),
            ),
        )
        assertEquals("MyVN.py", full.launchFile)
        assertEquals("My Visual Novel", full.suggestedTitle)
        assertTrue(full.hints.containsAll(listOf("renpy-runtime-folder", "lib-folder")))
    }

    @Test
    fun `two engines in one folder are reported as a conflict`() {
        val d = FormatDetector.detect(src("x", "Game.ini" to none, "Data/a.rxdata" to none, "game/script.rpy" to none)) as Detection.Rejected
        assertEquals(Detection.Reason.CONFLICTING_ENGINES, d.reason)
    }

    @Test
    fun `an unrecognised folder is rejected with a bounded file sample`() {
        val files = (1..120).associate { "file$it.dat" to none }
        val d = FormatDetector.detect(MemorySource("junk", files)) as Detection.Rejected
        assertEquals(Detection.Reason.UNRECOGNISED, d.reason)
        assertEquals(50, d.fileSample.size)
    }

    @Test
    fun `the filesystem source lists relative paths and detects a real folder`(@TempDir dir: File) {
        File(dir, "Data").mkdirs()
        File(dir, "Game.ini").writeText("Title=On Disk\n")
        File(dir, "Data/Scripts.rxdata").writeBytes(ByteArray(3))
        val source = FileSystemSource(dir)
        assertEquals(setOf("Game.ini", "Data/Scripts.rxdata"), source.entries().map { it.path }.toSet())
        assertEquals("On Disk", folder(FormatDetector.detect(source)).suggestedTitle)

        val rom = File(dir, "Game.gba").also { it.writeBytes(Fixtures.gba()) }
        val single = FileSystemSource(rom)
        assertEquals(listOf("Game.gba"), single.entries().map { it.path })
        assertTrue(FormatDetector.detect(single) is Detection.Cartridge)
    }
}
