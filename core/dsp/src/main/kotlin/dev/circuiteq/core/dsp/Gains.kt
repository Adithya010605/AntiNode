package dev.circuiteq.core.dsp

import kotlin.math.log10

object Gains {
    /** Preamp that keeps the loudest boost of [curveDb] at or below 0 dB. */
    fun autoPreampDb(curveDb: (Double) -> Double, points: Int = 512): Double {
        val peak = ToneCurve.logGrid(points).maxOf(curveDb)
        return if (peak > 0) -peak else 0.0
    }

    /** Per-channel attenuation for a balance of -100 (left) .. 100 (right). Returns (left, right) in dB. */
    fun balanceDb(balance: Int): Pair<Double, Double> {
        val b = balance.coerceIn(-100, 100)
        val cut = if (b == 0) 0.0 else (20 * log10(1 - kotlin.math.abs(b) / 100.0)).coerceAtLeast(-60.0)
        return if (b > 0) cut to 0.0 else 0.0 to cut
    }
}
