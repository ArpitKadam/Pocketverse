// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.core.common.api

import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32

/** Both hashes of one stream, computed in a single pass. */
data class RomHashes(val sha256: String, val crc32: String)

object Hashing {
    private const val BUFFER = 64 * 1024

    /** SHA-256 as 64 lowercase hex characters. */
    fun sha256Hex(bytes: ByteArray): String = toHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    /** SHA-256 (lowercase hex) and CRC-32 (8 uppercase hex) of [input], read to the end. Does not close [input]. */
    fun romHashes(input: InputStream): RomHashes {
        val sha = MessageDigest.getInstance("SHA-256")
        val crc = CRC32()
        val buf = ByteArray(BUFFER)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            sha.update(buf, 0, n)
            crc.update(buf, 0, n)
        }
        return RomHashes(toHex(sha.digest()), "%08X".format(crc.value))
    }

    private fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append("0123456789abcdef"[(b.toInt() shr 4) and 0xF]).append("0123456789abcdef"[b.toInt() and 0xF])
        return sb.toString()
    }
}
