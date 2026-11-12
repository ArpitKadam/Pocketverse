// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing

import com.pocketverse.core.model.api.Platform
import com.pocketverse.core.model.api.RomInfo

/** What a header says about a cartridge image. [warnings] are problems that did not make it invalid. */
internal data class HeaderResult(val platform: Platform, val rom: RomInfo, val warnings: List<String>)

internal object CartridgeHeaders {
    /** Bytes the detector reads from the start of a file: the NDS header is the longer one. */
    const val SNIFF_BYTES = 0x200

    private const val GBA_HEADER = 0xC0
    private const val GBA_MAX_SIZE = 32L * 1024 * 1024
    private const val NDS_HEADER = 0x200

    /** Tries NDS first (its header is longer and has a CRC), then GBA. */
    fun parse(header: ByteArray, fileSize: Long): HeaderResult? = parseNds(header, fileSize) ?: parseGba(header, fileSize)

    // ---- GBA: header layout from the public GBATEK description
    fun parseGba(h: ByteArray, fileSize: Long): HeaderResult? {
        if (h.size < GBA_HEADER || fileSize < GBA_HEADER || fileSize > GBA_MAX_SIZE) return null
        if (h[0xB2] != 0x96.toByte()) return null // fixed value
        if (h[3] != 0xEA.toByte()) return null // first instruction is an unconditional ARM branch
        val code = ascii(h, 0xAC, 4)
        if (!isGameCode(code)) return null

        val warnings = mutableListOf<String>()
        var sum = 0
        for (i in 0xA0..0xBC) sum += h[i].toInt() and 0xFF
        val expected = (-(sum + 0x19)) and 0xFF
        if (expected != (h[0xBD].toInt() and 0xFF)) warnings += "GBA header complement checksum does not match (common in ROM hacks)"

        val rom = RomInfo(
            gameCode = code,
            headerTitle = ascii(h, 0xA0, 12).trimEnd('\u0000', ' '),
            sizeBytes = fileSize,
            makerCode = ascii(h, 0xB0, 2).takeIf { it.all { c -> c.isLetterOrDigit() } },
            region = region(code),
        )
        return HeaderResult(Platform.GBA, rom, warnings)
    }

    // ---- NDS: header layout from the public GBATEK description
    fun parseNds(h: ByteArray, fileSize: Long): HeaderResult? {
        if (h.size < NDS_HEADER || fileSize < NDS_HEADER) return null
        val code = ascii(h, 0x0C, 4)
        if (!isGameCode(code)) return null
        val unit = h[0x12].toInt() and 0xFF
        if (unit != 0 && unit != 2 && unit != 3) return null
        val title = h.copyOfRange(0, 12)
        if (title.any { b -> b != 0.toByte() && (b < 0x20 || b > 0x7E) }) return null
        val arm9 = u32(h, 0x20)
        val arm7 = u32(h, 0x30)
        if (arm9 < NDS_HEADER || arm9 >= fileSize || arm7 < NDS_HEADER || arm7 >= fileSize) return null

        val warnings = mutableListOf<String>()
        val storedCrc = (h[0x15E].toInt() and 0xFF) or ((h[0x15F].toInt() and 0xFF) shl 8)
        if (Crc16.compute(h, 0, 0x15E) != storedCrc) warnings += "NDS header CRC does not match (common in ROM hacks and homebrew)"
        if (unit == 3) warnings += "DSi-exclusive title: may not run on an NDS core"

        val rom = RomInfo(
            gameCode = code,
            headerTitle = ascii(h, 0, 12).trimEnd('\u0000', ' '),
            sizeBytes = fileSize,
            makerCode = ascii(h, 0x10, 2).takeIf { it.all { c -> c.isLetterOrDigit() } },
            region = region(code),
            unitCode = unit,
            dsiEnhanced = unit == 2,
        )
        return HeaderResult(Platform.NDS, rom, warnings)
    }

    private fun ascii(b: ByteArray, off: Int, len: Int): String =
        String(CharArray(len) { i -> (b[off + i].toInt() and 0xFF).toChar() })

    private fun isGameCode(s: String) = s.length == 4 && s.all { it in 'A'..'Z' || it in '0'..'9' }

    private fun u32(b: ByteArray, off: Int): Long =
        (b[off].toLong() and 0xFF) or ((b[off + 1].toLong() and 0xFF) shl 8) or
            ((b[off + 2].toLong() and 0xFF) shl 16) or ((b[off + 3].toLong() and 0xFF) shl 24)

    /** The last letter of the game code names the region; a common convention, not a guarantee. */
    private fun region(code: String): String? = when (code[3]) {
        'J' -> "Japan"
        'E' -> "USA"
        'P' -> "Europe"
        'D' -> "Germany"
        'F' -> "France"
        'I' -> "Italy"
        'S' -> "Spain"
        'K' -> "Korea"
        'C' -> "China"
        'U' -> "Australia"
        else -> null
    }
}

/** CRC-16/MODBUS (polynomial 0xA001 reflected, start value 0xFFFF): the checksum the NDS header uses. */
internal object Crc16 {
    fun compute(data: ByteArray, from: Int, to: Int): Int {
        var crc = 0xFFFF
        for (i in from until to) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
        }
        return crc
    }
}
