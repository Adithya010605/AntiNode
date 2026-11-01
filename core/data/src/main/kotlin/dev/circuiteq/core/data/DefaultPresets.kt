package dev.circuiteq.core.data

import dev.circuiteq.core.model.BassShelf
import dev.circuiteq.core.model.Chain
import dev.circuiteq.core.model.Compressor
import dev.circuiteq.core.model.EffectId.BASS
import dev.circuiteq.core.model.EffectId.COMPRESSOR
import dev.circuiteq.core.model.EffectId.LIMITER
import dev.circuiteq.core.model.EffectId.LOUDNESS
import dev.circuiteq.core.model.EffectId.PREAMP
import dev.circuiteq.core.model.EffectId.TILT
import dev.circuiteq.core.model.EffectId.VIRTUALIZER
import dev.circuiteq.core.model.Loudness
import dev.circuiteq.core.model.Preamp
import dev.circuiteq.core.model.Tilt

/** Sound-signature presets every new profile starts with. They work on any headphone. */
internal object DefaultPresets {
    data class Seed(val name: String, val description: String, val chain: Chain)

    val all = listOf(
        Seed("Flat", "Everything disconnected", Chain.FLAT),
        Seed(
            "Warm", "Tilt −0.6 dB/oct · limiter",
            Chain(enabled = setOf(PREAMP, TILT, LIMITER), preamp = Preamp(-1.0), tilt = Tilt(-0.6)),
        ),
        Seed(
            "V-shape", "Bass + treble lift",
            Chain(
                enabled = setOf(PREAMP, BASS, LOUDNESS, LIMITER), preamp = Preamp(-5.0),
                bass = BassShelf(3.0, 100.0), loudness = Loudness(60),
            ),
        ),
        Seed(
            "Bass head", "Sub-bass shelf +6 dB",
            Chain(enabled = setOf(PREAMP, BASS, LIMITER), preamp = Preamp(-6.0), bass = BassShelf(6.0, 90.0)),
        ),
        Seed(
            "Late night", "Compressor first · loudness · warm",
            Chain(
                enabled = setOf(PREAMP, COMPRESSOR, TILT, LOUDNESS, LIMITER), toneAfterDynamics = true,
                preamp = Preamp(-3.0), compressor = Compressor(thresholdDb = -24.0, ratio = 3.0),
                tilt = Tilt(-0.6), loudness = Loudness(70),
            ),
        ),
        Seed(
            "Wide stage", "Virtualizer · limiter",
            Chain(enabled = setOf(PREAMP, VIRTUALIZER, LIMITER), preamp = Preamp(-1.0)),
        ),
    )
}
