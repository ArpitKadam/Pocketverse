// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing

import com.pocketverse.feature.importing.api.ArchiveExtractor
import com.pocketverse.feature.importing.api.ArchivePasswords
import com.pocketverse.feature.importing.api.ExtractResult
import com.pocketverse.feature.importing.api.ImportError
import com.pocketverse.feature.importing.api.ImportLimits
import java.io.File
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ArchiveExtractorTest {
    @TempDir
    lateinit var tmp: File

    private lateinit var out: File
    private val files = mapOf("a.txt" to "alpha".toByteArray(), "dir/b.bin" to ByteArray(300) { it.toByte() })

    @BeforeEach
    fun setUp() {
        out = File(tmp, "out").also { it.mkdir() }
    }

    private fun extract(archive: File, limits: ImportLimits = ImportLimits(), pw: ArchivePasswords = ArchivePasswords { _, _ -> null }, space: Long = Long.MAX_VALUE) =
        ArchiveExtractor(limits, availableBytes = { space }).extract(archive, out, pw)

    private fun assertExtracted() {
        assertArrayEquals(files.getValue("a.txt"), File(out, "a.txt").readBytes())
        assertArrayEquals(files.getValue("dir/b.bin"), File(out, "dir/b.bin").readBytes())
    }

    @Test
    fun `formats are recognised by content, not by name`() {
        val ex = ArchiveExtractor()
        assertEquals("zip", ex.kindOf(ArchiveFixtures.zip(File(tmp, "x.dat"), files)))
        assertEquals("7z", ex.kindOf(ArchiveFixtures.sevenZ(File(tmp, "y.zip"), files)))
        assertEquals("tar", ex.kindOf(ArchiveFixtures.tar(File(tmp, "z.bin"), files)))
        assertEquals("gzip", ex.kindOf(ArchiveFixtures.tar(File(tmp, "w.tar"), files, gzip = true)))
        assertNull(ex.kindOf(File(tmp, "plain.txt").also { it.writeText("hello world, not an archive") }))
    }

    @Test
    fun `zip 7z tar and tar gz all unpack to the same tree`() {
        listOf(
            ArchiveFixtures.zip(File(tmp, "a.zip"), files),
            ArchiveFixtures.sevenZ(File(tmp, "a.7z"), files),
            ArchiveFixtures.tar(File(tmp, "a.tar"), files),
            ArchiveFixtures.tar(File(tmp, "a.tgz"), files, gzip = true),
        ).forEach { archive ->
            out.deleteRecursively(); out.mkdir()
            val res = extract(archive)
            assertTrue(res is ExtractResult.Done, "${archive.name}: $res")
            val r = res as ExtractResult.Done
            assertEquals(2, r.fileCount, archive.name)
            assertExtracted()
        }
    }

    @Test
    fun `entries that could escape the folder are skipped and counted, junk is dropped silently`() {
        val zip = ArchiveFixtures.zip(
            File(tmp, "evil.zip"),
            files + mapOf("../escaped.txt" to ByteArray(1), "/abs.txt" to ByteArray(1), "__MACOSX/._a.txt" to ByteArray(1), "dir/.DS_Store" to ByteArray(1)),
        )
        val r = extract(zip) as ExtractResult.Done
        assertEquals(2, r.skippedUnsafe)
        assertEquals(2, r.fileCount)
        assertFalse(File(tmp, "escaped.txt").exists())
        assertFalse(File(out, "__MACOSX").exists())
        assertFalse(File(out, "dir/.DS_Store").exists())
        assertExtracted()
    }

    @Test
    fun `an encrypted zip asks for a password and retries until it works`() {
        val zip = ArchiveFixtures.encryptedZip(File(tmp, "secret.zip"), "hunter2", files)
        // no password offered: gives up
        assertTrue((extract(zip) as ExtractResult.Failed).error is ImportError.PasswordRequired)

        val asked = mutableListOf<Int>()
        val r = extract(zip, pw = ArchivePasswords { name, attempt ->
            asked += attempt
            assertEquals("secret.zip", name)
            if (attempt < 3) "wrong$attempt".toCharArray() else "hunter2".toCharArray()
        })
        assertTrue(r is ExtractResult.Done)
        assertEquals(listOf(1, 2, 3), asked)
        assertExtracted()
    }

    @Test
    fun `five wrong passwords stop the attempts`() {
        val zip = ArchiveFixtures.encryptedZip(File(tmp, "secret.zip"), "hunter2", files)
        var calls = 0
        val r = extract(zip, pw = ArchivePasswords { _, _ -> calls++; "nope".toCharArray() }) as ExtractResult.Failed
        assertTrue(r.error is ImportError.PasswordRequired)
        assertEquals(5, calls)
    }

    @Test
    fun `rar and unknown files are refused with their own errors`() {
        val rar = File(tmp, "x.rar").also { it.writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00) + ByteArray(600)) }
        assertEquals(ImportError.UnsupportedArchive("RAR"), (extract(rar) as ExtractResult.Failed).error)
        val junk = File(tmp, "j.zip").also { it.writeBytes(ByteArray(1000) { 7 }) }
        assertTrue((extract(junk) as ExtractResult.Failed).error is ImportError.UnsupportedArchive)
    }

    @Test
    fun `limits on file count, single file size and total size are enforced`() {
        val zip = ArchiveFixtures.zip(File(tmp, "a.zip"), files)
        assertTrue((extract(zip, ImportLimits(maxEntries = 1)) as ExtractResult.Failed).error is ImportError.LimitExceeded)
        out.deleteRecursively(); out.mkdir()
        assertTrue((extract(zip, ImportLimits(maxEntryBytes = 100)) as ExtractResult.Failed).error is ImportError.LimitExceeded)
        out.deleteRecursively(); out.mkdir()
        assertTrue((extract(zip, ImportLimits(maxTotalBytes = 200)) as ExtractResult.Failed).error is ImportError.LimitExceeded)
    }

    @Test
    fun `folders count toward the entry limit, so a flood of empty folders is refused`() {
        val dirs = (1..50).associate { "d$it/" to ByteArray(0) }
        // A tar has no index, so only the check made as each entry arrives can stop it.
        val tar = ArchiveFixtures.tar(File(tmp, "dirs.tar"), dirs)
        val r = extract(tar, ImportLimits(maxEntries = 10)) as ExtractResult.Failed
        assertTrue(r.error is ImportError.LimitExceeded)
        assertTrue(out.listFiles()!!.size <= 10, "stopped at the limit, made ${out.listFiles()!!.size}")
        // A zip is refused from its central directory before anything is created.
        out.deleteRecursively(); out.mkdir()
        val zip = ArchiveFixtures.zip(File(tmp, "dirs.zip"), dirs)
        assertTrue((extract(zip, ImportLimits(maxEntries = 10)) as ExtractResult.Failed).error is ImportError.LimitExceeded)
        assertEquals(0, out.listFiles()!!.size)
    }

    @Test
    fun `absurdly deep or long names are skipped like escape attempts`() {
        val deep = (1..70).joinToString("/") { "d" } + "/x.txt"
        val long = "n".repeat(300) + ".txt"
        val zip = ArchiveFixtures.zip(File(tmp, "deep.zip"), files + mapOf(deep to ByteArray(1), long to ByteArray(1)))
        val r = extract(zip) as ExtractResult.Done
        assertEquals(2, r.skippedUnsafe)
        assertExtracted()
    }

    @Test
    fun `a 7z with too many entries is refused before decoding`() {
        val many = (1..20).associate { "f$it.txt" to ByteArray(1) }
        val sz = ArchiveFixtures.sevenZ(File(tmp, "many.7z"), many)
        val r = extract(sz, ImportLimits(maxEntries = 5)) as ExtractResult.Failed
        assertTrue(r.error is ImportError.LimitExceeded)
        assertEquals(0, out.walkTopDown().count { it.isFile })
    }

    @Test
    fun `a 7z that needs more decoder memory than allowed is refused with a limit error`() {
        val sz = ArchiveFixtures.sevenZ(File(tmp, "mem.7z"), files)
        // The default LZMA2 dictionary written by Commons Compress is megabytes; allow only 1 KB.
        val r = ArchiveExtractor(maxDecoderMemoryKb = 1).extract(sz, out) as ExtractResult.Failed
        assertTrue(r.error is ImportError.LimitExceeded, "was ${r.error}")
    }

    @Test
    fun `not enough free space is reported before the entry is written`() {
        val zip = ArchiveFixtures.zip(File(tmp, "a.zip"), files)
        val r = extract(zip, space = 1000) as ExtractResult.Failed
        assertTrue(r.error is ImportError.InsufficientSpace)
        assertEquals(0, out.walkTopDown().count { it.isFile })
    }

    @Test
    fun `cancelling stops extraction`() {
        val zip = ArchiveFixtures.zip(File(tmp, "a.zip"), files)
        val r = ArchiveExtractor().extract(zip, out, shouldCancel = { true })
        assertEquals(ExtractResult.Cancelled, r)
    }
}
