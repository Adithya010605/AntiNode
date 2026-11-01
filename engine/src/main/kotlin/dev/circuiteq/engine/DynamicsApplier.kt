package dev.circuiteq.engine

import android.media.audiofx.DynamicsProcessing
import dev.circuiteq.core.dsp.BandMapper
import dev.circuiteq.core.dsp.Gains
import dev.circuiteq.core.dsp.ToneCurve
import dev.circuiteq.core.model.Chain
import dev.circuiteq.core.model.EffectId
import kotlin.math.pow

/** Builds DynamicsProcessing instances and writes a [Chain] into them. */
internal object DynamicsApplier {
    const val CHANNELS = 2
    const val MBC_BANDS = 5

    /** Tries the finest EQ first and falls back if the phone refuses. */
    private val BAND_COUNTS = intArrayOf(128, 64, 32, 16)

    fun create(sessionId: Int, priority: Int): Pair<DynamicsProcessing, Int>? {
        for (bands in BAND_COUNTS) {
            val cfg = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, CHANNELS,
                true, bands, true, MBC_BANDS, true, bands, true,
            ).setPreferredFrameDuration(10f).build()
            val dp = runCatching { DynamicsProcessing(priority, sessionId, cfg) }.getOrNull()
            if (dp != null) return dp to bands
        }
        return null
    }

    fun apply(dp: DynamicsProcessing, bands: Int, chain: Chain) {
        // Input: preamp + balance per channel.
        val pre = if (chain.isOn(EffectId.PREAMP)) chain.preamp.gainDb else 0.0
        val (l, r) = if (chain.isOn(EffectId.BALANCE)) Gains.balanceDb(chain.balance.value) else 0.0 to 0.0
        dp.setInputGainbyChannel(0, (pre + l).toFloat())
        dp.setInputGainbyChannel(1, (pre + r).toFloat())

        // Tone: one summed curve written into the pre- or post-EQ slot.
        val curve = ToneCurve.of(chain)
        val toneEq = eq(bands, enabled = !curve.isFlat) { BandMapper.map(bands, curve::totalDb) }
        val flatEq = eq(bands, enabled = false) { BandMapper.map(bands) { 0.0 } }
        if (chain.toneAfterDynamics) {
            dp.setPreEqAllChannelsTo(flatEq); dp.setPostEqAllChannelsTo(toneEq)
        } else {
            dp.setPreEqAllChannelsTo(toneEq); dp.setPostEqAllChannelsTo(flatEq)
        }

        dp.setMbcAllChannelsTo(mbc(chain))

        val lim = chain.isOn(EffectId.LIMITER)
        dp.setLimiterAllChannelsTo(
            DynamicsProcessing.Limiter(true, lim, 0, 1f, 60f, 10f, chain.limiter.ceilingDb.toFloat(), 0f),
        )
    }

    private inline fun eq(count: Int, enabled: Boolean, bands: () -> List<dev.circuiteq.core.dsp.EqBand>): DynamicsProcessing.Eq {
        val eq = DynamicsProcessing.Eq(true, enabled, count)
        bands().forEachIndexed { i, b -> eq.setBand(i, DynamicsProcessing.EqBand(true, b.cutoffHz.toFloat(), b.gainDb.toFloat())) }
        return eq
    }

    /**
     * Compressor, multiband and gate share Android's multiband compressor. A single compressor uses the same
     * settings on every band; multiband splits into [dev.circuiteq.core.model.Multiband.bands] bands.
     */
    private fun mbc(chain: Chain): DynamicsProcessing.Mbc {
        val comp = chain.isOn(EffectId.COMPRESSOR)
        val multi = chain.isOn(EffectId.MULTIBAND)
        val gate = chain.isOn(EffectId.GATE)
        val mbc = DynamicsProcessing.Mbc(true, comp || multi || gate, MBC_BANDS)

        val split = if (multi) chain.multiband.bands.coerceIn(2, MBC_BANDS) else 1
        // Crossovers log-spaced between 100 Hz and 10 kHz; bands past `split` repeat the last one up to 20 kHz.
        val crossovers = List(split - 1) { i -> 100.0 * 100.0.pow((i + 1.0) / split) }
        val c = chain.compressor
        val multiRatio = 1 + chain.multiband.amount.coerceIn(0, 100) / 100.0 * 3
        val ratio = when {
            comp && multi -> maxOf(c.ratio, multiRatio)
            comp -> c.ratio
            multi -> multiRatio
            else -> 1.0
        }
        val threshold = if (comp) c.thresholdDb else if (multi) -24.0 else 0.0
        for (i in 0 until MBC_BANDS) {
            val cutoff = crossovers.getOrNull(i) ?: (20_000.0 + i)
            mbc.setBand(
                i,
                DynamicsProcessing.MbcBand(
                    true, cutoff.toFloat(),
                    c.attackMs.toFloat(), c.releaseMs.toFloat(), ratio.toFloat(), threshold.toFloat(), 6f,
                    if (gate) chain.gate.thresholdDb.toFloat() else -90f, if (gate) 4f else 1f,
                    0f, 0f,
                ),
            )
        }
        return mbc
    }
}
