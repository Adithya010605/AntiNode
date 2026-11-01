package dev.circuiteq.core.dsp

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoEqGeneratorTest {
    /** AutoEQ's own result for 7Hz Salnotes Zero (oratory1990): measurement, target and its parametric EQ. */
    private val rows = javaClass.classLoader.getResource("salnotes_zero_autoeq.csv")!!.readText()
        .lines().drop(1).filter { it.isNotBlank() }.map { l -> l.split(',').map(String::toDouble) }
    private val freqs = rows.map { it[0] }.toDoubleArray()
    private fun column(i: Int) = Curve.resample(freqs, rows.map { it[i] }.toDoubleArray())

    @Test
    fun `fit matches AutoEQ's own parametric EQ`() {
        val result = AutoEqGenerator.generate(column(1), column(2))
        val reference = column(3).db
        val band = AutoEqGenerator.GRID.indices.filter { AutoEqGenerator.GRID[it] in 20.0..10_000.0 }
        val diffs = band.map { abs(result.fitted[it] - reference[it]) }
        val rms = sqrt(band.sumOf { (result.fitted[it] - reference[it]).let { d -> d * d } } / band.size)
        println("filters=${result.filters} preamp=${result.preampDb} rmsBefore=${result.rmsBefore} rmsAfter=${result.rmsAfter} maxDiff=${diffs.max()} rmsDiff=$rms")
        // How well AutoEQ's own filters correct the same measurement, scored the same way.
        val raw = column(1).db; val target = column(2).db
        val mid = AutoEqGenerator.GRID.indices.filter { AutoEqGenerator.GRID[it] in 300.0..3000.0 }
        val offset = mid.sumOf { target[it] - raw[it] } / mid.size
        val autoEqAfter = sqrt(band.sumOf { (raw[it] + offset - target[it] + reference[it]).let { d -> d * d } } / band.size)
        println("autoEqAfter=$autoEqAfter")
        assertEquals(10, result.filters.size)
        assertTrue(result.rmsAfter < result.rmsBefore * 0.65, "EQ should clearly reduce the error")
        assertTrue(result.rmsAfter <= autoEqAfter + 0.1, "ours ${result.rmsAfter} dB vs AutoEQ $autoEqAfter dB")
        assertTrue(rms < 0.6, "rms difference to AutoEQ $rms dB")
        assertTrue(diffs.max() < 2.0, "max difference to AutoEQ ${diffs.max()} dB")
    }

    @Test
    fun `flat headphone on its own target needs no eq`() {
        val flat = Curve(DoubleArray(AutoEqGenerator.GRID.size))
        val result = AutoEqGenerator.generate(flat, flat)
        assertTrue(result.fitted.all { abs(it) < 0.3 })
        assertEquals(0.0, result.preampDb, 0.3)
    }

    @Test
    fun `bass boost raises the low end`() {
        val plain = AutoEqGenerator.generate(column(1), column(2))
        val boosted = AutoEqGenerator.generate(column(1), column(2), AutoEqOptions(bassBoostDb = 6.0))
        val i30 = AutoEqGenerator.GRID.indexOfFirst { it >= 30 }
        assertTrue(boosted.fitted[i30] > plain.fitted[i30] + 3)
    }
}
