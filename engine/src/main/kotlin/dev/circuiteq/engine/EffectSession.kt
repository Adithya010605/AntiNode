package dev.circuiteq.engine

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log
import dev.circuiteq.core.model.Chain
import dev.circuiteq.core.model.EffectId

/** All effects attached to one audio session. */
internal class EffectSession(val id: Int, val packageName: String) {
    private var dp: DynamicsProcessing? = null
    private var virtualizer: Virtualizer? = null
    private var reverb: PresetReverb? = null
    var bands = 0; private set

    val created get() = dp != null
    val hasControl get() = runCatching { dp?.hasControl() == true }.getOrDefault(false)

    fun create(): Boolean {
        if (dp != null) return true
        val made = DynamicsApplier.create(id, PRIORITY) ?: return false.also { Log.w(TAG, "DynamicsProcessing refused for session $id") }
        dp = made.first; bands = made.second
        return true
    }

    /** Applies [chain], or disables everything when it is null (bypass). */
    fun apply(chain: Chain?) {
        val d = dp ?: return
        runCatching {
            if (chain == null) {
                d.enabled = false
            } else {
                DynamicsApplier.apply(d, bands, chain)
                d.enabled = true
            }
        }.onFailure { Log.w(TAG, "apply failed on session $id", it) }
        applyVirtualizer(chain)
        applyReverb(chain)
    }

    private fun applyVirtualizer(chain: Chain?) {
        val on = chain?.isOn(EffectId.VIRTUALIZER) == true
        if (!on) { virtualizer?.enabled = false; return }
        val v = virtualizer ?: runCatching { Virtualizer(PRIORITY, id) }.getOrNull()?.also { virtualizer = it } ?: return
        runCatching {
            if (v.strengthSupported) v.setStrength((chain!!.virtualizer.strength.coerceIn(0, 100) * 10).toShort())
            v.enabled = true
        }
    }

    private fun applyReverb(chain: Chain?) {
        val on = chain?.isOn(EffectId.REVERB) == true
        if (!on) { reverb?.enabled = false; return }
        val r = reverb ?: runCatching { PresetReverb(PRIORITY, id) }.getOrNull()?.also { reverb = it } ?: return
        runCatching {
            val mix = chain!!.reverb.mix
            r.preset = when {
                mix < 34 -> PresetReverb.PRESET_SMALLROOM
                mix < 67 -> PresetReverb.PRESET_MEDIUMROOM
                else -> PresetReverb.PRESET_LARGEROOM
            }
            r.enabled = true
        }
    }

    fun release() {
        runCatching { dp?.release() }; dp = null
        runCatching { virtualizer?.release() }; virtualizer = null
        runCatching { reverb?.release() }; reverb = null
    }

    companion object {
        private const val TAG = "CircuitEQ"
        /** High priority so our effect wins control of the session. */
        const val PRIORITY = Int.MAX_VALUE
    }
}
