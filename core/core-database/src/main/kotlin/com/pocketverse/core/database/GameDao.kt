// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class GameDao {
    @Query("SELECT * FROM games ORDER BY titleSort, folderName")
    abstract fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE platform = :platform ORDER BY titleSort, folderName")
    abstract fun observeByPlatform(platform: String): Flow<List<GameEntity>>

    /** [escapedLike] must already have `%`, `_` and `\` escaped with `\`. */
    @Query("SELECT * FROM games WHERE titleSort LIKE '%' || :escapedLike || '%' ESCAPE '\\' ORDER BY titleSort, folderName")
    abstract fun search(escapedLike: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE folderName = :folderName")
    abstract suspend fun get(folderName: String): GameEntity?

    @Query("SELECT folderName, metaModifiedMillis, metaSizeBytes FROM games")
    abstract suspend fun syncRows(): List<SyncRow>

    @Upsert
    abstract suspend fun upsert(rows: List<GameEntity>)

    @Query("DELETE FROM games WHERE folderName IN (:folderNames)")
    abstract suspend fun deleteChunk(folderNames: List<String>)

    /** Applies a whole sync in one transaction so the index never shows a half-applied scan. */
    @Transaction
    open suspend fun apply(upserts: List<GameEntity>, deletes: List<String>) {
        // SQLite limits the number of bound variables; 500 stays far below it.
        deletes.chunked(500).forEach { deleteChunk(it) }
        if (upserts.isNotEmpty()) upsert(upserts)
    }
}
