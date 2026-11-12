// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.library.importing

import com.pocketverse.core.model.api.Platform
import com.pocketverse.core.model.api.SourceKind
import com.pocketverse.core.storage.api.Library
import com.pocketverse.feature.importing.api.GameImporter
import com.pocketverse.feature.importing.api.ImportLimits
import com.pocketverse.feature.importing.api.ImportResult
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** An in-memory folder tree standing in for a document provider. Paths are like "Data/Scripts.rxdata". */
private class FakeTree(files: Map<String, ByteArray>, override val rootName: String = "Picked") : DocTree {
    private val nodes = mutableMapOf<String, DocNode>()
    private val children = mutableMapOf<String?, MutableList<DocNode>>()
    private val content = mutableMapOf<String, ByteArray>()

    init {
        files.forEach { (path, bytes) ->
            val parts = path.split('/')
            var parentId: String? = null
            for ((i, part) in parts.withIndex()) {
                val id = parts.take(i + 1).joinToString("/")
                val isFile = i == parts.lastIndex
                val node = nodes.getOrPut(id) {
                    DocNode(id, part, !isFile, if (isFile) bytes.size.toLong() else -1).also { children.getOrPut(parentId) { mutableListOf() }.add(it) }
                }
                if (isFile) content[id] = bytes
                parentId = node.id
            }
        }
    }

    override fun children(parent: DocNode?): List<DocNode> = children[parent?.id].orEmpty()
    override fun open(node: DocNode): InputStream = ByteArrayInputStream(content.getValue(node.id))
}

class TreeSourceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val rpg = mapOf(
        "Game.ini" to "[Game]\nTitle=Tree Game\n".toByteArray(),
        "Data/Scripts.rxdata" to ByteArray(30) { it.toByte() },
        "Graphics/Pictures/a.png" to ByteArray(5),
    )

    @Test
    fun `a picked tree is listed with relative paths and sizes, and files open by path`() {
        val source = TreeSource(FakeTree(rpg))
        assertEquals("Picked", source.displayName)
        assertEquals(SourceKind.FOLDER, source.kind)
        assertEquals(rpg.keys, source.entries().map { it.path }.toSet())
        assertEquals(30L, source.entries().first { it.path == "Data/Scripts.rxdata" }.size)
        assertArrayEquals(rpg.getValue("Game.ini"), source.open("Game.ini").readBytes())
    }

    @Test
    fun `an unknown path is an error, not a crash`() {
        try {
            TreeSource(FakeTree(rpg)).open("nope.txt")
            fail("expected IOException")
        } catch (e: java.io.IOException) {
            assertTrue(e.message!!.contains("nope.txt"))
        }
    }

    @Test
    fun `listing stops at the entry limit before anything is read`() {
        val many = (1..50).associate { "d${it % 5}/f$it.txt" to ByteArray(1) }
        try {
            TreeSource(FakeTree(many), ImportLimits(maxEntries = 20)).entries()
            fail("expected SourceLimitException")
        } catch (e: SourceLimitException) {
            assertTrue(e.message!!.contains("20"))
        }
    }

    @Test
    fun `folders nested too deeply are refused`() {
        val deep = mapOf((1..70).joinToString("/") { "d" } + "/f.txt" to ByteArray(1))
        try {
            TreeSource(FakeTree(deep)).entries()
            fail("expected SourceLimitException")
        } catch (e: SourceLimitException) {
            assertTrue(e.message!!.contains("deeper"))
        }
    }

    @Test
    fun `a picked rpg maker folder imports end to end through the real importer`() {
        val library = Library(tmp.newFolder("lib"))
        val importer = GameImporter(library, "0.1.0", availableBytes = { Long.MAX_VALUE })

        val result = importer.importSource(TreeSource(FakeTree(rpg, "Tree Folder"))) as ImportResult.Imported

        val meta = result.game.readMeta()
        assertEquals("Tree Game", meta.title)
        assertEquals(Platform.RPGMAKER, meta.platform)
        assertEquals(SourceKind.FOLDER, meta.import.sourceKind)
        assertEquals("Tree Folder", meta.import.originalName)
        assertArrayEquals(rpg.getValue("Data/Scripts.rxdata"), File(result.game.game, "Data/Scripts.rxdata").readBytes())
    }
}
