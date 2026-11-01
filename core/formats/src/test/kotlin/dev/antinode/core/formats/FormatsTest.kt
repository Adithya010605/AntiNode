package dev.antinode.core.formats

import dev.antinode.core.model.EffectId
import dev.antinode.core.model.FilterType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FormatsTest {
    private fun resource(name: String) = javaClass.classLoader.getResource(name)!!.readText()

    @Test
    fun `reads the salnotes autoeq file`() {
        val imported = Importer.read(resource("salnotes.txt"), "7Hz Salnotes Zero 2 ParametricEq.txt")
        assertEquals("Equalizer APO", imported.format)
        assertEquals(-2.2, imported.chain.preamp.gainDb)
        val f = imported.chain.peq.filters
        assertEquals(10, f.size)
        assertEquals(FilterType.LOW_SHELF, f[0].type)
        assertEquals(105.0, f[0].freq); assertEquals(-3.1, f[0].gain); assertEquals(0.70, f[0].q)
        assertEquals(FilterType.PEAK, f[7].type); assertEquals(5957.1, f[7].freq); assertEquals(4.64, f[7].q)
        assertEquals(FilterType.HIGH_SHELF, f[9].type)
        assertEquals("7Hz Salnotes Zero 2 ParametricEq", imported.chain.peq.source)
        assertTrue(imported.chain.isOn(EffectId.PEQ))
    }

    @Test
    fun `apo export reads back identically`() {
        val original = Importer.read(resource("salnotes.txt"), "salnotes.txt").chain
        val again = ApoFormat.read(ApoFormat.write(original)).chain
        assertEquals(original.preamp, again.preamp)
        assertEquals(original.peq.filters, again.peq.filters)
    }

    @Test
    fun `reads bandwidth in octaves and filters with slopes`() {
        val text = """
            Preamp: -6 dB
            Filter 1: ON PK Fc 1000 Hz Gain 3 dB BW Oct 1.0
            Filter 2: ON LS 12dB Fc 80 Hz Gain 4 dB
            Filter 3: OFF HP Fc 20 Hz
        """.trimIndent()
        val f = ApoFormat.read(text).chain.peq.filters
        assertEquals(3, f.size)
        assertEquals(1.414, f[0].q, 0.001)
        assertEquals(FilterType.LOW_SHELF, f[1].type)
        assertEquals(false, f[2].enabled)
    }

    @Test
    fun `reads an easy effects preset`() {
        val imported = Importer.read(resource("easyeffects.json"), "warm.json")
        assertEquals("Easy Effects", imported.format)
        val c = imported.chain
        assertEquals(-3.0, c.preamp.gainDb)
        assertEquals(2, c.peq.filters.size) // "Off" band dropped
        assertEquals(FilterType.LOW_SHELF, c.peq.filters[0].type)
        assertEquals(-1.0, c.limiter.ceilingDb)
        assertTrue(c.isOn(EffectId.LIMITER))
        assertTrue(imported.warnings.any { "crossfeed" in it })
    }

    @Test
    fun `native format round trips`() {
        val chain = Importer.read(resource("salnotes.txt"), "salnotes.txt").chain.toggle(EffectId.BASS)
        val back = Importer.read(NativeFormat.write("Mine", chain), "mine.json")
        assertEquals("Mine", back.name)
        assertEquals(chain, back.chain)
    }

    @Test
    fun `reads presets exported under the old name`() {
        val legacy = NativeFormat.write("Old", dev.antinode.core.model.Chain()).replace("\"antinode\"", "\"circuitEq\"")
        assertEquals("Old", Importer.read(legacy, "old.json").name)
    }

    @Test
    fun `rejects unrelated files`() {
        assertFailsWith<ImportException> { Importer.read("hello world", "notes.txt") }
    }
}
