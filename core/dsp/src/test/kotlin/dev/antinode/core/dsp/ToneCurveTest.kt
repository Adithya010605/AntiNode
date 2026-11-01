package dev.antinode.core.dsp

import dev.antinode.core.model.Chain
import dev.antinode.core.model.EffectId
import dev.antinode.core.model.Filter
import dev.antinode.core.model.FilterType
import dev.antinode.core.model.ParametricEq
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 7Hz Salnotes Zero 2 AutoEQ profile. */
val SALNOTES = ParametricEq(
    listOf(
        Filter(FilterType.LOW_SHELF, 105.0, -3.1, 0.70),
        Filter(FilterType.PEAK, 61.9, 2.1, 1.05),
        Filter(FilterType.PEAK, 175.4, -2.1, 1.05),
        Filter(FilterType.PEAK, 731.0, 1.1, 1.16),
        Filter(FilterType.PEAK, 1220.4, -0.5, 2.23),
        Filter(FilterType.PEAK, 2680.1, 2.2, 1.44),
        Filter(FilterType.PEAK, 4260.3, -1.3, 4.02),
        Filter(FilterType.PEAK, 5957.1, 2.5, 4.64),
        Filter(FilterType.PEAK, 8867.9, -2.3, 1.80),
        Filter(FilterType.HIGH_SHELF, 10000.0, 0.2, 0.70),
    ),
)

class ToneCurveTest {
    private val peqOnly = Chain(enabled = setOf(EffectId.PEQ), peq = SALNOTES)

    @Test
    fun `salnotes curve matches numpy reference`() {
        // Computed independently with numpy (RBJ cookbook, 48 kHz).
        val reference = mapOf(
            20.0 to -2.8950, 61.9 to -0.9111, 100.0 to -1.5033, 175.4 to -2.1489, 1000.0 to 0.5502,
            2680.1 to 2.1253, 5957.1 to 2.0820, 8867.9 to -2.0439, 15000.0 to 0.0161,
        )
        val curve = ToneCurve.of(peqOnly)
        for ((f, db) in reference) assertEquals(db, curve.totalDb(f), 0.001, "at $f Hz")
    }

    @Test
    fun `band approximation of the salnotes curve stays inaudible`() {
        val curve = ToneCurve.of(peqOnly)
        // Expected worst-case deviation per band count (see PLAN.md accuracy table).
        for ((count, limit) in listOf(64 to 0.6, 128 to 0.25)) {
            val bands = BandMapper.map(count, curve::totalDb)
            assertEquals(count, bands.size)
            val err = BandMapper.maxErrorDb(bands, curve::totalDb)
            assertTrue(err < limit, "$count bands: max error $err dB")
        }
    }

    @Test
    fun `disabled effects do not shape the curve`() {
        val curve = ToneCurve.of(Chain(enabled = emptySet(), peq = SALNOTES))
        assertTrue(curve.isFlat)
        assertEquals(0.0, curve.totalDb(1000.0), 1e-9)
    }

    @Test
    fun `tilt is zero at 1 kHz and slopes per octave`() {
        val curve = ToneCurve.of(Chain(enabled = setOf(EffectId.TILT), tilt = dev.antinode.core.model.Tilt(-1.0)))
        assertEquals(0.0, curve.totalDb(1000.0), 1e-9)
        assertEquals(-1.0, curve.totalDb(2000.0), 1e-9)
        assertEquals(1.0, curve.totalDb(500.0), 1e-9)
    }

    @Test
    fun `auto preamp cancels the largest boost`() {
        val curve = ToneCurve.of(peqOnly)
        val pre = Gains.autoPreampDb(curve::totalDb)
        assertTrue(abs(pre + 2.13) < 0.05, "was $pre")
    }

    @Test
    fun `balance attenuates the opposite channel`() {
        assertEquals(0.0 to 0.0, Gains.balanceDb(0))
        val (l, r) = Gains.balanceDb(50)
        assertEquals(-6.02, l, 0.01); assertEquals(0.0, r)
    }
}
