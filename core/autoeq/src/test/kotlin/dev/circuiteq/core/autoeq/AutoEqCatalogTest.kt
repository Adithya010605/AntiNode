package dev.circuiteq.core.autoeq

import dev.circuiteq.core.dsp.AutoEqGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AutoEqCatalogTest {
    private val catalog by lazy {
        val cl = javaClass.classLoader
        AutoEqCatalog.read(cl.getResourceAsStream("catalog.tsv.gz")!!, cl.getResourceAsStream("targets.tsv.gz")!!)
    }

    @Test
    fun `loads the bundled database`() {
        assertTrue(catalog.entries.size > 8000, "entries: ${catalog.entries.size}")
        assertTrue(catalog.entries.count { it.hasMeasurement } > 6000)
        assertTrue(catalog.targets.any { it.name == "Harman in-ear 2019" })
    }

    @Test
    fun `finds the salnotes zero 2`() {
        val hits = catalog.search("salnotes zero 2")
        assertTrue(hits.isNotEmpty())
        assertEquals("7Hz Salnotes Zero 2", hits.first().name)
        assertEquals(10, hits.first().filters.size)
    }

    @Test
    fun `rtings over-ear uses the rtings variant of harman`() {
        val e = catalog.entries.first { it.source == "Rtings" && it.type == HeadphoneType.OVER_EAR && "HMS" !in it.rig }
        val choice = catalog.targetsFor(e).first { it.label == "Harman over-ear 2018" }
        assertEquals("Rtings Harman over-ear 2018", choice.target.name)
        assertTrue(catalog.targetsFor(e).first().recommended)
    }

    @Test
    fun `generates an eq for a measured entry in reasonable time`() {
        val e = catalog.search("salnotes zero").first { it.hasMeasurement }
        val target = catalog.targetsFor(e).first { it.recommended }.target
        val started = System.nanoTime()
        val result = AutoEqGenerator.generate(assertNotNull(e.measurement), target.curve)
        val ms = (System.nanoTime() - started) / 1e6
        println("${e.name} (${e.subtitle}) → ${target.name}: ${result.rmsBefore} → ${result.rmsAfter} dB in $ms ms")
        assertTrue(result.rmsAfter < result.rmsBefore)
    }

    @Test
    fun `reads a squig style csv`() {
        val csv = (0..40).joinToString("\n") { i -> "${20 * Math.pow(1000.0, i / 40.0)},${i * 0.1}" }
        assertNotNull(AutoEqCatalog.parseCurveCsv("frequency,raw\n$csv"))
    }
}
