// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.core.database

import com.pocketverse.core.storage.api.ScannedGame

internal data class Stamp(val modifiedMillis: Long, val sizeBytes: Long)

internal data class SyncPlan(
    val added: List<ScannedGame>,
    val updated: List<ScannedGame>,
    val removed: List<String>,
)

/** Decides which folders to (re)read into the index. Pure, so it is tested without a database. */
internal object SyncPlanner {
    fun plan(scanned: List<ScannedGame>, stamps: Map<ScannedGame, Stamp>, rows: List<SyncRow>): SyncPlan {
        val byName = rows.associateBy { it.folderName }
        val added = mutableListOf<ScannedGame>()
        val updated = mutableListOf<ScannedGame>()
        for (game in scanned) {
            val row = byName[game.name]
            val stamp = stamps.getValue(game)
            when {
                row == null -> added += game
                row.metaModifiedMillis != stamp.modifiedMillis || row.metaSizeBytes != stamp.sizeBytes -> updated += game
            }
        }
        val present = scanned.mapTo(HashSet()) { it.name }
        val removed = rows.map { it.folderName }.filter { it !in present }
        return SyncPlan(added, updated, removed)
    }
}
