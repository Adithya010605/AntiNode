package dev.circuiteq.app.ui.autoeq

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.circuiteq.app.autoeq.AutoEqRepository
import dev.circuiteq.app.ui.common.CircuitSwitch
import dev.circuiteq.app.ui.common.Eyebrow
import dev.circuiteq.app.ui.common.LabeledSlider
import dev.circuiteq.app.ui.common.PillButton
import dev.circuiteq.app.ui.common.Segmented
import dev.circuiteq.app.ui.common.circuitFieldColors
import dev.circuiteq.app.ui.common.pillOutline
import dev.circuiteq.app.ui.sheets.CircuitSheet
import dev.circuiteq.app.ui.theme.Palette
import dev.circuiteq.app.ui.theme.Type
import dev.circuiteq.core.autoeq.AutoEqCatalog
import dev.circuiteq.core.autoeq.CatalogEntry
import dev.circuiteq.core.autoeq.Target
import dev.circuiteq.core.dsp.AutoEqGenerator
import dev.circuiteq.core.dsp.AutoEqOptions
import dev.circuiteq.core.dsp.AutoEqResult
import dev.circuiteq.core.dsp.Biquad
import dev.circuiteq.core.model.Filter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** What the user chose to apply. */
data class AutoEqApply(
    val headphone: String,
    val label: String,
    val filters: List<Filter>,
    val preampDb: Double,
    val renameOutput: Boolean,
)

@Composable
fun AutoEqSheet(
    initialQuery: String,
    outputName: String,
    suggestRename: Boolean,
    onApply: (AutoEqApply) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var catalog by remember { mutableStateOf<AutoEqCatalog?>(null) }
    var custom by remember { mutableStateOf<List<Target>>(emptyList()) }
    var selected by remember { mutableStateOf<CatalogEntry?>(null) }
    LaunchedEffect(Unit) {
        catalog = AutoEqRepository.catalog(context)
        custom = AutoEqRepository.customTargets(context)
    }

    CircuitSheet("AutoEQ", "${"%,d".format(catalog?.entries?.size ?: 8849)} headphones, measured by the community · offline", onDismiss) {
        val cat = catalog
        when {
            cat == null -> Text("Opening the database…", style = Type.Body, modifier = Modifier.padding(vertical = 24.dp))
            selected == null -> SearchView(cat, initialQuery) { selected = it }
            else -> DetailView(
                catalog = cat,
                entry = selected!!,
                custom = custom,
                outputName = outputName,
                suggestRename = suggestRename,
                onCustomChanged = { custom = it },
                onBack = { selected = null },
                onApply = { onApply(it); onDismiss() },
            )
        }
    }
}

@Composable
private fun SearchView(catalog: AutoEqCatalog, initialQuery: String, onPick: (CatalogEntry) -> Unit) {
    var query by remember { mutableStateOf(initialQuery) }
    val results = remember(query) { catalog.search(query) }
    OutlinedTextField(
        value = query, onValueChange = { query = it }, singleLine = true,
        label = { Text("Search headphones or IEMs") },
        textStyle = TextStyle(fontFamily = Type.Body.fontFamily, fontSize = Type.DeviceName.fontSize),
        colors = circuitFieldColors(), modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    if (query.isNotBlank() && results.isEmpty()) {
        Text("Nothing found. Try fewer words, e.g. just the model.", style = Type.Body, modifier = Modifier.padding(vertical = 8.dp))
    }
    // The sheet scrolls as a whole, so the list is a plain column of at most 60 rows.
    results.forEach { e ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp).heightIn(min = 52.dp)
                .pillOutline(false, Palette.Beige4).clickable { onPick(e) }.padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(e.name, style = Type.PillName.copy(color = Palette.Beige), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(e.subtitle, style = Type.Value.copy(color = Palette.Beige3), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (e.hasMeasurement) "measured" else "EQ only", style = Type.Value.copy(color = if (e.hasMeasurement) Palette.Beige2 else Palette.Beige3))
        }
    }
}

@Composable
private fun DetailView(
    catalog: AutoEqCatalog,
    entry: CatalogEntry,
    custom: List<Target>,
    outputName: String,
    suggestRename: Boolean,
    onCustomChanged: (List<Target>) -> Unit,
    onBack: () -> Unit,
    onApply: (AutoEqApply) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val choices = remember(entry, custom) { catalog.targetsFor(entry, custom) }
    var mode by remember(entry) { mutableIntStateOf(0) }
    var targetIdx by remember(entry) { mutableIntStateOf(0) }
    var bass by remember(entry) { mutableFloatStateOf(0f) }
    var tilt by remember(entry) { mutableFloatStateOf(0f) }
    var maxBoost by remember(entry) { mutableFloatStateOf(6f) }
    var result by remember(entry) { mutableStateOf<AutoEqResult?>(null) }
    var working by remember { mutableStateOf(false) }
    var rename by remember(entry) { mutableStateOf(suggestRename) }
    var menu by remember { mutableStateOf(false) }
    val measurement = remember(entry) { entry.measurement }
    val choice = choices.getOrElse(targetIdx) { choices.first() }
    val recommended = remember(choices) { choices.first { it.recommended } }

    val importTarget = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val name = AutoEqRepository.importTarget(context, uri)
            val list = AutoEqRepository.customTargets(context)
            onCustomChanged(list)
            if (name != null) {
                val idx = catalog.targetsFor(entry, list).indexOfFirst { it.target.custom && it.target.name == name }
                if (idx >= 0) { targetIdx = idx; mode = 1 }
            }
        }
    }

    // Custom mode regenerates whenever an option changes.
    LaunchedEffect(entry, mode, targetIdx, bass, tilt, maxBoost, choices) {
        if (mode != 1 || measurement == null) return@LaunchedEffect
        delay(150)
        working = true
        result = withContext(Dispatchers.Default) {
            AutoEqGenerator.generate(measurement, choice.target.curve, AutoEqOptions(bass.toDouble(), tilt.toDouble(), maxBoost.toDouble()))
        }
        working = false
    }

    val filters = if (mode == 1) result?.filters.orEmpty() else entry.filters
    val preamp = if (mode == 1) result?.preampDb ?: 0.0 else entry.preampDb
    val shownTarget = if (mode == 1) choice.target else recommended.target

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = Type.DeviceName)
            Text(entry.subtitle, style = Type.Value.copy(color = Palette.Beige3))
        }
        PillButton("Back", onBack)
    }
    Spacer(Modifier.height(14.dp))
    Segmented(listOf("AutoEQ result", "Choose target"), mode, { if (it == 0 || measurement != null) mode = it })
    if (measurement == null) {
        Text(
            "${entry.source} doesn't publish raw measurements, so only AutoEQ's own result is available for this one.",
            style = Type.Value.copy(color = Palette.Beige3), modifier = Modifier.padding(top = 8.dp),
        )
    }

    Spacer(Modifier.height(14.dp))
    DeviationGraph(measurement, shownTarget.curve.db, filters)
    Row(Modifier.padding(top = 6.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (measurement != null) {
            Text("┅ before", style = Type.Value.copy(color = Palette.Beige3))
            Text("━ after EQ", style = Type.Value.copy(color = Palette.Beige))
        }
        Text("━ EQ", style = Type.Value.copy(color = Palette.Amber))
    }
    if (measurement != null) {
        val (before, after) = remember(measurement, shownTarget, filters) { deviation(measurement.db, shownTarget.curve.db, filters) }
        Text(
            "Off target, 20 Hz–10 kHz: %.1f dB → %.1f dB RMS".format(before, after) + if (working) " · working…" else "",
            style = Type.Mono, modifier = Modifier.padding(top = 8.dp),
        )
    }

    if (mode == 1) {
        Spacer(Modifier.height(16.dp))
        Eyebrow("Target")
        Spacer(Modifier.height(8.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().height(44.dp).pillOutline(true, Palette.Beige3).clickable { menu = true }.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(choice.label + if (choice.recommended) "  ★" else "", style = Type.PillName.copy(color = Palette.Beige), modifier = Modifier.weight(1f))
                Text("▾", style = Type.PillName.copy(color = Palette.Beige2))
            }
            DropdownMenu(menu, { menu = false }, containerColor = Palette.Panel2, modifier = Modifier.heightIn(max = 420.dp)) {
                choices.forEachIndexed { i, c ->
                    DropdownMenuItem(
                        text = { Text(c.label + if (c.recommended) "  ★ recommended" else "", style = Type.Body.copy(color = if (i == targetIdx) Palette.Beige else Palette.Beige2)) },
                        onClick = { targetIdx = i; menu = false },
                    )
                }
            }
        }
        if (choice.target.name != choice.label && !choice.target.custom) {
            Text("Using “${choice.target.name}”, made for this measurement rig.", style = Type.Value.copy(color = Palette.Beige3), modifier = Modifier.padding(top = 6.dp))
        }
        Row(Modifier.padding(top = 8.dp)) { PillButton("Import target CSV", { importTarget.launch(arrayOf("text/*", "text/csv", "application/octet-stream")) }) }
        LabeledSlider("Bass boost", bass, 0f..12f, "+%.1f dB".format(bass), { bass = (it * 2).roundToInt() / 2f })
        LabeledSlider("Tilt", tilt, -1f..1f, "%+.1f dB/oct".format(tilt), { tilt = (it * 10).roundToInt() / 10f })
        LabeledSlider("Max boost", maxBoost, 0f..12f, "+%.0f dB".format(maxBoost), { maxBoost = it.roundToInt().toFloat() })
    }

    Spacer(Modifier.height(16.dp))
    Eyebrow("Filters · ${filters.size} · preamp ${"%.1f".format(preamp)} dB")
    Spacer(Modifier.height(6.dp))
    filters.forEachIndexed { i, f ->
        Text(
            "%2d  %-4s %8s Hz  %+5.1f dB  Q %.2f".format(i + 1, f.type.apoCode, if (f.freq >= 1000) "%.2fk".format(f.freq / 1000) else "%.0f".format(f.freq), f.gain, f.q),
            style = Type.Mono,
        )
    }

    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Name this output “${entry.name}”", style = Type.PillName.copy(color = Palette.Beige))
            Text("Currently “$outputName”", style = Type.Value.copy(color = Palette.Beige3))
        }
        CircuitSwitch(rename, { rename = it })
    }
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        val label = if (mode == 1) "${choice.label}${if (bass > 0) " +%.1f dB bass".format(bass) else ""}" else "AutoEQ"
        PillButton(
            "Apply", solid = true, enabled = filters.isNotEmpty() && !working,
            onClick = { onApply(AutoEqApply(entry.name, label, filters, preamp, rename)) },
        )
    }
}

/** RMS deviation from target before and after the filters, 20 Hz–10 kHz, with midrange levels aligned. */
private fun deviation(measurement: DoubleArray, target: DoubleArray, filters: List<Filter>): Pair<Double, Double> {
    val g = AutoEqGenerator.GRID
    val eq = eqCurve(filters)
    val mid = g.indices.filter { g[it] in 300.0..3000.0 }
    val off = mid.sumOf { target[it] - measurement[it] } / mid.size
    val band = g.indices.filter { g[it] <= 10_000 }
    fun rms(f: (Int) -> Double) = sqrt(band.sumOf { f(it).let { d -> d * d } } / band.size)
    return rms { measurement[it] + off - target[it] } to rms { measurement[it] + off + eq[it] - target[it] }
}

private fun eqCurve(filters: List<Filter>): DoubleArray {
    val bq = filters.map(Biquad::of)
    return DoubleArray(AutoEqGenerator.GRID.size) { i -> bq.sumOf { it.magnitudeDb(AutoEqGenerator.GRID[i]) } }
}

/** Deviation from target before (dotted) and after EQ (solid), with the EQ itself in amber. */
@Composable
private fun DeviationGraph(measurement: dev.circuiteq.core.dsp.Curve?, target: DoubleArray, filters: List<Filter>) {
    val measurer = rememberTextMeasurer()
    val g = AutoEqGenerator.GRID
    val eq = remember(filters) { eqCurve(filters) }
    val before = remember(measurement, target) {
        measurement?.db?.let { m ->
            val mid = g.indices.filter { g[it] in 300.0..3000.0 }
            val off = mid.sumOf { target[it] - m[it] } / mid.size
            DoubleArray(g.size) { m[it] + off - target[it] }
        }
    }
    val range = 12.0
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(2.1f).border(1.dp, Palette.Beige4, RoundedCornerShape(16.dp))
            .padding(start = 26.dp, end = 8.dp, top = 10.dp, bottom = 20.dp)
            .semantics { contentDescription = "Deviation from target before and after EQ" },
    ) {
        val w = size.width; val h = size.height
        fun x(f: Double) = ((log10(f) - log10(20.0)) / 3 * w).toFloat()
        fun y(db: Double) = ((range - db.coerceIn(-range, range)) / (2 * range) * h).toFloat()
        for (db in listOf(-12.0, -6.0, 0.0, 6.0, 12.0)) {
            drawLine(if (db == 0.0) Palette.Beige3 else Palette.Trace, Offset(0f, y(db)), Offset(w, y(db)), 1f)
            val t = measurer.measure((if (db > 0) "+" else "") + db.toInt(), Type.Value.copy(color = Palette.Beige3, fontSize = Type.Value.fontSize * 0.85f))
            drawText(t, topLeft = Offset(-t.size.width - 5.dp.toPx(), y(db) - t.size.height / 2f))
        }
        for ((f, label) in listOf(20.0 to "20", 100.0 to "100", 1000.0 to "1k", 10_000.0 to "10k", 20_000.0 to "20k")) {
            drawLine(Palette.Trace, Offset(x(f), 0f), Offset(x(f), h), 1f)
            val t = measurer.measure(label, Type.Value.copy(color = Palette.Beige3, fontSize = Type.Value.fontSize * 0.85f))
            drawText(t, topLeft = Offset((x(f) - t.size.width / 2f).coerceIn(-4f, w - t.size.width + 4f), h + 4.dp.toPx()))
        }
        fun path(v: DoubleArray) = Path().apply { v.forEachIndexed { i, d -> if (i == 0) moveTo(x(g[i]), y(d)) else lineTo(x(g[i]), y(d)) } }
        if (before != null) {
            drawPath(path(before), Palette.Beige3, style = Stroke(1.3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))))
            drawPath(path(DoubleArray(g.size) { before[it] + eq[it] }), Palette.Beige, style = Stroke(1.8.dp.toPx(), join = StrokeJoin.Round))
        }
        drawPath(path(eq), Palette.Amber.copy(alpha = 0.9f), style = Stroke(1.4.dp.toPx(), join = StrokeJoin.Round))
    }
}
