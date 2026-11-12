// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.core.database

import com.pocketverse.core.model.api.ContentHash
import com.pocketverse.core.model.api.EngineRef
import com.pocketverse.core.model.api.GameRecord
import com.pocketverse.core.model.api.HashMethod
import com.pocketverse.core.model.api.ImportInfo
import com.pocketverse.core.model.api.LaunchSpec
import com.pocketverse.core.model.api.Platform
import com.pocketverse.core.model.api.RomInfo
import com.pocketverse.core.model.api.SourceKind
import com.pocketverse.core.storage.api.Library
import com.pocketverse.core.storage.api.ScannedGame
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SyncPlannerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val library by lazy { Library(tmp.newFolder("lib")) }
    private var counter = 0

    /** A real game folder (so the planner sees real [ScannedGame]s); its name is "<title> [<id>]". */
    private fun game(title: String): ScannedGame {
        val prefix = "%08x".format(++counter)
        val record = GameRecord(
            id = prefix,
            title = title,
            platform = Platform.GBA,
            engine = EngineRef("e"),
            launch = LaunchSpec("x"),
            hash = ContentHash(prefix + "0".repeat(56), HashMethod.ROM),
            import = ImportInfo(Instant.EPOCH, "x", SourceKind.FILE, "0"),
            rom = RomInfo("AAAA", "T", 1),
        )
        val folder = library.beginImport(record).commit()
        return ScannedGame(folder, record)
    }

    @Test
    fun `new changed unchanged and vanished folders are told apart`() {
        val new = game("New")
        val changed = game("Changed")
        val same = game("Same")
        val stamps = mapOf(new to Stamp(1, 1), changed to Stamp(20, 5), same to Stamp(30, 6))
        val rows = listOf(
            SyncRow(changed.name, 10, 5), // older mtime
            SyncRow(same.name, 30, 6),
            SyncRow("Gone [00000099]", 1, 1),
        )

        val plan = SyncPlanner.plan(listOf(new, changed, same), stamps, rows)

        assertEquals(listOf(new.name), plan.added.map { it.name })
        assertEquals(listOf(changed.name), plan.updated.map { it.name })
        assertEquals(listOf("Gone [00000099]"), plan.removed)
    }

    @Test
    fun `a size change alone marks a row stale`() {
        val g = game("G")
        val plan = SyncPlanner.plan(listOf(g), mapOf(g to Stamp(10, 99)), listOf(SyncRow(g.name, 10, 5)))
        assertEquals(listOf(g.name), plan.updated.map { it.name })
    }
}
