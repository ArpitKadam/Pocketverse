// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.app

import com.pocketverse.core.common.api.Hashing
import com.pocketverse.core.model.api.ContentHash
import com.pocketverse.core.model.api.EngineRef
import com.pocketverse.core.model.api.GameRecord
import com.pocketverse.core.model.api.HashMethod
import com.pocketverse.core.model.api.ImportInfo
import com.pocketverse.core.model.api.LaunchSpec
import com.pocketverse.core.model.api.Platform
import com.pocketverse.core.model.api.RomInfo
import com.pocketverse.core.model.api.SourceKind
import com.pocketverse.core.storage.api.GameFolder
import com.pocketverse.core.storage.api.Library
import java.io.File
import java.time.Instant

/**
 * Debug-only stand-in for the real import pipeline (feature-import, Phase 2): puts a tiny fake game in the
 * library through the same transactional path the real import will use. Never part of a release build's UI.
 */
internal object DebugImportStub {
    fun add(library: Library, seed: Long = System.nanoTime(), now: Instant = Instant.now()): GameFolder {
        val bytes = "pocketverse-test-rom-$seed".toByteArray()
        val hashes = Hashing.romHashes(bytes.inputStream())
        val record = GameRecord(
            id = hashes.sha256.take(8),
            title = "Test Game ${hashes.sha256.take(4)}",
            platform = Platform.GBA,
            engine = EngineRef("libretro-mgba"),
            launch = LaunchSpec("test.gba"),
            hash = ContentHash(hashes.sha256, HashMethod.ROM, hashes.crc32),
            import = ImportInfo(now, "test.gba", SourceKind.FILE, "0.1.0-debug"),
            rom = RomInfo(gameCode = "TEST", headerTitle = "TEST GAME", sizeBytes = bytes.size.toLong()),
        )
        val pending = library.beginImport(record)
        try {
            File(pending.folder.game, "test.gba").writeBytes(bytes)
            return pending.commit()
        } catch (e: Throwable) {
            pending.abort()
            throw e
        }
    }
}
