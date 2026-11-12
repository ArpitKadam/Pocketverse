// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.core.common.api

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class HashingTest {
    @Test
    fun `sha256 of abc matches the published vector`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Hashing.sha256Hex("abc".toByteArray()))
    }

    @Test
    fun `sha256 of empty input matches the published vector`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Hashing.sha256Hex(ByteArray(0)))
    }

    @Test
    fun `crc32 of 123456789 matches the standard check value`() {
        val h = Hashing.romHashes("123456789".byteInputStream())
        assertEquals("CBF43926", h.crc32)
        assertEquals(Hashing.sha256Hex("123456789".toByteArray()), h.sha256)
    }

    @Test
    fun `streaming larger than the buffer gives the same result as one shot`() {
        val data = ByteArray(200_000) { (it % 251).toByte() }
        assertEquals(Hashing.sha256Hex(data), Hashing.romHashes(data.inputStream()).sha256)
    }
}
