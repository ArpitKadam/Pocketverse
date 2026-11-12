// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.core.storage.api

/** A `meta.json` could not be used. Never thrown for a merely unknown extra key. */
sealed class MetaException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The file is not valid JSON, or breaks a rule of the schema. */
    class Invalid(message: String, cause: Throwable? = null) : MetaException(message, cause)

    /** The file was written by a newer Pocketverse ("created by a newer version"). */
    class UnsupportedSchema(val found: Int) : MetaException("meta.json schema version $found is newer than the supported version")
}
