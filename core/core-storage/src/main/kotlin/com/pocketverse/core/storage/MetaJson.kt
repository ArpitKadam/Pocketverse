// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.core.storage

import com.pocketverse.core.model.api.ContentHash
import com.pocketverse.core.model.api.EngineRef
import com.pocketverse.core.model.api.GameRecord
import com.pocketverse.core.model.api.HashMethod
import com.pocketverse.core.model.api.ImportInfo
import com.pocketverse.core.model.api.LaunchSpec
import com.pocketverse.core.model.api.PlayStats
import com.pocketverse.core.model.api.Platform
import com.pocketverse.core.model.api.RomInfo
import com.pocketverse.core.model.api.SettingValue
import com.pocketverse.core.model.api.SourceKind
import com.pocketverse.core.model.api.UiMode
import com.pocketverse.core.model.api.UiOrientation
import com.pocketverse.core.model.api.UiPrefs
import com.pocketverse.core.storage.api.MetaException
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** Reads and writes `meta.json` v1 (`docs/architecture/META_SCHEMA_V1.md`). Unknown keys survive a read-then-write. */
internal object MetaJson {
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun decode(text: String): GameRecord {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject ?: throw MetaException.Invalid("meta.json is not a JSON object")
        } catch (e: SerializationException) {
            throw MetaException.Invalid("meta.json is not valid JSON: ${e.message}", e)
        }
        val version = root["schemaVersion"]?.asInt() ?: throw MetaException.Invalid("schemaVersion is missing")
        if (version > GameRecord.SCHEMA_VERSION) throw MetaException.UnsupportedSchema(version)
        if (version < 1) throw MetaException.Invalid("schemaVersion must be 1 or more")

        val platformId = root.str("platform")
        val platform = Platform.fromId(platformId) ?: throw MetaException.Invalid("unknown platform '$platformId'")
        val engine = root.obj("engine")
        val hash = root.obj("hash")
        val hashMethod = hash.str("method").let { HashMethod.fromId(it) ?: throw MetaException.Invalid("unknown hash method '$it'") }
        val imp = root.obj("import")
        val sourceKind = imp.str("sourceKind").let { SourceKind.fromId(it) ?: throw MetaException.Invalid("unknown source kind '$it'") }

        try {
            return GameRecord(
                id = root.str("id"),
                title = root.str("title"),
                platform = platform,
                engine = EngineRef(engine.str("id"), engine.optStr("variant")),
                launch = LaunchSpec(root.obj("launch").str("file").also(::checkRelative)),
                hash = ContentHash(hash.str("sha256"), hashMethod, hash.optStr("crc32")),
                import = ImportInfo(
                    importedAt = parseInstant(imp.str("importedAt")),
                    originalName = imp.str("originalName"),
                    sourceKind = sourceKind,
                    appVersion = imp.str("appVersion"),
                ),
                rom = root.optObj("rom")?.let(::decodeRom),
                ui = root.optObj("ui")?.let(::decodeUi),
                settings = root.optObj("settings")?.let(::decodeSettings).orEmpty(),
                controlLayouts = root.optObj("controls")?.optObj("layouts")
                    ?.mapValues { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw MetaException.Invalid("controls.layouts.$k must be a string") }
                    .orEmpty(),
                stats = root.optObj("stats")?.let(::decodeStats),
                coverPath = root.optObj("art")?.optStr("cover")?.also(::checkRelative),
                raw = root,
            )
        } catch (e: IllegalArgumentException) {
            throw MetaException.Invalid(e.message ?: "invalid meta.json", e)
        }
    }

    fun encode(record: GameRecord): String {
        val raw = record.raw
        val top = LinkedHashMap<String, JsonElement>(raw)
        top["schemaVersion"] = JsonPrimitive(GameRecord.SCHEMA_VERSION)
        top["id"] = JsonPrimitive(record.id)
        top["title"] = JsonPrimitive(record.title)
        top["platform"] = JsonPrimitive(record.platform.id)
        top["engine"] = nested(raw, "engine") {
            put("id", JsonPrimitive(record.engine.id))
            putOrRemove("variant", record.engine.variant?.let { JsonPrimitive(it) })
        }
        top["launch"] = nested(raw, "launch") { put("file", JsonPrimitive(record.launch.file)) }
        top["hash"] = nested(raw, "hash") {
            put("sha256", JsonPrimitive(record.hash.sha256))
            put("method", JsonPrimitive(record.hash.method.id))
            putOrRemove("crc32", record.hash.crc32?.let { JsonPrimitive(it) })
        }
        record.rom?.let { rom ->
            top["rom"] = nested(raw, "rom") {
                put("gameCode", JsonPrimitive(rom.gameCode))
                put("headerTitle", JsonPrimitive(rom.headerTitle))
                put("sizeBytes", JsonPrimitive(rom.sizeBytes))
                putOrRemove("makerCode", rom.makerCode?.let { JsonPrimitive(it) })
                putOrRemove("region", rom.region?.let { JsonPrimitive(it) })
                putOrRemove("unitCode", rom.unitCode?.let { JsonPrimitive(it) })
                putOrRemove("dsiEnhanced", rom.dsiEnhanced?.let { JsonPrimitive(it) })
            }
        } ?: top.remove("rom")
        top["import"] = nested(raw, "import") {
            put("importedAt", JsonPrimitive(record.import.importedAt.toString()))
            put("originalName", JsonPrimitive(record.import.originalName))
            put("sourceKind", JsonPrimitive(record.import.sourceKind.id))
            put("appVersion", JsonPrimitive(record.import.appVersion))
        }
        record.ui?.let { ui ->
            top["ui"] = nested(raw, "ui") {
                putOrRemove("mode", ui.mode?.let { JsonPrimitive(it.id) })
                putOrRemove("orientation", ui.orientation?.let { JsonPrimitive(it.id) })
            }
        } ?: top.remove("ui")
        if (record.settings.isEmpty()) top.remove("settings") else top["settings"] = JsonObject(record.settings.mapValues { (_, v) -> encodeSetting(v) })
        if (record.controlLayouts.isEmpty()) {
            (top["controls"] as? JsonObject)?.let { c -> top["controls"] = JsonObject(c - "layouts") }
        } else {
            top["controls"] = nested(raw, "controls") { put("layouts", JsonObject(record.controlLayouts.mapValues { JsonPrimitive(it.value) })) }
        }
        record.stats?.let { s ->
            top["stats"] = nested(raw, "stats") {
                put("playTimeSeconds", JsonPrimitive(s.playTimeSeconds))
                put("launchCount", JsonPrimitive(s.launchCount))
                putOrRemove("lastPlayedAt", s.lastPlayedAt?.let { JsonPrimitive(it.toString()) })
            }
        } ?: top.remove("stats")
        record.coverPath?.let { c -> top["art"] = nested(raw, "art") { put("cover", JsonPrimitive(c)) } }
            ?: (top["art"] as? JsonObject)?.let { a -> top["art"] = JsonObject(a - "cover") }
        return json.encodeToString(JsonObject.serializer(), JsonObject(top)) + "\n"
    }

    // --- nested objects: start from what the file had, so unknown nested keys survive
    private fun nested(raw: JsonObject, key: String, fill: MutableMap<String, JsonElement>.() -> Unit): JsonObject {
        val m = LinkedHashMap<String, JsonElement>((raw[key] as? JsonObject).orEmpty())
        m.fill()
        return JsonObject(m)
    }

    private fun MutableMap<String, JsonElement>.putOrRemove(key: String, value: JsonElement?) {
        if (value == null) remove(key) else put(key, value)
    }

    // --- decoding helpers
    private fun decodeRom(o: JsonObject) = RomInfo(
        gameCode = o.str("gameCode"),
        headerTitle = o.str("headerTitle"),
        sizeBytes = o["sizeBytes"]?.asLong() ?: throw MetaException.Invalid("rom.sizeBytes is missing"),
        makerCode = o.optStr("makerCode"),
        region = o.optStr("region"),
        unitCode = o["unitCode"]?.asInt(),
        dsiEnhanced = (o["dsiEnhanced"] as? JsonPrimitive)?.booleanOrNull,
    )

    private fun decodeUi(o: JsonObject) = UiPrefs(
        mode = o.optStr("mode")?.let { UiMode.fromId(it) ?: throw MetaException.Invalid("unknown ui.mode '$it'") },
        orientation = o.optStr("orientation")?.let { UiOrientation.fromId(it) ?: throw MetaException.Invalid("unknown ui.orientation '$it'") },
    )

    private fun decodeStats(o: JsonObject) = PlayStats(
        playTimeSeconds = o["playTimeSeconds"]?.asLong() ?: 0,
        lastPlayedAt = o.optStr("lastPlayedAt")?.let(::parseInstant),
        launchCount = o["launchCount"]?.asInt() ?: 0,
    )

    private fun decodeSettings(o: JsonObject): Map<String, SettingValue> = o.mapValues { (k, v) ->
        val typed = v as? JsonObject ?: throw MetaException.Invalid("setting '$k' must be an object")
        if (typed.size != 1) throw MetaException.Invalid("setting '$k' must have exactly one typed value")
        val (type, value) = typed.entries.single()
        val p = value as? JsonPrimitive ?: throw MetaException.Invalid("setting '$k' must hold a primitive value")
        when (type) {
            "boolean" -> SettingValue.Bool(p.booleanOrNull ?: throw MetaException.Invalid("setting '$k' is not a boolean"))
            "string" -> if (p.isString) SettingValue.Str(p.content) else throw MetaException.Invalid("setting '$k' is not a string")
            "number" -> SettingValue.Num(p.doubleOrNull ?: throw MetaException.Invalid("setting '$k' is not a number"))
            else -> throw MetaException.Invalid("setting '$k' has unknown type '$type'")
        }
    }

    private fun encodeSetting(v: SettingValue): JsonElement = when (v) {
        is SettingValue.Bool -> JsonObject(mapOf("boolean" to JsonPrimitive(v.value)))
        is SettingValue.Str -> JsonObject(mapOf("string" to JsonPrimitive(v.value)))
        is SettingValue.Num -> JsonObject(mapOf("number" to JsonPrimitive(v.value)))
    }

    private fun JsonObject.str(key: String): String = optStr(key) ?: throw MetaException.Invalid("'$key' is missing or not a string")
    private fun JsonObject.optStr(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.obj(key: String): JsonObject = this[key] as? JsonObject ?: throw MetaException.Invalid("'$key' is missing or not an object")
    private fun JsonObject.optObj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonElement.asInt(): Int? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
    private fun JsonElement.asLong(): Long? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

    private fun parseInstant(s: String): Instant = try {
        Instant.parse(s)
    } catch (e: DateTimeParseException) {
        throw MetaException.Invalid("'$s' is not an ISO-8601 UTC timestamp", e)
    }

    /** Paths in meta.json are relative to the game folder, `/` separated, and never climb out of it. */
    fun checkRelative(path: String) {
        val bad = path.isEmpty() || path.startsWith("/") || path.contains('\\') ||
            Regex("^[A-Za-z]:").containsMatchIn(path) || path.split('/').any { it == ".." }
        if (bad) throw MetaException.Invalid("'$path' is not a safe relative path")
    }
}
