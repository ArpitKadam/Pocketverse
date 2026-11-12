// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing

import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream

/** Builds small archives of every supported kind for tests. */
object ArchiveFixtures {
    fun zip(file: File, files: Map<String, ByteArray>): File {
        ZipOutputStream(file.outputStream()).use { z ->
            files.forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry()
            }
        }
        return file
    }

    fun encryptedZip(file: File, password: String, files: Map<String, ByteArray>): File {
        val zip = ZipFile(file, password.toCharArray())
        val params = ZipParameters().apply {
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.AES
            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
        }
        files.forEach { (name, bytes) -> zip.addStream(ByteArrayInputStream(bytes), ZipParameters(params).also { it.fileNameInZip = name }) }
        return file
    }

    fun sevenZ(file: File, files: Map<String, ByteArray>): File {
        SevenZOutputFile(file).use { sz ->
            files.forEach { (name, bytes) ->
                sz.putArchiveEntry(SevenZArchiveEntry().also { it.name = name; it.size = bytes.size.toLong() })
                sz.write(bytes)
                sz.closeArchiveEntry()
            }
        }
        return file
    }

    fun tar(file: File, files: Map<String, ByteArray>, gzip: Boolean = false): File {
        val raw = file.outputStream()
        TarArchiveOutputStream(if (gzip) GZIPOutputStream(raw) else raw).use { t ->
            files.forEach { (name, bytes) ->
                t.putArchiveEntry(TarArchiveEntry(name).also { it.size = bytes.size.toLong() })
                t.write(bytes)
                t.closeArchiveEntry()
            }
        }
        return file
    }

    /** A small RPG Maker XP game tree. */
    fun rpgMakerXp(prefix: String = "", title: String = "Archive Game"): Map<String, ByteArray> = mapOf(
        "${prefix}Game.ini" to "[Game]\nTitle=$title\n".toByteArray(),
        "${prefix}Game.exe" to ByteArray(10),
        "${prefix}Data/Scripts.rxdata" to ByteArray(40) { it.toByte() },
        "${prefix}Graphics/Pictures/a.png" to ByteArray(7),
    )
}
