// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing.api

/**
 * Safety limits for anything copied or unpacked from outside the app, so a hostile or broken source cannot
 * exhaust the disk or the file table. Sizes are checked against what the source declares, and writes are
 * stopped at the declared size, so a source cannot lie its way past them.
 */
data class ImportLimits(
    val maxTotalBytes: Long = 20L * 1024 * 1024 * 1024,
    val maxEntryBytes: Long = 8L * 1024 * 1024 * 1024,
    val maxEntries: Int = 250_000,
)
