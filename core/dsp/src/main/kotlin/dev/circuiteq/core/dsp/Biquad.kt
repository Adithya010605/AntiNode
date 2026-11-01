package dev.circuiteq.core.dsp

import dev.circuiteq.core.model.Filter
import dev.circuiteq.core.model.FilterType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

const val DEFAULT_SAMPLE_RATE = 48_000.0

/** Second-order IIR section with RBJ "Audio EQ Cookbook" coefficients, normalised so a0 = 1. */
data class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {

    /** Magnitude response in dB at [freq] Hz. */
    fun magnitudeDb(freq: Double, sampleRate: Double = DEFAULT_SAMPLE_RATE): Double {
        val w = 2 * PI * freq / sampleRate
        val c1 = cos(w); val s1 = -sin(w)
        val c2 = cos(2 * w); val s2 = -sin(2 * w)
        val nr = b0 + b1 * c1 + b2 * c2
        val ni = b1 * s1 + b2 * s2
        val dr = 1 + a1 * c1 + a2 * c2
        val di = a1 * s1 + a2 * s2
        return 10 * log10((nr * nr + ni * ni) / (dr * dr + di * di))
    }

    companion object {
        val IDENTITY = Biquad(1.0, 0.0, 0.0, 0.0, 0.0)

        fun of(filter: Filter, sampleRate: Double = DEFAULT_SAMPLE_RATE): Biquad =
            design(filter.type, filter.freq, filter.gain, filter.q, sampleRate)

        fun design(
            type: FilterType,
            freq: Double,
            gainDb: Double,
            q: Double,
            sampleRate: Double = DEFAULT_SAMPLE_RATE,
        ): Biquad {
            val f0 = freq.coerceIn(1.0, sampleRate / 2 * 0.999)
            val a = 10.0.pow(gainDb / 40)
            val w = 2 * PI * f0 / sampleRate
            val c = cos(w)
            val alpha = sin(w) / (2 * q.coerceAtLeast(0.01))
            val sq = 2 * sqrt(a) * alpha
            return when (type) {
                FilterType.PEAK -> normalise(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
                FilterType.LOW_SHELF -> normalise(
                    a * ((a + 1) - (a - 1) * c + sq), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - sq),
                    (a + 1) + (a - 1) * c + sq, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - sq,
                )
                FilterType.HIGH_SHELF -> normalise(
                    a * ((a + 1) + (a - 1) * c + sq), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - sq),
                    (a + 1) - (a - 1) * c + sq, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - sq,
                )
                FilterType.LOW_PASS -> normalise((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
                FilterType.HIGH_PASS -> normalise((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
                FilterType.NOTCH -> normalise(1.0, -2 * c, 1.0, 1 + alpha, -2 * c, 1 - alpha)
                FilterType.BAND_PASS -> normalise(alpha, 0.0, -alpha, 1 + alpha, -2 * c, 1 - alpha)
            }
        }

        private fun normalise(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
            Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }
}
