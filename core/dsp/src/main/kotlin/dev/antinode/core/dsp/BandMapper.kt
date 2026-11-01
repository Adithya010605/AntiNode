package dev.antinode.core.dsp

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/** One band of Android's DynamicsProcessing EQ: it covers frequencies up to [cutoffHz]. */
data class EqBand(val cutoffHz: Double, val gainDb: Double)

/**
 * Converts any EQ curve into Android's fixed-band EQ. Bands are log-spaced from 20 Hz to 20 kHz; each band
 * gets the curve's value at its geometric centre. The last band extends to Nyquist.
 */
object BandMapper {
    const val MIN_FREQ = 20.0
    const val MAX_FREQ = 20_000.0

    fun edges(count: Int): DoubleArray {
        require(count >= 2) { "need at least 2 bands" }
        val ratio = MAX_FREQ / MIN_FREQ
        return DoubleArray(count) { i -> MIN_FREQ * ratio.pow((i + 1.0) / count) }
    }

    fun map(count: Int, curveDb: (Double) -> Double): List<EqBand> {
        val e = edges(count)
        return List(count) { i ->
            val lower = if (i == 0) MIN_FREQ * MIN_FREQ / e[0] else e[i - 1]
            val centre = sqrt(lower * e[i])
            EqBand(cutoffHz = e[i], gainDb = curveDb(centre))
        }
    }

    /**
     * Worst-case deviation between the true curve and the band approximation, assuming the engine blends
     * smoothly between band centres (linear in log-frequency).
     */
    fun maxErrorDb(bands: List<EqBand>, curveDb: (Double) -> Double, probes: Int = 2000): Double {
        val centres = DoubleArray(bands.size) { i ->
            val lower = if (i == 0) MIN_FREQ * MIN_FREQ / bands[0].cutoffHz else bands[i - 1].cutoffHz
            sqrt(lower * bands[i].cutoffHz)
        }
        var worst = 0.0
        for (f in ToneCurve.logGrid(probes, centres.first(), centres.last())) {
            val j = centres.indexOfFirst { it >= f }.coerceAtLeast(1)
            val t = (ln(f) - ln(centres[j - 1])) / (ln(centres[j]) - ln(centres[j - 1]))
            val approx = bands[j - 1].gainDb + t * (bands[j].gainDb - bands[j - 1].gainDb)
            worst = maxOf(worst, abs(approx - curveDb(f)))
        }
        return worst
    }
}
