package dev.circuiteq.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class OutputKind(val label: String) {
    USB("USB"),
    WIRED("Headphone jack"),
    BLUETOOTH("Bluetooth"),
    SPEAKER("Speaker"),
    HDMI("HDMI"),
    OTHER("Other"),
}

/** Line-art shown in the device pill. */
@Serializable
enum class Art { DAC, IEM, OVER_EAR, BUDS, WIRED, SPEAKER, MONITORS }

/** A physical output the phone can route media to. */
@Serializable
data class OutputDevice(
    /** Stable key used to bind setups, e.g. "USB:FiiO KA3". */
    val key: String,
    val kind: OutputKind,
    val name: String,
    /** e.g. "24-bit / 96 kHz" when the device reports it. */
    val format: String? = null,
)
