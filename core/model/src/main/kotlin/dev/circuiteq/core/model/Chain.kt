package dev.circuiteq.core.model

import kotlinx.serialization.Serializable

/** Stages in the order Android's DynamicsProcessing engine runs them. */
enum class Stage { INPUT, TONE, DYNAMICS, LIMIT, SPACE }

@Serializable
enum class EffectId(val stage: Stage, val title: String) {
    PREAMP(Stage.INPUT, "Preamp"),
    BALANCE(Stage.INPUT, "Balance"),
    PEQ(Stage.TONE, "Parametric EQ"),
    BASS(Stage.TONE, "Bass"),
    TILT(Stage.TONE, "Tilt"),
    LOUDNESS(Stage.TONE, "Loudness"),
    COMPRESSOR(Stage.DYNAMICS, "Compressor"),
    MULTIBAND(Stage.DYNAMICS, "Multiband"),
    GATE(Stage.DYNAMICS, "Gate"),
    LIMITER(Stage.LIMIT, "Limiter"),
    VIRTUALIZER(Stage.SPACE, "Virtualizer"),
    REVERB(Stage.SPACE, "Reverb"),
}

@Serializable data class Preamp(val gainDb: Double = 0.0)

/** -100 = fully left, 0 = centre, 100 = fully right. */
@Serializable data class Balance(val value: Int = 0)

@Serializable data class BassShelf(val gainDb: Double = 2.0, val freq: Double = 105.0)

/** dB per octave around 1 kHz; negative = warmer, positive = brighter. */
@Serializable data class Tilt(val dbPerOctave: Double = -0.4)

/** 0..100 % of the equal-loudness lift. */
@Serializable data class Loudness(val amount: Int = 50)

@Serializable
data class Compressor(
    val thresholdDb: Double = -18.0,
    val ratio: Double = 2.5,
    val attackMs: Double = 10.0,
    val releaseMs: Double = 200.0,
)

@Serializable data class Multiband(val bands: Int = 3, val amount: Int = 40)

@Serializable data class Gate(val thresholdDb: Double = -70.0)

@Serializable data class Limiter(val ceilingDb: Double = -0.3)

@Serializable data class Virtualizer(val strength: Int = 35)

@Serializable data class Reverb(val mix: Int = 15)

/** Everything one output setup applies: which effects are connected plus every effect's settings. */
@Serializable
data class Chain(
    val enabled: Set<EffectId> = setOf(EffectId.PREAMP, EffectId.PEQ, EffectId.LIMITER),
    /** Android has two EQ slots; true moves the tone stage after dynamics. */
    val toneAfterDynamics: Boolean = false,
    val preamp: Preamp = Preamp(),
    val balance: Balance = Balance(),
    val peq: ParametricEq = ParametricEq(),
    val bass: BassShelf = BassShelf(),
    val tilt: Tilt = Tilt(),
    val loudness: Loudness = Loudness(),
    val compressor: Compressor = Compressor(),
    val multiband: Multiband = Multiband(),
    val gate: Gate = Gate(),
    val limiter: Limiter = Limiter(),
    val virtualizer: Virtualizer = Virtualizer(),
    val reverb: Reverb = Reverb(),
) {
    fun isOn(id: EffectId) = id in enabled

    fun toggle(id: EffectId) = copy(enabled = if (id in enabled) enabled - id else enabled + id)

    /** Stage order as drawn and as processed. */
    fun stageOrder(): List<Stage> =
        if (toneAfterDynamics) listOf(Stage.INPUT, Stage.DYNAMICS, Stage.TONE, Stage.LIMIT, Stage.SPACE)
        else listOf(Stage.INPUT, Stage.TONE, Stage.DYNAMICS, Stage.LIMIT, Stage.SPACE)

    companion object {
        val FLAT = Chain(enabled = emptySet())
    }
}
