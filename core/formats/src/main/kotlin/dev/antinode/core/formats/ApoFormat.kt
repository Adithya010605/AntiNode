package dev.antinode.core.formats

import dev.antinode.core.model.Chain
import dev.antinode.core.model.EffectId
import dev.antinode.core.model.Filter
import dev.antinode.core.model.FilterType
import dev.antinode.core.model.ParametricEq
import dev.antinode.core.model.Preamp
import java.util.Locale

/** Equalizer APO config / AutoEQ "ParametricEQ.txt" (also what squig.link and REW export). */
object ApoFormat {
    private val preampRe = Regex("""^\s*Preamp\s*:\s*(-?[\d.]+)\s*dB""", RegexOption.IGNORE_CASE)
    private val filterRe = Regex(
        """^\s*Filter\s*\d*\s*:\s*(ON|OFF)\s+([A-Z]+)(?:\s+\d+\s*dB)?\s+Fc\s+([\d.]+)\s*Hz""" +
            """(?:\s+Gain\s+(-?[\d.]+)\s*dB)?(?:\s+Q\s+([\d.]+))?(?:\s+BW\s+Oct\s+([\d.]+))?""",
        RegexOption.IGNORE_CASE,
    )

    private val types = mapOf(
        "PK" to FilterType.PEAK, "PEQ" to FilterType.PEAK, "MODAL" to FilterType.PEAK,
        "LSC" to FilterType.LOW_SHELF, "LS" to FilterType.LOW_SHELF, "LSQ" to FilterType.LOW_SHELF,
        "HSC" to FilterType.HIGH_SHELF, "HS" to FilterType.HIGH_SHELF, "HSQ" to FilterType.HIGH_SHELF,
        "LP" to FilterType.LOW_PASS, "LPQ" to FilterType.LOW_PASS,
        "HP" to FilterType.HIGH_PASS, "HPQ" to FilterType.HIGH_PASS,
        "NO" to FilterType.NOTCH, "BP" to FilterType.BAND_PASS,
    )

    fun looksLikeApo(text: String) = text.lineSequence().any { filterRe.containsMatchIn(it) || preampRe.containsMatchIn(it) }

    fun read(text: String, name: String? = null): Imported {
        var preamp: Double? = null
        val filters = mutableListOf<Filter>()
        val warnings = mutableListOf<String>()
        for (line in text.lineSequence()) {
            if (line.trimStart().startsWith("#")) continue
            preampRe.find(line)?.let { preamp = it.groupValues[1].toDouble(); return@let }
            val m = filterRe.find(line) ?: continue
            val (state, code, fc, gain, q, bw) = m.destructured
            val type = types[code.uppercase()]
            if (type == null) {
                warnings += "Skipped unsupported filter type $code"
                continue
            }
            val qValue = when {
                q.isNotEmpty() -> q.toDouble()
                bw.isNotEmpty() -> octavesToQ(bw.toDouble())
                else -> Filter.DEFAULT_Q
            }
            filters += Filter(
                type = type,
                freq = fc.toDouble(),
                gain = gain.ifEmpty { "0" }.toDouble(),
                q = qValue,
                enabled = state.equals("ON", ignoreCase = true),
            )
        }
        if (filters.isEmpty() && preamp == null) throw ImportException("No Preamp or Filter lines found")
        val chain = Chain(
            enabled = setOf(EffectId.PREAMP, EffectId.PEQ, EffectId.LIMITER),
            preamp = Preamp(preamp ?: 0.0),
            peq = ParametricEq(filters, source = name),
        )
        return Imported(chain, name, "Equalizer APO", warnings)
    }

    fun write(chain: Chain): String = buildString {
        appendLine("Preamp: ${fmt(chain.preamp.gainDb)} dB")
        chain.peq.filters.forEachIndexed { i, f ->
            append("Filter ${i + 1}: ${if (f.enabled) "ON" else "OFF"} ${f.type.apoCode} Fc ${fmt(f.freq, 1)} Hz")
            if (f.type.usesGain) append(" Gain ${fmt(f.gain)} dB")
            appendLine(" Q ${fmt(f.q)}")
        }
    }

    /** Bandwidth in octaves → Q. */
    fun octavesToQ(n: Double): Double {
        val p = Math.pow(2.0, n)
        return Math.sqrt(p) / (p - 1)
    }

    private fun fmt(v: Double, digits: Int = 2) = String.format(Locale.US, "%.${digits}f", v)
}
