package dev.antinode.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class FilterType(val apoCode: String, val usesGain: Boolean) {
    PEAK("PK", true),
    LOW_SHELF("LSC", true),
    HIGH_SHELF("HSC", true),
    LOW_PASS("LPQ", false),
    HIGH_PASS("HPQ", false),
    NOTCH("NO", false),
    BAND_PASS("BP", false),
}

@Serializable
data class Filter(
    val type: FilterType,
    val freq: Double,
    val gain: Double = 0.0,
    val q: Double = DEFAULT_Q,
    val enabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_Q = 0.7071
    }
}

@Serializable
data class ParametricEq(
    val filters: List<Filter> = emptyList(),
    /** Where the filters came from, e.g. the imported file name. */
    val source: String? = null,
)
