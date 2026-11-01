package dev.circuiteq.app.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.circuiteq.app.ui.theme.Palette
import dev.circuiteq.app.ui.theme.Type
import dev.circuiteq.core.dsp.ToneCurve
import dev.circuiteq.core.model.Chain
import dev.circuiteq.core.model.EffectId
import dev.circuiteq.core.model.Filter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

private const val F_MIN = 20.0
private const val F_MAX = 20_000.0

/**
 * Frequency response of the tone stage (everything summed) with the parametric EQ alone dashed underneath.
 * Filter nodes can be dragged: left/right moves frequency, up/down changes gain.
 */
@Composable
fun ToneGraph(
    chain: Chain,
    selected: Int?,
    onSelect: (Int) -> Unit,
    onMoveFilter: ((Int, Filter) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val curve = remember(chain) { ToneCurve.of(chain) }
    val freqs = remember { ToneCurve.logGrid(240) }
    val total = remember(curve) { freqs.map(curve::totalDb) }
    val peqOnly = remember(curve) { freqs.map(curve::peqDb) }
    val showPeq = chain.isOn(EffectId.PEQ)
    val filters = chain.peq.filters
    val range = remember(total) { max(8.0, ceil((total.maxOfOrNull { abs(it) } ?: 0.0) / 4) * 4) }
    val measurer = rememberTextMeasurer()
    val latestFilters by rememberUpdatedState(filters)
    val move by rememberUpdatedState(onMoveFilter)
    val select by rememberUpdatedState(onSelect)
    var dragging by remember { mutableIntStateOf(-1) }

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(2.1f)
            .border(1.dp, Palette.Beige4, RoundedCornerShape(16.dp))
            .padding(start = 26.dp, end = 8.dp, top = 10.dp, bottom = 20.dp)
            .semantics { contentDescription = "Tone stage frequency response, 20 Hz to 20 kHz" }
            .pointerInput(showPeq) {
                if (!showPeq) return@pointerInput
                fun nearest(p: Offset): Int {
                    var best = -1; var bestD = 28.dp.toPx()
                    latestFilters.forEachIndexed { i, f ->
                        val d = (Offset(xOf(f.freq, size.width.toFloat()), yOf(curve.peqDb(f.freq), range, size.height.toFloat())) - p).getDistance()
                        if (d < bestD) { bestD = d; best = i }
                    }
                    return best
                }
                detectTapGestures { p -> nearest(p).takeIf { it >= 0 }?.let(select) }
            }
            .pointerInput(showPeq, move != null) {
                if (!showPeq || move == null) return@pointerInput
                detectDragGestures(
                    onDragStart = { p ->
                        var best = -1; var bestD = 32.dp.toPx()
                        latestFilters.forEachIndexed { i, f ->
                            val d = (Offset(xOf(f.freq, size.width.toFloat()), yOf(f.gain, range, size.height.toFloat())) - p).getDistance()
                            if (d < bestD) { bestD = d; best = i }
                        }
                        dragging = best
                        if (best >= 0) select(best)
                    },
                    onDragEnd = { dragging = -1 },
                    onDragCancel = { dragging = -1 },
                ) { change, _ ->
                    val i = dragging
                    val f = latestFilters.getOrNull(i) ?: return@detectDragGestures
                    change.consume()
                    val freq = freqOf(change.position.x, size.width.toFloat()).coerceIn(F_MIN, F_MAX)
                    val gain = if (f.type.usesGain) dbOf(change.position.y, range, size.height.toFloat()).coerceIn(-24.0, 24.0) else f.gain
                    move?.invoke(i, f.copy(freq = Math.round(freq * 10) / 10.0, gain = Math.round(gain * 10) / 10.0))
                }
            },
    ) {
        val w = size.width; val h = size.height
        // grid
        var db = -range
        while (db <= range + 1e-6) {
            val y = yOf(db, range, h)
            drawLine(if (db == 0.0) Palette.Beige3 else Palette.Trace, Offset(0f, y), Offset(w, y), 1f)
            val t = measurer.measure((if (db > 0) "+" else "") + db.toInt(), Type.Value.copy(color = Palette.Beige3, fontSize = Type.Value.fontSize * 0.85f))
            drawText(t, topLeft = Offset(-t.size.width - 5.dp.toPx(), y - t.size.height / 2f))
            db += range / 2
        }
        for ((f, label) in listOf(20.0 to "20", 100.0 to "100", 1000.0 to "1k", 10_000.0 to "10k", 20_000.0 to "20k")) {
            val x = xOf(f, w)
            drawLine(Palette.Trace, Offset(x, 0f), Offset(x, h), 1f)
            val t = measurer.measure(label, Type.Value.copy(color = Palette.Beige3, fontSize = Type.Value.fontSize * 0.85f))
            drawText(t, topLeft = Offset((x - t.size.width / 2f).coerceIn(-4f, w - t.size.width + 4f), h + 4.dp.toPx()))
        }
        fun path(values: List<Double>) = Path().apply {
            values.forEachIndexed { i, v ->
                val x = xOf(freqs[i], w); val y = yOf(v, range, h)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        if (showPeq && filters.isNotEmpty()) {
            drawPath(path(peqOnly), Palette.Beige3, style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
        }
        drawPath(path(total), Palette.Beige, style = Stroke(1.8.dp.toPx(), join = StrokeJoin.Round))
        if (showPeq) {
            filters.forEachIndexed { i, f ->
                val c = Offset(xOf(f.freq, w), yOf(if (f.type.usesGain) f.gain else curve.peqDb(f.freq), range, h))
                val active = i == selected
                drawCircle(Palette.Ground, 4.5.dp.toPx(), c)
                drawCircle(
                    if (active) Palette.Amber else if (f.enabled) Palette.Beige else Palette.Beige3,
                    4.5.dp.toPx(), c, style = Stroke(if (active) 2.dp.toPx() else 1.3.dp.toPx()),
                )
            }
        }
    }
}

private fun xOf(f: Double, w: Float) = ((log10(f) - log10(F_MIN)) / (log10(F_MAX) - log10(F_MIN)) * w).toFloat()
private fun freqOf(x: Float, w: Float) = 10.0.pow(log10(F_MIN) + x / w * (log10(F_MAX) - log10(F_MIN)))
private fun yOf(db: Double, range: Double, h: Float) = ((range - db.coerceIn(-range, range)) / (2 * range) * h).toFloat()
private fun dbOf(y: Float, range: Double, h: Float) = range - y / h * 2 * range
