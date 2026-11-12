// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing

import com.pocketverse.feature.importing.api.GameSource
import com.pocketverse.feature.importing.api.SourceEntry
import java.io.ByteArrayInputStream
import java.io.InputStream

/** In-memory source: the detector is tested without touching the disk. */
class MemorySource(override val displayName: String, private val files: Map<String, ByteArray>) : GameSource {
    constructor(displayName: String, vararg files: Pair<String, ByteArray>) : this(displayName, linkedMapOf(*files))

    override fun entries() = files.map { (p, b) -> SourceEntry(p, b.size.toLong()) }
    override fun open(path: String): InputStream = ByteArrayInputStream(files.getValue(path))
}

/** Synthetic cartridge images with valid headers and no game content. */
object Fixtures {
    fun text(s: String) = s.toByteArray()

    /** A GBA image: ARM branch, our own title and code, correct complement checksum unless [goodChecksum] is false. */
    fun gba(
        title: String = "TESTGAME",
        code: String = "AXXE",
        size: Int = 0x400,
        goodChecksum: Boolean = true,
        fixedByte: Int = 0x96,
    ): ByteArray {
        val b = ByteArray(size)
        b[0] = 0x2E; b[1] = 0; b[2] = 0; b[3] = 0xEA.toByte()
        title.toByteArray().copyInto(b, 0xA0, 0, minOf(12, title.length))
        code.toByteArray().copyInto(b, 0xAC)
        "01".toByteArray().copyInto(b, 0xB0)
        b[0xB2] = fixedByte.toByte()
        var sum = 0
        for (i in 0xA0..0xBC) sum += b[i].toInt() and 0xFF
        b[0xBD] = (if (goodChecksum) (-(sum + 0x19)) and 0xFF else 0x00).toByte()
        return b
    }

    /** An NDS image: our own title and code, ARM9/ARM7 offsets inside the file, header CRC correct unless [goodCrc] is false. */
    fun nds(
        title: String = "TESTNDS",
        code: String = "ATXE",
        unitCode: Int = 0,
        size: Int = 0x1000,
        goodCrc: Boolean = true,
    ): ByteArray {
        val b = ByteArray(size)
        title.toByteArray().copyInto(b, 0, 0, minOf(12, title.length))
        code.toByteArray().copyInto(b, 0x0C)
        "01".toByteArray().copyInto(b, 0x10)
        b[0x12] = unitCode.toByte()
        putU32(b, 0x20, 0x200) // ARM9 offset
        putU32(b, 0x30, 0x400) // ARM7 offset
        val crc = Crc16.compute(b, 0, 0x15E)
        val stored = if (goodCrc) crc else crc xor 0x1234
        b[0x15E] = (stored and 0xFF).toByte()
        b[0x15F] = (stored ushr 8).toByte()
        return b
    }

    private fun putU32(b: ByteArray, off: Int, v: Int) {
        b[off] = v.toByte(); b[off + 1] = (v ushr 8).toByte(); b[off + 2] = (v ushr 16).toByte(); b[off + 3] = (v ushr 24).toByte()
    }
}
