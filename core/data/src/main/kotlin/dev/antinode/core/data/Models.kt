package dev.antinode.core.data

import dev.antinode.core.model.Art
import dev.antinode.core.model.Chain
import dev.antinode.core.model.OutputKind

data class Profile(val id: Long, val name: String) {
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"
}

/**
 * One output + headphone combination, e.g. "USB DAC + Salnotes Zero 2". Several setups can share a device
 * key (one DAC, several IEMs); the most recently used one is applied when that device is connected.
 */
data class Setup(
    val id: Long,
    val profileId: Long,
    val name: String,
    val deviceKey: String,
    val deviceKind: OutputKind,
    val deviceName: String,
    val art: List<Art>,
    val bypass: Boolean,
    val presetId: Long?,
    val chain: Chain,
    val modified: Boolean,
    val lastUsedAt: Long,
)

data class Preset(
    val id: Long,
    val profileId: Long,
    val name: String,
    val description: String,
    val chain: Chain,
)

data class EqState(
    val profiles: List<Profile>,
    val activeProfile: Profile,
    val setups: List<Setup>,
    val presets: List<Preset>,
    val selectedSetup: Setup?,
    val power: Boolean,
) {
    /** The setup the engine should apply for [deviceKey]: most recently used for that device. */
    fun setupFor(deviceKey: String): Setup? =
        setups.filter { it.deviceKey == deviceKey }.maxByOrNull { it.lastUsedAt }
}
