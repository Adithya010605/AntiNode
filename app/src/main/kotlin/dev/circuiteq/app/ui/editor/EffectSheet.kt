package dev.circuiteq.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.circuiteq.app.ui.common.CircuitSwitch
import dev.circuiteq.app.ui.common.Eyebrow
import dev.circuiteq.app.ui.common.LabeledSlider
import dev.circuiteq.app.ui.common.PillButton
import dev.circuiteq.app.ui.common.Segmented
import dev.circuiteq.app.ui.theme.Palette
import dev.circuiteq.app.ui.theme.Type
import dev.circuiteq.core.dsp.Gains
import dev.circuiteq.core.dsp.ToneCurve
import dev.circuiteq.core.model.Chain
import dev.circuiteq.core.model.EffectId
import dev.circuiteq.core.model.Filter
import dev.circuiteq.core.model.FilterType
import dev.circuiteq.core.model.Stage
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private val EffectId.description: String
    get() = when (this) {
        EffectId.PREAMP -> "Input gain · keeps boosts from clipping"
        EffectId.BALANCE -> "Per-channel input gain"
        EffectId.PEQ -> "Parametric filters, converted to Android's EQ bands"
        EffectId.BASS -> "Low shelf, summed into the tone curve"
        EffectId.TILT -> "Warm ↔ bright around 1 kHz"
        EffectId.LOUDNESS -> "Equal-loudness lift for quiet listening"
        EffectId.COMPRESSOR -> "Evens out loud and quiet parts"
        EffectId.MULTIBAND -> "Compresses bass, mids and treble separately"
        EffectId.GATE -> "Silences hiss below the threshold"
        EffectId.LIMITER -> "Always last · catches peaks"
        EffectId.VIRTUALIZER -> "Android spatial effect · depends on your phone"
        EffectId.REVERB -> "Android preset room · depends on your phone"
    }

private val FilterType.label: String
    get() = when (this) {
        FilterType.PEAK -> "Peak"
        FilterType.LOW_SHELF -> "Low shelf"
        FilterType.HIGH_SHELF -> "High shelf"
        FilterType.LOW_PASS -> "Low-pass"
        FilterType.HIGH_PASS -> "High-pass"
        FilterType.NOTCH -> "Notch"
        FilterType.BAND_PASS -> "Band-pass"
    }

private fun fmtFreq(f: Double) = if (f >= 1000) "%.2fk".format(f / 1000) else "%.1f".format(f)
private fun sgn(v: Double) = (if (v > 0) "+" else if (v < 0) "−" else "±") + "%.1f".format(abs(v))

/**
 * Editor for one effect. Edits apply to a local draft immediately and are written to storage (and so to the
 * engine) a moment later, which keeps sliders smooth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectSheet(
    id: EffectId,
    chain: Chain,
    sourceName: String?,
    onCommit: (Chain) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onAutoEq: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(id) { mutableStateOf(chain) }
    var committed by remember(id) { mutableStateOf(chain) }
    LaunchedEffect(draft) {
        if (draft != committed) { delay(80); committed = draft; onCommit(draft) }
    }
    // Pick up outside changes (e.g. an import) while the sheet is open, but not our own commits coming back.
    LaunchedEffect(chain) { if (chain != committed) { committed = chain; draft = chain } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Panel,
        contentColor = Palette.Beige,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 14.dp).size(38.dp, 4.dp).background(Palette.Beige4, RoundedCornerShape(4.dp)))
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(id.title, style = Type.SheetTitle)
                    Spacer(Modifier.height(6.dp))
                    Text(id.description, style = Type.Mono.copy(fontSize = Type.Value.fontSize))
                }
                CircuitSwitch(draft.isOn(id), { draft = draft.toggle(id) })
            }

            if (id.stage == Stage.TONE) {
                Spacer(Modifier.height(16.dp))
                var selected by remember { mutableStateOf<Int?>(null) }
                ToneGraph(
                    chain = draft,
                    selected = selected,
                    onSelect = { selected = it },
                    onMoveFilter = if (id == EffectId.PEQ) { i, f ->
                        draft = draft.copy(peq = draft.peq.copy(filters = draft.peq.filters.toMutableList().also { it[i] = f }))
                    } else null,
                )
                Row(Modifier.padding(top = 6.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("━ Tone stage (summed)", style = Type.Value.copy(color = Palette.Beige2))
                    if (draft.isOn(EffectId.PEQ) && draft.peq.filters.isNotEmpty()) {
                        Text("┅ Parametric EQ only", style = Type.Value.copy(color = Palette.Beige3))
                    }
                }
                if (id == EffectId.PEQ) {
                    PeqEditor(draft, selected, { selected = it }, { draft = it }, sourceName, onImport, onExport, onAutoEq)
                }
            }

            Controls(id, draft) { draft = it }

            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PillButton("Done", onDismiss, solid = true)
            }
        }
    }
}

@Composable
private fun Controls(id: EffectId, c: Chain, set: (Chain) -> Unit) {
    when (id) {
        EffectId.PREAMP -> {
            LabeledSlider("Gain", c.preamp.gainDb.toFloat(), -24f..12f, "${sgn(c.preamp.gainDb)} dB", {
                set(c.copy(preamp = c.preamp.copy(gainDb = (it * 10).roundToInt() / 10.0)))
            })
            val auto = Gains.autoPreampDb(ToneCurve.of(c)::totalDb)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Auto keeps the loudest boost at 0 dB: ${sgn(auto)} dB", style = Type.Value.copy(color = Palette.Beige3), modifier = Modifier.weight(1f))
                PillButton("Auto", { set(c.copy(preamp = c.preamp.copy(gainDb = (auto * 10).roundToInt() / 10.0))) })
            }
        }
        EffectId.BALANCE -> LabeledSlider(
            "Left ↔ Right", c.balance.value.toFloat(), -100f..100f,
            c.balance.value.let { if (it == 0) "Centre" else if (it < 0) "L ${-it}" else "R $it" },
            { set(c.copy(balance = c.balance.copy(value = it.roundToInt()))) },
        )
        EffectId.PEQ -> Unit
        EffectId.BASS -> {
            LabeledSlider("Shelf gain", c.bass.gainDb.toFloat(), -6f..12f, "${sgn(c.bass.gainDb)} dB", {
                set(c.copy(bass = c.bass.copy(gainDb = (it * 2).roundToInt() / 2.0)))
            })
            LabeledSlider("Below", c.bass.freq.toFloat(), 40f..250f, "${c.bass.freq.toInt()} Hz", {
                set(c.copy(bass = c.bass.copy(freq = (it / 5).roundToInt() * 5.0)))
            })
        }
        EffectId.TILT -> LabeledSlider("Slope", c.tilt.dbPerOctave.toFloat(), -2f..2f, "${sgn(c.tilt.dbPerOctave)} dB/oct", {
            set(c.copy(tilt = c.tilt.copy(dbPerOctave = (it * 10).roundToInt() / 10.0)))
        })
        EffectId.LOUDNESS -> LabeledSlider("Amount", c.loudness.amount.toFloat(), 0f..100f, "${c.loudness.amount}%", {
            set(c.copy(loudness = c.loudness.copy(amount = it.roundToInt())))
        })
        EffectId.COMPRESSOR -> {
            val k = c.compressor
            LabeledSlider("Threshold", k.thresholdDb.toFloat(), -60f..0f, "${k.thresholdDb.toInt()} dB", { set(c.copy(compressor = k.copy(thresholdDb = it.roundToInt().toDouble()))) })
            LabeledSlider("Ratio", k.ratio.toFloat(), 1f..10f, "%.1f:1".format(k.ratio), { set(c.copy(compressor = k.copy(ratio = (it * 10).roundToInt() / 10.0))) })
            LabeledSlider("Attack", k.attackMs.toFloat(), 1f..200f, "${k.attackMs.toInt()} ms", { set(c.copy(compressor = k.copy(attackMs = it.roundToInt().toDouble()))) })
            LabeledSlider("Release", k.releaseMs.toFloat(), 20f..1000f, "${k.releaseMs.toInt()} ms", { set(c.copy(compressor = k.copy(releaseMs = (it / 10).roundToInt() * 10.0))) })
        }
        EffectId.MULTIBAND -> {
            val m = c.multiband
            LabeledSlider("Bands", m.bands.toFloat(), 2f..5f, "${m.bands}", { set(c.copy(multiband = m.copy(bands = it.roundToInt()))) }, steps = 2)
            LabeledSlider("Amount", m.amount.toFloat(), 0f..100f, "${m.amount}%", { set(c.copy(multiband = m.copy(amount = it.roundToInt()))) })
        }
        EffectId.GATE -> LabeledSlider("Threshold", c.gate.thresholdDb.toFloat(), -90f..-30f, "${c.gate.thresholdDb.toInt()} dB", {
            set(c.copy(gate = c.gate.copy(thresholdDb = it.roundToInt().toDouble())))
        })
        EffectId.LIMITER -> LabeledSlider("Ceiling", c.limiter.ceilingDb.toFloat(), -6f..0f, "%.1f dBFS".format(c.limiter.ceilingDb), {
            set(c.copy(limiter = c.limiter.copy(ceilingDb = (it * 10).roundToInt() / 10.0)))
        })
        EffectId.VIRTUALIZER -> LabeledSlider("Strength", c.virtualizer.strength.toFloat(), 0f..100f, "${c.virtualizer.strength}%", {
            set(c.copy(virtualizer = c.virtualizer.copy(strength = it.roundToInt())))
        })
        EffectId.REVERB -> LabeledSlider(
            "Room", c.reverb.mix.toFloat(), 0f..100f,
            when { c.reverb.mix < 34 -> "Small"; c.reverb.mix < 67 -> "Medium"; else -> "Large" },
            { set(c.copy(reverb = c.reverb.copy(mix = it.roundToInt()))) },
        )
    }
}

@Composable
private fun PeqEditor(
    c: Chain,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    set: (Chain) -> Unit,
    sourceName: String?,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onAutoEq: () -> Unit,
) {
    val filters = c.peq.filters
    fun update(i: Int, f: Filter) = set(c.copy(peq = c.peq.copy(filters = filters.toMutableList().also { it[i] = f })))

    Spacer(Modifier.height(16.dp))
    Row(
        Modifier.fillMaxWidth().height(52.dp).border(1.5.dp, Palette.Beige, RoundedCornerShape(50)).clickable(onClick = onAutoEq).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("AutoEQ for your headphones", style = Type.PillName.copy(color = Palette.Beige))
            Text("8,849 profiles · Harman and other targets · offline", style = Type.Value.copy(color = Palette.Beige3))
        }
        Text("›", style = Type.SheetTitle)
    }
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Eyebrow("Filters · ${filters.size}", Modifier.weight(1f))
        PillButton("+ Add", {
            set(c.copy(peq = c.peq.copy(filters = filters + Filter(FilterType.PEAK, 1000.0, 0.0, 1.0))))
            onSelect(filters.size)
        })
    }
    Spacer(Modifier.height(8.dp))
    filters.forEachIndexed { i, f ->
        FilterRow(i, f, expanded = i == selected, onClick = { onSelect(if (i == selected) null else i) }, onChange = { update(i, it) }, onRemove = {
            set(c.copy(peq = c.peq.copy(filters = filters.filterIndexed { j, _ -> j != i })))
            onSelect(null)
        })
    }
    if (filters.isEmpty()) {
        Text("No filters yet. Import an AutoEQ / Equalizer APO file, or add filters by hand.", style = Type.Body, modifier = Modifier.padding(vertical = 8.dp))
    }

    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            sourceName ?: "Hand-made filters",
            style = Type.Value.copy(color = Palette.Beige2), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        PillButton("Import", onImport)
        Spacer(Modifier.width(6.dp))
        PillButton("Export", onExport, enabled = filters.isNotEmpty())
    }

    Spacer(Modifier.height(18.dp))
    Eyebrow("Position in chain")
    Spacer(Modifier.height(8.dp))
    Segmented(
        listOf("Before compressor", "After compressor"),
        if (c.toneAfterDynamics) 1 else 0,
        { set(c.copy(toneAfterDynamics = it == 1)) },
    )
    Text(
        "Android's effect engine has two EQ slots, before and after dynamics. Input stays first and the limiter stays last, so the chain only offers orders it can really play.",
        style = Type.Value.copy(color = Palette.Beige3), modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun FilterRow(
    index: Int,
    f: Filter,
    expanded: Boolean,
    onClick: () -> Unit,
    onChange: (Filter) -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .then(if (expanded) Modifier.border(1.dp, Palette.Beige4, RoundedCornerShape(16.dp)) else Modifier)
            .padding(horizontal = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(40.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clickable { onChange(f.copy(enabled = !f.enabled)) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(8.dp).then(
                        if (f.enabled) Modifier.background(Palette.Beige, CircleShape) else Modifier.border(1.3.dp, Palette.Beige3, CircleShape),
                    ),
                )
            }
            Text("${index + 1}", style = Type.Mono.copy(color = Palette.Beige3), modifier = Modifier.width(22.dp))
            Box {
                Text(
                    f.type.label, style = Type.Mono.copy(color = Palette.Beige),
                    modifier = Modifier.width(84.dp).clickable { menu = true },
                )
                DropdownMenu(menu, { menu = false }, containerColor = Palette.Panel2) {
                    FilterType.entries.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.label, style = Type.Body.copy(color = Palette.Beige)) },
                            onClick = { onChange(f.copy(type = t)); menu = false },
                        )
                    }
                }
            }
            Text("${fmtFreq(f.freq)} Hz", style = Type.Mono, modifier = Modifier.weight(1f))
            Text(if (f.type.usesGain) "${sgn(f.gain)} dB" else "—", style = Type.Mono, modifier = Modifier.width(66.dp))
            Text("Q %.2f".format(f.q), style = Type.Mono, modifier = Modifier.width(56.dp))
        }
        if (expanded) {
            val logF = log10(f.freq).toFloat()
            LabeledSlider("Frequency", logF, log10(20f)..log10(20000f), "${fmtFreq(f.freq)} Hz", {
                onChange(f.copy(freq = (10.0.pow(it.toDouble()) * 10).roundToInt() / 10.0))
            })
            if (f.type.usesGain) {
                LabeledSlider("Gain", f.gain.toFloat(), -12f..12f, "${sgn(f.gain)} dB", { onChange(f.copy(gain = (it * 10).roundToInt() / 10.0)) })
            }
            LabeledSlider("Q", log10(f.q).toFloat(), log10(0.1f)..log10(10f), "%.2f".format(f.q), {
                onChange(f.copy(q = (10.0.pow(it.toDouble()) * 100).roundToInt() / 100.0))
            })
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                PillButton("Remove filter", onRemove)
            }
        }
    }
}
