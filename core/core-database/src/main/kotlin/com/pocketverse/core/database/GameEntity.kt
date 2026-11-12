// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per game folder. A cache of that folder's `meta.json`: the file is the truth, this row only exists
 * so the library can be listed, sorted and searched without opening every folder.
 */
@Entity(tableName = "games", indices = [Index("titleSort"), Index("platform"), Index("hashSha256")])
internal data class GameEntity(
    @PrimaryKey val folderName: String,
    val id: String,
    val title: String,
    /** Lowercase title for ordering and search. */
    val titleSort: String,
    val platform: String,
    val engineId: String,
    val hashSha256: String,
    val importedAtMillis: Long,
    val lastPlayedAtMillis: Long?,
    val playTimeSeconds: Long,
    val launchCount: Int,
    val coverPath: String?,
    /** `meta.json` last-modified time and size when this row was written; a change means the row is stale. */
    val metaModifiedMillis: Long,
    val metaSizeBytes: Long,
)

/** Just enough of a row to decide whether it is stale. */
internal data class SyncRow(val folderName: String, val metaModifiedMillis: Long, val metaSizeBytes: Long)
