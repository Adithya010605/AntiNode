package dev.circuiteq.core.dsp

import dev.circuiteq.core.model.Chain
import dev.circuiteq.core.model.EffectId
import dev.circuiteq.core.model.FilterType
import dev.circuiteq.core.model.ParametricEq
import kotlin.math.log2
import kotlin.math.pow

/**
 * The tone stage is one EQ curve: parametric EQ, bass shelf, tilt and loudness are summed (in dB)
 * before being written into Android's EQ bands.
 */
class ToneCurve private constructor(
    private val peq: List<Biquad>,
    private val extras: List<Biquad>,
    private val tiltDbPerOctave: Double,
) {
    fun peqDb(freq: Double): Double = peq.sumOf { it.magnitudeDb(freq) }

    fun totalDb(freq: Double): Double =
        peqDb(freq) + extras.sumOf { it.magnitudeDb(freq) } + tiltDbPerOctave * log2(freq / 1000.0)

    val isFlat: Boolean get() = peq.isEmpty() && extras.isEmpty() && tiltDbPerOctave == 0.0

    companion object {
        fun of(chain: Chain): ToneCurve {
            val peq = if (chain.isOn(EffectId.PEQ)) biquads(chain.peq) else emptyList()
            val extras = buildList {
                if (chain.isOn(EffectId.BASS)) {
                    add(Biquad.design(FilterType.LOW_SHELF, chain.bass.freq, chain.bass.gainDb, 0.7))
                }
                if (chain.isOn(EffectId.LOUDNESS)) {
                    val a = chain.loudness.amount.coerceIn(0, 100) / 100.0
                    add(Biquad.design(FilterType.LOW_SHELF, 90.0, 6.0 * a, 0.7))
                    add(Biquad.design(FilterType.HIGH_SHELF, 9000.0, 3.0 * a, 0.7))
                }
            }
            val tilt = if (chain.isOn(EffectId.TILT)) chain.tilt.dbPerOctave else 0.0
            return ToneCurve(peq, extras, tilt)
        }

        fun biquads(eq: ParametricEq): List<Biquad> = eq.filters.filter { it.enabled }.map(Biquad::of)

        /** Log-spaced frequencies from [from] to [to] Hz inclusive. */
        fun logGrid(points: Int, from: Double = 20.0, to: Double = 20_000.0): DoubleArray {
            val ratio = to / from
            return DoubleArray(points) { i -> from * ratio.pow(i / (points - 1.0)) }
        }
    }
}
