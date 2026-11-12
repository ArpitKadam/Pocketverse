// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.core.storage.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class FolderNamesTest {
    @Test
    fun `illegal characters become spaces and runs collapse`() {
        assertEquals("A B C D", FolderNames.sanitize("A/B\\C:*?\"<>|D"))
        assertEquals("Pokemon Fire Red", FolderNames.sanitize("Pokemon:  Fire/Red"))
    }

    @Test
    fun `control characters are removed and ends are trimmed`() {
        assertEquals("Game", FolderNames.sanitize("  ..Game\u0001\u007f.. "))
    }

    @Test
    fun `empty or illegal-only titles become Untitled`() {
        assertEquals("Untitled", FolderNames.sanitize(""))
        assertEquals("Untitled", FolderNames.sanitize("***"))
        assertEquals("Untitled", FolderNames.sanitize("..."))
    }

    @Test
    fun `reserved device names get an underscore, also with an extension`() {
        assertEquals("con_", FolderNames.sanitize("con"))
        assertEquals("NUL.txt_", FolderNames.sanitize("NUL.txt"))
        assertEquals("COM1_", FolderNames.sanitize("COM1"))
        assertEquals("Console", FolderNames.sanitize("Console"))
    }

    @Test
    fun `long titles are cut to 80 code points without splitting a surrogate pair`() {
        val title = "x".repeat(79) + "😀" + "tail" // emoji is one code point at position 80
        val cut = FolderNames.sanitize(title)
        assertEquals(80, cut.codePointCount(0, cut.length))
        assertFalse(cut.endsWith("tail"))
        assertEquals("x".repeat(79) + "😀", cut)
    }

    @Test
    fun `folder name is title plus short id in brackets`() {
        assertEquals("Example Game [3fa94c1e]", FolderNames.folderName("Example: Game", "3fa94c1e"))
    }
}
