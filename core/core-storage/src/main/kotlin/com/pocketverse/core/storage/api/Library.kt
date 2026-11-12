// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.core.storage.api

import com.pocketverse.core.model.api.GameRecord
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** The same game (same content hash) is already in the library. */
class GameAlreadyInLibrary(val existing: GameFolder) : Exception("this game is already in the library as '${existing.name}'")

/** A folder in the library that could not be read as a game. */
data class ScanProblem(val folderName: String, val reason: String)

/** A folder with a readable `meta.json`, and the record read from it. */
data class ScannedGame(val folder: GameFolder, val record: GameRecord) {
    val name: String get() = folder.name
}

data class LibraryScan(val games: List<ScannedGame>, val problems: List<ScanProblem>)

/**
 * The library root and everything directly under it (`docs/architecture/STORAGE.md`). The root is chosen by
 * the caller (app-specific external directory by default, or the shared folder), this class does not care which.
 */
class Library(val root: File) {
    init {
        if (!root.isDirectory && !root.mkdirs()) throw IOException("cannot create library root ${root.path}")
        val nomedia = File(root, ".nomedia")
        if (!nomedia.exists()) nomedia.createNewFile()
    }

    /**
     * Starts importing a game. Nothing is visible in the library until [PendingGame.commit]: files are written
     * into a hidden temporary folder, and one rename puts the finished folder in place.
     *
     * @throws GameAlreadyInLibrary when a folder with the same content hash already exists.
     */
    fun beginImport(record: GameRecord): PendingGame {
        findByHash(record)?.let { throw GameAlreadyInLibrary(it) }
        val tmp = File(root, "$IMPORTING_PREFIX${UUID.randomUUID()}")
        if (!tmp.mkdir()) throw IOException("cannot create ${tmp.path}")
        GameFolder.SUBFOLDERS.forEach { sub ->
            if (!File(tmp, sub).mkdir()) {
                tmp.deleteRecursively()
                throw IOException("cannot create $sub in ${tmp.path}")
            }
        }
        return PendingGame(this, GameFolder(tmp), record)
    }

    /**
     * A hidden scratch folder inside the library, on the same filesystem as the final game folders so a finished
     * file can be moved into place with a rename. The caller deletes it; [cleanupAbandonedImports] removes it
     * if the app died first.
     */
    fun createStagingDir(): File {
        val dir = File(root, "$IMPORTING_PREFIX${UUID.randomUUID()}")
        if (!dir.mkdir()) throw IOException("cannot create ${dir.path}")
        return dir
    }

    /** Games found under the root, plus the folders that could not be read (never throws for a bad folder). */
    fun scan(): LibraryScan {
        val games = mutableListOf<ScannedGame>()
        val problems = mutableListOf<ScanProblem>()
        val dirs = root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }.orEmpty().sortedBy { it.name.lowercase() }
        for (dir in dirs) {
            val folder = GameFolder(dir)
            try {
                games += ScannedGame(folder, folder.readMeta())
            } catch (e: MetaException) {
                problems += ScanProblem(dir.name, e.message ?: "unreadable meta.json")
            }
        }
        return LibraryScan(games, problems)
    }

    /** Removes half-finished imports left by a crash. Call at start-up. */
    fun cleanupAbandonedImports(): Int {
        val leftovers = root.listFiles { f -> f.isDirectory && f.name.startsWith(IMPORTING_PREFIX) }.orEmpty()
        leftovers.forEach { it.deleteRecursively() }
        return leftovers.size
    }

    // ponytail: rescans every meta.json per import (O(n) reads); switch to the core-database index when libraries get large
    private fun findByHash(record: GameRecord): GameFolder? =
        scan().games.firstOrNull { it.record.hash.sha256 == record.hash.sha256 }?.folder

    /** Moves the finished temporary folder into place under its final name; extends the id if the name is taken. */
    internal fun commit(pending: GameFolder, record: GameRecord): GameFolder {
        var finalRecord = record
        var target = File(root, FolderNames.folderName(record.title, record.id))
        if (target.exists()) {
            // Same name, different content (a hash collision on the short id): use a longer id.
            val longer = record.hash.shortId(12)
            finalRecord = record.copy(id = longer)
            target = File(root, FolderNames.folderName(record.title, longer))
            if (target.exists()) throw IOException("folder '${target.name}' already exists")
        }
        pending.writeMeta(finalRecord)
        try {
            Files.move(pending.root.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(pending.root.toPath(), target.toPath())
        }
        return GameFolder(target)
    }

    companion object {
        const val IMPORTING_PREFIX = ".importing-"
    }
}

/** A game being imported. Write into [folder] (`game/` first), then [commit] or [abort]. */
class PendingGame internal constructor(
    private val library: Library,
    val folder: GameFolder,
    private val record: GameRecord,
) {
    private var done = false

    /** Publishes the game: writes `meta.json` and renames the folder into the library. */
    fun commit(): GameFolder {
        check(!done) { "import already finished" }
        done = true
        return library.commit(folder, record)
    }

    /** Throws away everything written so far. Safe to call after a failure. */
    fun abort() {
        done = true
        folder.deleteRecursively()
    }
}
