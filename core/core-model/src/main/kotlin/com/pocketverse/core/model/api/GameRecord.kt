// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.core.model.api

import java.time.Instant
import kotlinx.serialization.json.JsonObject

/** Game family. [id] is the stable string stored in `meta.json`. */
enum class Platform(val id: String) {
    GBA("gba"),
    NDS("nds"),
    RPGMAKER("rpgmaker"),
    RENPY("renpy"),
    RPGMAKER_MV("rpgmaker-mv");

    /** Cartridge platforms carry a [RomInfo] and are identified by a ROM hash. */
    val isCartridge: Boolean get() = this == GBA || this == NDS

    companion object {
        fun fromId(id: String): Platform? = entries.firstOrNull { it.id == id }
    }
}

enum class HashMethod(val id: String) {
    ROM("rom"),
    MANIFEST("manifest");

    companion object {
        fun fromId(id: String): HashMethod? = entries.firstOrNull { it.id == id }
    }
}

enum class SourceKind(val id: String) {
    FILE("file"),
    FOLDER("folder"),
    ARCHIVE("archive"),
    PACKAGE("package");

    companion object {
        fun fromId(id: String): SourceKind? = entries.firstOrNull { it.id == id }
    }
}

enum class UiMode(val id: String) {
    GBA("gba"),
    NDS("nds"),
    PC("pc"),
    DEFAULT("default");

    companion object {
        fun fromId(id: String): UiMode? = entries.firstOrNull { it.id == id }
    }
}

enum class UiOrientation(val id: String) {
    AUTO("auto"),
    PORTRAIT("portrait"),
    LANDSCAPE("landscape");

    companion object {
        fun fromId(id: String): UiOrientation? = entries.firstOrNull { it.id == id }
    }
}

data class EngineRef(val id: String, val variant: String? = null)

/** Launch file, relative to the game's `game/` folder, `/` separated. */
data class LaunchSpec(val file: String)

data class ContentHash(val sha256: String, val method: HashMethod, val crc32: String? = null) {
    init {
        require(SHA256_REGEX.matches(sha256)) { "sha256 must be 64 lowercase hex characters" }
        require(crc32 == null || CRC32_REGEX.matches(crc32)) { "crc32 must be 8 uppercase hex characters" }
    }

    /** Short id: the first [length] hex characters of the hash (8, or 12 after a folder-name collision). */
    fun shortId(length: Int = 8): String {
        require(length in 8..12) { "short id length must be 8..12" }
        return sha256.take(length)
    }

    private companion object {
        val SHA256_REGEX = Regex("^[0-9a-f]{64}$")
        val CRC32_REGEX = Regex("^[0-9A-F]{8}$")
    }
}

data class RomInfo(
    val gameCode: String,
    val headerTitle: String,
    val sizeBytes: Long,
    val makerCode: String? = null,
    val region: String? = null,
    val unitCode: Int? = null,
    val dsiEnhanced: Boolean? = null,
)

data class ImportInfo(
    val importedAt: Instant,
    val originalName: String,
    val sourceKind: SourceKind,
    val appVersion: String,
)

data class UiPrefs(val mode: UiMode? = null, val orientation: UiOrientation? = null)

data class PlayStats(
    val playTimeSeconds: Long = 0,
    val lastPlayedAt: Instant? = null,
    val launchCount: Int = 0,
)

/** A typed per-game setting. The type is part of the stored value, never guessed. */
sealed interface SettingValue {
    data class Bool(val value: Boolean) : SettingValue
    data class Str(val value: String) : SettingValue
    data class Num(val value: Double) : SettingValue
}

/**
 * The in-memory form of a game folder's `meta.json` (schema version 1, `docs/architecture/META_SCHEMA_V1.md`).
 *
 * [raw] is the JSON object the record was read from. It lets the writer keep keys this version does not
 * understand (top level and inside nested objects). It is empty for a record built in code.
 */
data class GameRecord(
    val id: String,
    val title: String,
    val platform: Platform,
    val engine: EngineRef,
    val launch: LaunchSpec,
    val hash: ContentHash,
    val import: ImportInfo,
    val rom: RomInfo? = null,
    val ui: UiPrefs? = null,
    val settings: Map<String, SettingValue> = emptyMap(),
    val controlLayouts: Map<String, String> = emptyMap(),
    val stats: PlayStats? = null,
    val coverPath: String? = null,
    val raw: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(id.length in 8..12 && hash.sha256.startsWith(id)) { "id must be the first 8..12 hex characters of the sha256" }
        require(!platform.isCartridge || rom != null) { "cartridge platforms require rom info" }
    }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}
