// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The library index. A version bump drops and rebuilds it (see `LibraryIndex.open`): it is a cache of the
 * game folders, so there are no migrations to maintain.
 */
@Database(entities = [GameEntity::class], version = 1, exportSchema = false)
internal abstract class IndexDatabase : RoomDatabase() {
    abstract fun games(): GameDao
}
