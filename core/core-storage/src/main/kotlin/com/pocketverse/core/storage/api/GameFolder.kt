// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.core.storage.api

import com.pocketverse.core.model.api.GameRecord
import com.pocketverse.core.storage.AtomicFiles
import com.pocketverse.core.storage.MetaJson
import java.io.File

/**
 * One game's self-contained folder (`docs/architecture/STORAGE.md` section 4). The only place that knows
 * the folder layout: no other module builds these paths by hand.
 */
class GameFolder internal constructor(val root: File) {
    /** The original game files. Never written by engines. */
    val game: File get() = File(root, GAME)
    val save: File get() = File(root, SAVE)
    val state: File get() = File(root, STATE)
    val autostate: File get() = File(root, AUTOSTATE)
    val cheats: File get() = File(root, CHEATS)
    /** Everything an engine generates: compiled scripts, caches, logs. */
    val engine: File get() = File(root, ENGINE)
    val art: File get() = File(root, ART)
    val metaFile: File get() = File(root, META)

    val name: String get() = root.name

    /** @throws MetaException when the file is missing, unreadable, invalid or from a newer version. */
    fun readMeta(): GameRecord {
        val text = try {
            metaFile.readText(Charsets.UTF_8)
        } catch (e: java.io.IOException) {
            throw MetaException.Invalid("cannot read ${metaFile.name}: ${e.message}", e)
        }
        return MetaJson.decode(text)
    }

    /** Atomic: a crash leaves either the old or the new file, never a mix. */
    fun writeMeta(record: GameRecord) {
        AtomicFiles.write(metaFile, MetaJson.encode(record).toByteArray(Charsets.UTF_8))
    }

    /** Deletes the whole folder. The caller has already confirmed with the user. */
    fun deleteRecursively(): Boolean = root.deleteRecursively()

    companion object {
        const val GAME = "game"
        const val SAVE = "save"
        const val STATE = "state"
        const val AUTOSTATE = "autostate"
        const val CHEATS = "cheats"
        const val ENGINE = "engine"
        const val ART = "art"
        const val META = "meta.json"

        /** The folders every game folder contains. */
        val SUBFOLDERS = listOf(GAME, SAVE, STATE, AUTOSTATE, CHEATS, ENGINE, ART)
    }
}
