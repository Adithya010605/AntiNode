package dev.antinode.core.formats

import dev.antinode.core.model.Chain
import dev.antinode.core.model.Compressor
import dev.antinode.core.model.EffectId
import dev.antinode.core.model.Filter
import dev.antinode.core.model.FilterType
import dev.antinode.core.model.Limiter
import dev.antinode.core.model.ParametricEq
import dev.antinode.core.model.Preamp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Easy Effects output preset (.json). Reads the equalizer (left channel, or both when split), the
 * compressor and the limiter. Other plugins are listed as warnings.
 */
object EasyEffectsFormat {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val types = mapOf(
        "bell" to FilterType.PEAK,
        "lo-shelf" to FilterType.LOW_SHELF,
        "hi-shelf" to FilterType.HIGH_SHELF,
        "lo-pass" to FilterType.LOW_PASS,
        "hi-pass" to FilterType.HIGH_PASS,
        "notch" to FilterType.NOTCH,
        "bandpass" to FilterType.BAND_PASS,
    )

    fun read(text: String, name: String? = null): Imported {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }
            .getOrElse { throw ImportException("Not valid JSON") }
        val output = (root["output"] ?: root["input"])?.jsonObject
            ?: throw ImportException("No \"output\" section; is this an Easy Effects preset?")

        val warnings = mutableListOf<String>()
        val enabled = mutableSetOf<EffectId>()
        var chain = Chain(enabled = emptySet())

        plugin(output, "equalizer")?.let { eq ->
            val split = eq.bool("split-channels") == true
            val left = eq["left"]?.jsonObject
            val filters = left?.let { readBands(it, warnings) }.orEmpty()
            if (split) warnings += "Split L/R EQ: only the left channel was imported"
            val input = eq.num("input-gain") ?: 0.0
            val outputGain = eq.num("output-gain") ?: 0.0
            chain = chain.copy(preamp = Preamp(input + outputGain), peq = ParametricEq(filters, source = name))
            enabled += EffectId.PEQ
            enabled += EffectId.PREAMP
        }
        plugin(output, "compressor")?.let { c ->
            chain = chain.copy(
                compressor = Compressor(
                    thresholdDb = c.num("threshold") ?: -18.0,
                    ratio = c.num("ratio") ?: 2.5,
                    attackMs = c.num("attack") ?: 10.0,
                    releaseMs = c.num("release") ?: 200.0,
                ),
            )
            enabled += EffectId.COMPRESSOR
        }
        plugin(output, "limiter")?.let { l ->
            chain = chain.copy(limiter = Limiter((l.num("threshold") ?: -0.3).coerceIn(-12.0, 0.0)))
            enabled += EffectId.LIMITER
        }
        val known = setOf("equalizer", "compressor", "limiter", "blocklist", "plugins_order")
        output.keys.map { it.substringBefore('#') }.filter { it !in known }.distinct().forEach {
            warnings += "Easy Effects \"$it\" has no Android equivalent and was skipped"
        }
        if (EffectId.PEQ !in enabled && EffectId.COMPRESSOR !in enabled && EffectId.LIMITER !in enabled) {
            throw ImportException("No equalizer, compressor or limiter found in this preset")
        }
        return Imported(chain.copy(enabled = enabled), name, "Easy Effects", warnings)
    }

    private fun plugin(output: JsonObject, kind: String): JsonObject? =
        output.entries.firstOrNull { it.key == kind || it.key.startsWith("$kind#") }?.value as? JsonObject

    private fun readBands(channel: JsonObject, warnings: MutableList<String>): List<Filter> =
        channel.entries
            .filter { it.key.startsWith("band") }
            .sortedBy { it.key.removePrefix("band").toIntOrNull() ?: 0 }
            .mapNotNull { (_, v) ->
                val band = v.jsonObject
                val typeName = band["type"]?.jsonPrimitive?.content?.lowercase() ?: "bell"
                if (typeName == "off") return@mapNotNull null
                val type = types[typeName] ?: run {
                    warnings += "Band type \"$typeName\" imported as a bell filter"
                    FilterType.PEAK
                }
                Filter(
                    type = type,
                    freq = band.num("frequency") ?: return@mapNotNull null,
                    gain = band.num("gain") ?: 0.0,
                    q = band.num("q") ?: Filter.DEFAULT_Q,
                    enabled = band.bool("mute") != true,
                )
            }

    private fun JsonObject.num(key: String) = (this[key] as? JsonElement)?.jsonPrimitive?.doubleOrNull
    private fun JsonObject.bool(key: String) = (this[key] as? JsonElement)?.jsonPrimitive?.booleanOrNull
}
