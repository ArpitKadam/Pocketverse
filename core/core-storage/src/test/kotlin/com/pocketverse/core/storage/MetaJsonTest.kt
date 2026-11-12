// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.core.storage

import com.pocketverse.core.model.api.ContentHash
import com.pocketverse.core.model.api.EngineRef
import com.pocketverse.core.model.api.GameRecord
import com.pocketverse.core.model.api.HashMethod
import com.pocketverse.core.model.api.ImportInfo
import com.pocketverse.core.model.api.LaunchSpec
import com.pocketverse.core.model.api.Platform
import com.pocketverse.core.model.api.RomInfo
import com.pocketverse.core.model.api.SettingValue
import com.pocketverse.core.model.api.SourceKind
import com.pocketverse.core.model.api.UiMode
import com.pocketverse.core.model.api.UiPrefs
import com.pocketverse.core.storage.api.MetaException
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MetaJsonTest {
    private val sha = "3fa94c1e" + "0".repeat(56)

    private fun gba() = GameRecord(
        id = "3fa94c1e",
        title = "Example GBA Game",
        platform = Platform.GBA,
        engine = EngineRef("libretro-mgba"),
        launch = LaunchSpec("example.gba"),
        hash = ContentHash(sha, HashMethod.ROM, "A1B2C3D4"),
        import = ImportInfo(Instant.parse("2026-11-12T10:00:00Z"), "example.gba", SourceKind.FILE, "0.1.0"),
        rom = RomInfo("AXXE", "EXAMPLE", 16_777_216),
        ui = UiPrefs(UiMode.GBA),
        settings = mapOf("cheats" to SettingValue.Bool(false), "rpg.fontScale" to SettingValue.Num(0.75), "lang" to SettingValue.Str("en")),
        controlLayouts = mapOf("landscape" to "layout-1"),
        coverPath = "art/cover.png",
    )

    @Test
    fun `a record survives encode then decode`() {
        val record = gba()
        val back = MetaJson.decode(MetaJson.encode(record))
        assertEquals(record, back.copy(raw = record.raw))
    }

    @Test
    fun `output is pretty printed with two-space indent, ends with a newline and uses documented key names`() {
        val text = MetaJson.encode(gba())
        assertTrue(text.startsWith("{\n  \"schemaVersion\": 1,\n  \"id\": \"3fa94c1e\""))
        assertTrue(text.endsWith("}\n"))
        assertTrue("\"importedAt\": \"2026-11-12T10:00:00Z\"" in text)
    }

    @Test
    fun `unknown keys, top level and nested, are kept when the file is rewritten`() {
        val text = MetaJson.encode(gba())
        val tweaked = Json.parseToJsonElement(text).jsonObject.toMutableMap().also {
            it["future"] = JsonPrimitive("keep me")
            it["x"] = JsonObject(mapOf("engineData" to JsonPrimitive(7)))
            it["engine"] = JsonObject(it["engine"]!!.jsonObject + ("pack" to JsonPrimitive("p1")))
        }
        val loaded = MetaJson.decode(Json.encodeToString(JsonObject.serializer(), JsonObject(tweaked)))
        val rewritten = Json.parseToJsonElement(MetaJson.encode(loaded.copy(title = "Renamed"))).jsonObject
        assertEquals("keep me", rewritten["future"]!!.jsonPrimitive.content)
        assertEquals("7", rewritten["x"]!!.jsonObject["engineData"]!!.jsonPrimitive.content)
        assertEquals("p1", rewritten["engine"]!!.jsonObject["pack"]!!.jsonPrimitive.content)
        assertEquals("Renamed", rewritten["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun `clearing an optional field removes it from the file`() {
        val loaded = MetaJson.decode(MetaJson.encode(gba()))
        val cleared = Json.parseToJsonElement(MetaJson.encode(loaded.copy(ui = null, coverPath = null, settings = emptyMap()))).jsonObject
        assertNull(cleared["ui"])
        assertNull(cleared["settings"])
        assertNull(cleared["art"]!!.jsonObject["cover"])
    }

    @Test
    fun `a newer schema version is refused with its version number`() {
        val newer = MetaJson.encode(gba()).replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")
        val e = assertThrows<MetaException.UnsupportedSchema> { MetaJson.decode(newer) }
        assertEquals(2, e.found)
    }

    @Test
    fun `invalid input is reported as Invalid, never crashes`() {
        assertThrows<MetaException.Invalid> { MetaJson.decode("not json") }
        assertThrows<MetaException.Invalid> { MetaJson.decode("[]") }
        assertThrows<MetaException.Invalid> { MetaJson.decode("{}") }
        val good = MetaJson.encode(gba())
        assertThrows<MetaException.Invalid> { MetaJson.decode(good.replace("\"platform\": \"gba\"", "\"platform\": \"saturn\"")) }
        assertThrows<MetaException.Invalid> { MetaJson.decode(good.replace("\"3fa94c1e\"", "\"zz\"")) }
    }

    @Test
    fun `launch and cover paths must be safe relative paths`() {
        val good = MetaJson.encode(gba())
        for (bad in listOf("../x.gba", "/abs/x.gba", "C:/x.gba", "a\\\\b.gba")) {
            assertThrows<MetaException.Invalid>("launch=$bad") { MetaJson.decode(good.replace("\"example.gba\"", "\"$bad\"").replace("\"originalName\": \"$bad\"", "\"originalName\": \"example.gba\"")) }
        }
    }

    @Test
    fun `a cartridge record without rom info is rejected`() {
        val noRom = Json.parseToJsonElement(MetaJson.encode(gba())).jsonObject.toMutableMap().also { it.remove("rom") }
        assertThrows<MetaException.Invalid> { MetaJson.decode(Json.encodeToString(JsonObject.serializer(), JsonObject(noRom))) }
    }

    @Test
    fun `settings must carry exactly one typed value`() {
        val text = MetaJson.encode(gba()).replace("\"boolean\": false", "\"boolean\": false, \"string\": \"x\"")
        assertThrows<MetaException.Invalid> { MetaJson.decode(text) }
    }
}
