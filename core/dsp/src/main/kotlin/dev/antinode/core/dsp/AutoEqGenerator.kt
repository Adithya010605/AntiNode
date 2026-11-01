package dev.antinode.core.dsp

import dev.antinode.core.model.Filter
import dev.antinode.core.model.FilterType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A frequency response sampled on [AutoEqGenerator.GRID]. */
class Curve(val db: DoubleArray) {
    init { require(db.size == AutoEqGenerator.GRID.size) { "curve must be on the standard grid" } }

    companion object {
        /** Resamples (freq, dB) points onto the standard grid, interpolating in log-frequency. */
        fun resample(freqs: DoubleArray, values: DoubleArray): Curve {
            val g = AutoEqGenerator.GRID
            val out = DoubleArray(g.size)
            var j = 0
            for (i in g.indices) {
                val f = g[i]
                while (j < freqs.size - 2 && freqs[j + 1] < f) j++
                out[i] = when {
                    f <= freqs.first() -> values.first()
                    f >= freqs.last() -> values.last()
                    else -> {
                        val t = (ln(f) - ln(freqs[j])) / (ln(freqs[j + 1]) - ln(freqs[j]))
                        values[j] + t.coerceIn(0.0, 1.0) * (values[j + 1] - values[j])
                    }
                }
            }
            return Curve(out)
        }
    }
}

data class AutoEqOptions(
    /** Extra low shelf added to the target at 105 Hz, like AutoEQ's "bass boost". */
    val bassBoostDb: Double = 0.0,
    /** Tilts the target around 1 kHz; negative is warmer. */
    val tiltDbPerOctave: Double = 0.0,
    /** Largest boost any part of the EQ may apply. */
    val maxBoostDb: Double = 6.0,
    val peakingFilters: Int = 8,
)

data class AutoEqResult(
    val filters: List<Filter>,
    val preampDb: Double,
    /** What the headphone needs, after smoothing and boost limits, on [AutoEqGenerator.GRID]. */
    val desired: DoubleArray,
    /** What the fitted filters actually do. */
    val fitted: DoubleArray,
    /** RMS of (headphone − target) before and after EQ, 20 Hz–10 kHz. */
    val rmsBefore: Double,
    val rmsAfter: Double,
)

/**
 * Generates a parametric EQ that moves a measured headphone towards a target, following AutoEQ's method:
 * normalise, smooth the error (much more in the treble, where measurements are unreliable), limit boosts, then
 * fit a low shelf at 105 Hz, peaking filters and a high shelf at 10 kHz with Levenberg–Marquardt.
 */
object AutoEqGenerator {
    /** 192 log-spaced points, 20 Hz–20 kHz. */
    val GRID: DoubleArray = ToneCurve.logGrid(192)

    private val cos1 = DoubleArray(GRID.size)
    private val sin1 = DoubleArray(GRID.size)
    private val cos2 = DoubleArray(GRID.size)
    private val sin2 = DoubleArray(GRID.size)

    init {
        for (i in GRID.indices) {
            val w = 2 * PI * GRID[i] / DEFAULT_SAMPLE_RATE
            cos1[i] = cos(w); sin1[i] = -sin(w); cos2[i] = cos(2 * w); sin2[i] = -sin(2 * w)
        }
    }

    private const val SHELF_LOW = 105.0
    private const val SHELF_HIGH = 10_000.0
    private const val SHELF_Q = 0.7

    fun generate(measurement: Curve, target: Curve, options: AutoEqOptions = AutoEqOptions()): AutoEqResult {
        val n = GRID.size
        val goal = DoubleArray(n) { i ->
            val f = GRID[i]
            target.db[i] +
                Biquad.design(FilterType.LOW_SHELF, SHELF_LOW, options.bassBoostDb, SHELF_Q).magnitudeDb(f) +
                options.tiltDbPerOctave * log2(f / 1000.0)
        }
        // Align levels in the midrange so only the shape matters.
        val mid = GRID.indices.filter { GRID[it] in 300.0..3000.0 }
        val offset = mid.sumOf { goal[it] - measurement.db[it] } / mid.size
        val error = DoubleArray(n) { measurement.db[it] + offset - goal[it] }

        val smoothed = smoothError(error)
        val desired = DoubleArray(n) { i ->
            val eq = -smoothed[i]
            // Boosts are capped; treble boosts more strictly because ear canals vary most there.
            val cap = if (GRID[i] > 6000) min(options.maxBoostDb, 4.0) else options.maxBoostDb
            eq.coerceAtMost(cap)
        }

        val weights = DoubleArray(n) { i -> if (GRID[i] <= 10_000) 1.0 else if (GRID[i] <= 16_000) 0.4 else 0.1 }
        val fit = Fit(options.peakingFilters, desired, weights)
        fit.initialise()
        fit.optimise()

        val filters = fit.filters().map { it.rounded() }
        val response = DoubleArray(n) { i -> filters.sumOf { f -> mag(Biquad.of(f), i) } }
        val preamp = -(response.maxOrNull() ?: 0.0).coerceAtLeast(0.0)

        fun rms(values: DoubleArray) = sqrt(GRID.indices.filter { GRID[it] <= 10_000 }.sumOf { values[it] * values[it] } /
            GRID.count { it <= 10_000 })
        return AutoEqResult(
            filters = filters,
            preampDb = Math.round(preamp * 10) / 10.0,
            desired = desired,
            fitted = response,
            rmsBefore = rms(error),
            rmsAfter = rms(DoubleArray(n) { error[it] + response[it] }),
        )
    }

    /** 1/12-octave smoothing up to 6 kHz, 2-octave above 8 kHz, blended in between. */
    private fun smoothError(error: DoubleArray): DoubleArray {
        val fine = smooth(error, 1.0 / 12)
        val coarse = smooth(error, 2.0)
        return DoubleArray(error.size) { i ->
            val f = GRID[i]
            val t = ((log2(f) - log2(6000.0)) / (log2(8000.0) - log2(6000.0))).coerceIn(0.0, 1.0)
            fine[i] * (1 - t) + coarse[i] * t
        }
    }

    private fun smooth(values: DoubleArray, octaves: Double): DoubleArray {
        val pos = DoubleArray(GRID.size) { log2(GRID[it]) }
        return DoubleArray(values.size) { i ->
            var sum = 0.0; var weight = 0.0
            for (j in values.indices) {
                val d = (pos[j] - pos[i]) / (octaves / 2)
                if (abs(d) > 2.5) continue
                val w = exp(-d * d)
                sum += values[j] * w; weight += w
            }
            sum / weight
        }
    }

    private fun mag(b: Biquad, i: Int): Double {
        val nr = b.b0 + b.b1 * cos1[i] + b.b2 * cos2[i]
        val ni = b.b1 * sin1[i] + b.b2 * sin2[i]
        val dr = 1 + b.a1 * cos1[i] + b.a2 * cos2[i]
        val di = b.a1 * sin1[i] + b.a2 * sin2[i]
        return 10 * log10((nr * nr + ni * ni) / (dr * dr + di * di))
    }

    private fun Filter.rounded() = copy(
        freq = Math.round(freq * 10) / 10.0,
        gain = Math.round(gain * 10) / 10.0,
        q = Math.round(q * 100) / 100.0,
    )

    /**
     * Parameters: [0] low-shelf gain, [1] high-shelf gain, then per peaking filter log10(fc), gain, ln(q).
     */
    private class Fit(val peaks: Int, val desired: DoubleArray, val weights: DoubleArray) {
        val p = DoubleArray(2 + 3 * peaks)
        private val n = desired.size

        fun filters(params: DoubleArray = p): List<Filter> = buildList {
            add(Filter(FilterType.LOW_SHELF, SHELF_LOW, params[0], SHELF_Q))
            val sortedPeaks = (0 until peaks).map { k ->
                Filter(FilterType.PEAK, 10.0.pow(params[2 + 3 * k]), params[3 + 3 * k], exp(params[4 + 3 * k]))
            }.sortedBy { it.freq }
            addAll(sortedPeaks)
            add(Filter(FilterType.HIGH_SHELF, SHELF_HIGH, params[1], SHELF_Q))
        }

        private fun response(params: DoubleArray, out: DoubleArray) {
            out.fill(0.0)
            for (f in filters(params)) {
                val b = Biquad.of(f)
                for (i in 0 until n) out[i] += mag(b, i)
            }
        }

        private fun residuals(params: DoubleArray, out: DoubleArray) {
            response(params, out)
            for (i in 0 until n) out[i] = (out[i] - desired[i]) * weights[i]
        }

        private fun cost(r: DoubleArray) = r.sumOf { it * it }

        private fun clamp(params: DoubleArray) {
            params[0] = params[0].coerceIn(-12.0, 12.0)
            params[1] = params[1].coerceIn(-12.0, 12.0)
            for (k in 0 until peaks) {
                params[2 + 3 * k] = params[2 + 3 * k].coerceIn(log10(20.0), log10(10_000.0))
                params[3 + 3 * k] = params[3 + 3 * k].coerceIn(-12.0, 12.0)
                params[4 + 3 * k] = params[4 + 3 * k].coerceIn(ln(0.18), ln(6.0))
            }
        }

        /** Shelves from the ends of the curve, then peaks greedily on the largest remaining deviation. */
        fun initialise() {
            val low = GRID.indices.filter { GRID[it] < 60 }
            val high = GRID.indices.filter { GRID[it] in 11_000.0..16_000.0 }
            p[0] = (low.sumOf { desired[it] } / low.size).coerceIn(-12.0, 12.0)
            p[1] = (high.sumOf { desired[it] } / high.size).coerceIn(-12.0, 12.0)
            val residual = DoubleArray(n)
            for (k in 0 until peaks) {
                p[2 + 3 * k] = log10(1000.0); p[3 + 3 * k] = 0.0; p[4 + 3 * k] = ln(1.4)
            }
            for (k in 0 until peaks) {
                response(p, residual)
                var best = -1; var bestAbs = 0.0
                for (i in 0 until n) {
                    if (GRID[i] < 20 || GRID[i] > 10_000) continue
                    val d = desired[i] - residual[i]
                    if (abs(d) > bestAbs) { bestAbs = abs(d); best = i }
                }
                if (best < 0) break
                p[2 + 3 * k] = log10(GRID[best])
                p[3 + 3 * k] = (desired[best] - residual[best]).coerceIn(-12.0, 12.0)
                p[4 + 3 * k] = ln(1.4)
            }
            clamp(p)
        }

        /** Levenberg–Marquardt with forward-difference Jacobian. */
        fun optimise(iterations: Int = 150) {
            val m = p.size
            val r = DoubleArray(n)
            val rTrial = DoubleArray(n)
            val jac = Array(m) { DoubleArray(n) }
            val trial = DoubleArray(m)
            residuals(p, r)
            var c = cost(r)
            var lambda = 1e-2
            repeat(iterations) {
                for (a in 0 until m) {
                    val h = if (a < 2 || (a - 2) % 3 == 1) 1e-3 else 1e-4
                    p.copyInto(trial); trial[a] += h
                    residuals(trial, rTrial)
                    for (i in 0 until n) jac[a][i] = (rTrial[i] - r[i]) / h
                }
                val jtj = Array(m) { a -> DoubleArray(m) { b -> var s = 0.0; for (i in 0 until n) s += jac[a][i] * jac[b][i]; s } }
                val jtr = DoubleArray(m) { a -> var s = 0.0; for (i in 0 until n) s += jac[a][i] * r[i]; s }
                var improved = false
                repeat(8) {
                    if (improved) return@repeat
                    val aMat = Array(m) { a -> DoubleArray(m) { b -> jtj[a][b] + if (a == b) lambda * max(jtj[a][a], 1e-9) else 0.0 } }
                    val step = solve(aMat, DoubleArray(m) { -jtr[it] }) ?: run { lambda *= 4; return@repeat }
                    for (a in 0 until m) trial[a] = p[a] + step[a]
                    clamp(trial)
                    residuals(trial, rTrial)
                    val ct = cost(rTrial)
                    if (ct < c) {
                        val gain = (c - ct) / c
                        trial.copyInto(p); rTrial.copyInto(r); c = ct
                        lambda = max(lambda / 3, 1e-7)
                        improved = true
                        if (gain < 1e-7) return
                    } else {
                        lambda *= 4
                    }
                }
                if (!improved) return
            }
        }

        private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
            val m = b.size
            for (col in 0 until m) {
                var pivot = col
                for (row in col + 1 until m) if (abs(a[row][col]) > abs(a[pivot][col])) pivot = row
                if (abs(a[pivot][col]) < 1e-12) return null
                if (pivot != col) {
                    val t = a[col]; a[col] = a[pivot]; a[pivot] = t
                    val tb = b[col]; b[col] = b[pivot]; b[pivot] = tb
                }
                for (row in col + 1 until m) {
                    val factor = a[row][col] / a[col][col]
                    for (k in col until m) a[row][k] -= factor * a[col][k]
                    b[row] -= factor * b[col]
                }
            }
            val x = DoubleArray(m)
            for (row in m - 1 downTo 0) {
                var s = b[row]
                for (k in row + 1 until m) s -= a[row][k] * x[k]
                x[row] = s / a[row][row]
            }
            return x
        }
    }
}
