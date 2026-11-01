package dev.circuiteq.core.autoeq

import dev.circuiteq.core.dsp.AutoEqGenerator
import dev.circuiteq.core.dsp.Curve
import dev.circuiteq.core.model.Filter
import dev.circuiteq.core.model.FilterType
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.Locale
import java.util.zip.GZIPInputStream

enum class HeadphoneType(val label: String) { IN_EAR("In-ear"), OVER_EAR("Over-ear"), EARBUD("Earbud") }

/** One AutoEQ profile: a headphone as measured by one source on one rig. */
class CatalogEntry(
    val name: String,
    val source: String,
    val rig: String,
    val type: HeadphoneType,
    /** AutoEQ's own precomputed result. */
    val preampDb: Double,
    val filters: List<Filter>,
    private val measurementCentiDb: ShortArray?,
) {
    val hasMeasurement: Boolean get() = measurementCentiDb != null

    val measurement: Curve? get() = measurementCentiDb?.let { s -> Curve(DoubleArray(s.size) { s[it] / 100.0 }) }

    val subtitle: String
        get() = listOf(source, rig.ifBlank { null }, type.label).filterNotNull().joinToString(" · ")

    internal val searchKey = normalise("$name $source")
}

class Target(val name: String, val curve: Curve, val custom: Boolean = false)

/** A target as offered for one entry: the generic name, resolved to the variant made for that rig if there is one. */
data class TargetChoice(val label: String, val target: Target, val recommended: Boolean)

internal fun normalise(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()

class AutoEqCatalog(val entries: List<CatalogEntry>, targets: List<Target>) {
    private val targetsByName = targets.associateBy { it.name }
    val targets: List<Target> = targets

    /** Every query word must appear; names that start with the query rank first. */
    fun search(query: String, limit: Int = 60): List<CatalogEntry> {
        val words = normalise(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val q = words.joinToString(" ")
        return entries.asSequence()
            .filter { e -> words.all { it in e.searchKey } }
            .sortedWith(
                compareBy<CatalogEntry> { if (normalise(it.name).startsWith(q)) 0 else 1 }
                    .thenBy { it.name.length }
                    .thenBy { it.name }
                    .thenBy { if (it.hasMeasurement) 0 else 1 },
            )
            .take(limit)
            .toList()
    }

    /** Targets that make sense for [entry], with rig-specific variants substituted. */
    fun targetsFor(entry: CatalogEntry, extra: List<Target> = emptyList()): List<TargetChoice> {
        val prefix = rigPrefix(entry)
        val recommended = recommendedTarget(entry)
        val generic = targets.filter { t -> RIG_PREFIXES.none { t.name.startsWith(it) } }
        val choices = generic.map { g ->
            val resolved = prefix?.let { targetsByName["$it${g.name}"] } ?: g
            TargetChoice(g.name, resolved, g.name == recommended)
        }
        return (choices.sortedByDescending { it.recommended } + extra.map { TargetChoice("Custom · ${it.name}", it, false) })
    }

    fun recommendedTarget(entry: CatalogEntry): String = when {
        "5128" in entry.rig -> "Diffuse field 5128 -1dB per octave"
        entry.type == HeadphoneType.OVER_EAR -> "Harman over-ear 2018"
        else -> "Harman in-ear 2019"
    }.takeIf { it in targetsByName } ?: targets.first().name

    private fun rigPrefix(e: CatalogEntry): String? = when {
        "HMS II.3" in e.rig -> "HMS II.3 "
        e.source.equals("crinacle", true) && "EARS" in e.rig -> "crinacle EARS + 711 "
        e.source.equals("Rtings", true) -> "Rtings "
        e.source.equals("Innerfidelity", true) -> "Innerfidelity "
        e.source.equals("Headphone.com Legacy", true) -> "Headphone.com Legacy "
        else -> null
    }

    companion object {
        private val RIG_PREFIXES = listOf("HMS II.3 ", "crinacle EARS + 711 ", "Rtings ", "Innerfidelity ", "Headphone.com Legacy ")

        private val TYPES = mapOf("in-ear" to HeadphoneType.IN_EAR, "over-ear" to HeadphoneType.OVER_EAR, "earbud" to HeadphoneType.EARBUD)
        private val FILTERS = mapOf(
            "PK" to FilterType.PEAK, "LSC" to FilterType.LOW_SHELF, "LS" to FilterType.LOW_SHELF,
            "HSC" to FilterType.HIGH_SHELF, "HS" to FilterType.HIGH_SHELF, "LP" to FilterType.LOW_PASS, "HP" to FilterType.HIGH_PASS,
        )

        /**
         * Reads the TSV files written by tools/catalog/build_catalog.py, gzipped or not (Android's packager
         * unpacks .gz assets).
         */
        fun read(catalog: InputStream, targets: InputStream): AutoEqCatalog {
            val entries = text(catalog).bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    val c = line.split('\t')
                    if (c.size < 7) return@mapNotNull null
                    CatalogEntry(
                        name = c[0], source = c[1], rig = c[2], type = TYPES[c[3]] ?: HeadphoneType.IN_EAR,
                        preampDb = c[4].toDoubleOrNull() ?: 0.0,
                        filters = c[5].split(';').mapNotNull { f ->
                            val p = f.split(',')
                            val type = FILTERS[p.getOrNull(0)] ?: return@mapNotNull null
                            Filter(type, p[1].toDouble(), p[2].toDouble(), p[3].toDouble())
                        },
                        measurementCentiDb = c[6].takeIf { it.isNotEmpty() }?.let(::decode),
                    )
                }.toList()
            }
            val targetList = text(targets).bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    val c = line.split('\t')
                    if (c.size < 2) null
                    else decode(c[1]).let { s -> Target(c[0], Curve(DoubleArray(s.size) { s[it] / 100.0 })) }
                }.toList()
            }
            return AutoEqCatalog(entries, targetList)
        }

        private fun text(input: InputStream): InputStream {
            val buffered = java.io.BufferedInputStream(input)
            buffered.mark(2)
            val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b
            buffered.reset()
            return if (gzip) GZIPInputStream(buffered) else buffered
        }

        private fun decode(b64: String): ShortArray {
            val bytes = Base64.getDecoder().decode(b64)
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            return ShortArray(AutoEqGenerator.GRID.size) { buf.get(it) }
        }

        /** Parses a "frequency,dB" CSV (squig.link / REW / AutoEQ export) into a curve on the standard grid. */
        fun parseCurveCsv(text: String): Curve? {
            val points = text.lineSequence().mapNotNull { line ->
                val parts = line.split(',', ';', '\t', ' ').map { it.trim() }.filter { it.isNotEmpty() }
                val f = parts.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
                val v = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                if (f > 0 && v.isFinite()) f to v else null
            }.sortedBy { it.first }.toList()
            if (points.size < 10) return null
            return Curve.resample(points.map { it.first }.toDoubleArray(), points.map { it.second }.toDoubleArray())
        }
    }
}
