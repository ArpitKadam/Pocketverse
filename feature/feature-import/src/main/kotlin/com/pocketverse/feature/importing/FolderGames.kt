// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.importing

import com.pocketverse.core.model.api.EngineRef
import com.pocketverse.core.model.api.Platform
import com.pocketverse.feature.importing.api.Detection
import com.pocketverse.feature.importing.api.GameSource
import com.pocketverse.feature.importing.api.SourceEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Folder-style games, recognised by the engine's own files (CLAUDE.md section 7.2), never by an `.exe`. */
internal object FolderGames {
    private class Rel(val original: String, val lower: String)

    /** Every engine whose markers are present under [prefix] (normally zero or one). */
    fun detect(source: GameSource, entries: List<SourceEntry>, prefix: String, fallbackTitle: String): List<Detection.FolderGame> {
        val rels = entries
            .filter { it.path.startsWith(prefix, ignoreCase = true) }
            .map { val r = it.path.substring(prefix.length); Rel(r, r.lowercase()) }
        return listOfNotNull(
            rpgMaker(source, prefix, rels, fallbackTitle),
            rpgMakerWeb(source, prefix, rels, fallbackTitle),
            renpy(source, prefix, rels, fallbackTitle),
        )
    }

    // ---- RPG Maker XP / VX / VX Ace: Game.ini plus engine data or an encrypted archive
    private fun rpgMaker(source: GameSource, prefix: String, rels: List<Rel>, fallback: String): Detection.FolderGame? {
        val ini = rels.firstOrNull { it.lower == "game.ini" } ?: return null
        val variant = when {
            rels.any { it.lower.startsWith("data/") && it.lower.endsWith(".rvdata2") } -> "rgss3"
            rels.any { it.lower.startsWith("data/") && it.lower.endsWith(".rvdata") } -> "rgss2"
            rels.any { it.lower.startsWith("data/") && it.lower.endsWith(".rxdata") } -> "rgss1"
            rels.any { '/' !in it.lower && it.lower.endsWith(".rgss3a") } -> "rgss3"
            rels.any { '/' !in it.lower && it.lower.endsWith(".rgss2a") } -> "rgss2"
            rels.any { '/' !in it.lower && it.lower.endsWith(".rgssad") } -> "rgss1"
            else -> return null // a Game.ini alone is not enough: plenty of Windows games have one
        }
        val hints = buildSet {
            add(variant)
            if (rels.any { it.lower.startsWith("pbs/") || it.lower == "data/pkmnanimations.rxdata" }) add("pokemon-essentials")
        }
        val title = readText(source, prefix + ini.original)?.let { text ->
            text.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("Title=", ignoreCase = true) }
                ?.substringAfter('=')?.trim()?.takeIf { it.isNotEmpty() && '�' !in it }
        } ?: fallback
        return Detection.FolderGame(
            platform = Platform.RPGMAKER,
            engine = EngineRef("mkxp-z", variant),
            rootPrefix = prefix,
            launchFile = ini.original,
            suggestedTitle = title,
            hints = hints,
        )
    }

    // ---- RPG Maker MV / MZ: the engine's JavaScript core, plus index.html next to it
    private fun rpgMakerWeb(source: GameSource, prefix: String, rels: List<Rel>, fallback: String): Detection.FolderGame? {
        val cores = listOf(
            "www/js/rpg_core.js" to ("www/" to "mv"),
            "js/rpg_core.js" to ("" to "mv"),
            "www/js/rmmz_core.js" to ("www/" to "mz"),
            "js/rmmz_core.js" to ("" to "mz"),
        )
        for ((core, baseAndVariant) in cores) {
            val (base, variant) = baseAndVariant
            if (rels.none { it.lower == core }) continue
            val index = rels.firstOrNull { it.lower == base + "index.html" } ?: continue
            val system = rels.firstOrNull { it.lower == base + "data/system.json" }
            val title = system?.let { readText(source, prefix + it.original) }?.let(::gameTitle) ?: fallback
            return Detection.FolderGame(
                platform = Platform.RPGMAKER_MV,
                engine = EngineRef("rpgmv", variant),
                rootPrefix = prefix,
                launchFile = index.original,
                suggestedTitle = title,
                hints = setOf(variant),
            )
        }
        return null
    }

    // ---- Ren'Py: scripts or archives under game/
    private val renpyExtensions = setOf("rpy", "rpyc", "rpa", "rpym", "rpymc")

    private fun renpy(source: GameSource, prefix: String, rels: List<Rel>, fallback: String): Detection.FolderGame? {
        if (rels.none { it.lower.startsWith("game/") && it.lower.substringAfterLast('.', "") in renpyExtensions }) return null
        val launcher = rels
            .filter { '/' !in it.lower && (it.lower.endsWith(".py") || it.lower.endsWith(".sh")) && !it.lower.startsWith("renpy") }
            .sortedWith(compareBy({ !it.lower.endsWith(".py") }, { it.lower }))
            .firstOrNull()
        val options = rels.firstOrNull { it.lower == "game/options.rpy" }
        val title = options?.let { readText(source, prefix + it.original) }?.let { text ->
            Regex("""config\.name\s*=\s*_?\(?\s*["']([^"']+)["']""").find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        } ?: fallback
        val hints = buildSet {
            if (rels.any { it.lower.startsWith("renpy/") }) add("renpy-runtime-folder")
            if (rels.any { it.lower.startsWith("lib/") }) add("lib-folder")
        }
        return Detection.FolderGame(
            platform = Platform.RENPY,
            engine = EngineRef("renpy"),
            rootPrefix = prefix,
            launchFile = launcher?.original ?: "game",
            suggestedTitle = title,
            hints = hints,
        )
    }

    private fun gameTitle(json: String): String? = try {
        (Json.parseToJsonElement(json) as? JsonObject)?.get("gameTitle")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    /** First 64 KiB as UTF-8 text, or null if the file cannot be read. Enough for the small config files we look at. */
    private fun readText(source: GameSource, path: String): String? = try {
        source.open(path).use { it.readNBytes(64 * 1024).toString(Charsets.UTF_8) }
    } catch (_: Exception) {
        null
    }
}
