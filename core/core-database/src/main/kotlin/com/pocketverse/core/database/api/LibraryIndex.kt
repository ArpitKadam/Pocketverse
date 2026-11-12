// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.core.database.api

import android.content.Context
import androidx.room.Room
import com.pocketverse.core.database.GameEntity
import com.pocketverse.core.database.IndexDatabase
import com.pocketverse.core.database.Stamp
import com.pocketverse.core.database.SyncPlanner
import com.pocketverse.core.model.api.PlayStats
import com.pocketverse.core.model.api.Platform
import com.pocketverse.core.storage.api.GameFolder
import com.pocketverse.core.storage.api.Library
import com.pocketverse.core.storage.api.ScanProblem
import com.pocketverse.core.storage.api.ScannedGame
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** One row of the library list. [folderName] identifies the game inside the library root. */
data class GameEntry(
    val folderName: String,
    val id: String,
    val title: String,
    val platform: Platform,
    val engineId: String,
    val importedAt: Instant,
    val lastPlayedAt: Instant?,
    val playTimeSeconds: Long,
    val launchCount: Int,
    /** Cover path relative to the game folder, or null. */
    val coverPath: String?,
)

data class SyncReport(val added: Int, val updated: Int, val removed: Int, val problems: List<ScanProblem>)

/**
 * A searchable, sortable cache of the library. The `meta.json` files are the truth: delete the database and
 * [sync] rebuilds it (`docs/architecture/STORAGE.md`, rule 7).
 */
class LibraryIndex internal constructor(private val db: IndexDatabase) {
    private val dao get() = db.games()

    /** All games sorted by title, optionally only one platform. Re-emits when the index changes. */
    fun games(platform: Platform? = null): Flow<List<GameEntry>> =
        (if (platform == null) dao.observeAll() else dao.observeByPlatform(platform.id)).map { rows -> rows.map { it.toEntry() } }

    /** Games whose title contains [text] (case-insensitive). A blank query lists everything. */
    fun search(text: String): Flow<List<GameEntry>> =
        if (text.isBlank()) games() else dao.search(escapeLike(text.lowercase().trim())).map { rows -> rows.map { it.toEntry() } }

    /** Brings the index in line with the folders: only new or changed `meta.json` files are used. */
    suspend fun sync(library: Library): SyncReport = withContext(Dispatchers.IO) {
        val scan = library.scan()
        val stamps = scan.games.associateWith { Stamp(it.folder.metaFile.lastModified(), it.folder.metaFile.length()) }
        val plan = SyncPlanner.plan(scan.games, stamps, dao.syncRows())
        val rows = (plan.added + plan.updated).map { it.toEntity(stamps.getValue(it)) }
        if (rows.isNotEmpty() || plan.removed.isNotEmpty()) dao.apply(rows, plan.removed)
        SyncReport(plan.added.size, plan.updated.size, plan.removed.size, scan.problems)
    }

    /** Adds one finished play session. The record goes into `meta.json` first (the source of truth), then the row. */
    suspend fun recordPlay(game: GameFolder, seconds: Long, at: Instant): Unit = withContext(Dispatchers.IO) {
        require(seconds >= 0) { "seconds must not be negative" }
        val record = game.readMeta()
        val old = record.stats ?: PlayStats()
        val updated = record.copy(stats = PlayStats(old.playTimeSeconds + seconds, at, old.launchCount + 1))
        game.writeMeta(updated)
        val scanned = ScannedGame(game, updated)
        dao.upsert(listOf(scanned.toEntity(Stamp(game.metaFile.lastModified(), game.metaFile.length()))))
    }

    fun close() = db.close()

    companion object {
        private const val FILE_NAME = "library-index.db"

        /** The on-disk index. Any schema change drops and rebuilds it. */
        fun open(context: Context): LibraryIndex = LibraryIndex(
            Room.databaseBuilder(context.applicationContext, IndexDatabase::class.java, FILE_NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build(),
        )

        /** An index that lives only in memory, for tests. */
        fun inMemory(context: Context): LibraryIndex = LibraryIndex(
            Room.inMemoryDatabaseBuilder(context.applicationContext, IndexDatabase::class.java).build(),
        )

        private fun escapeLike(s: String) = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}

private fun ScannedGame.toEntity(stamp: Stamp) = GameEntity(
    folderName = name,
    id = record.id,
    title = record.title,
    titleSort = record.title.lowercase(),
    platform = record.platform.id,
    engineId = record.engine.id,
    hashSha256 = record.hash.sha256,
    importedAtMillis = record.import.importedAt.toEpochMilli(),
    lastPlayedAtMillis = record.stats?.lastPlayedAt?.toEpochMilli(),
    playTimeSeconds = record.stats?.playTimeSeconds ?: 0,
    launchCount = record.stats?.launchCount ?: 0,
    coverPath = record.coverPath,
    metaModifiedMillis = stamp.modifiedMillis,
    metaSizeBytes = stamp.sizeBytes,
)

private fun GameEntity.toEntry() = GameEntry(
    folderName = folderName,
    id = id,
    title = title,
    platform = Platform.fromId(platform) ?: error("unknown platform '$platform' in index"),
    engineId = engineId,
    importedAt = Instant.ofEpochMilli(importedAtMillis),
    lastPlayedAt = lastPlayedAtMillis?.let { Instant.ofEpochMilli(it) },
    playTimeSeconds = playTimeSeconds,
    launchCount = launchCount,
    coverPath = coverPath,
)
