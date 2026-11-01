package dev.antinode.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import dev.antinode.app.ui.common.pillOutline
import dev.antinode.app.ui.common.verticalText
import dev.antinode.app.ui.theme.Palette
import dev.antinode.app.ui.theme.Type
import dev.antinode.core.model.Chain
import dev.antinode.core.model.EffectId
import dev.antinode.core.model.Stage
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

private fun Stage.label(chain: Chain) = when (this) {
    Stage.INPUT -> "Input"
    Stage.TONE -> if (chain.toneAfterDynamics) "Tone · post" else "Tone"
    Stage.DYNAMICS -> "Dynamics"
    Stage.LIMIT -> "Limit"
    Stage.SPACE -> "Space"
}

private fun sgn(v: Double, digits: Int = 1) =
    (if (v > 0) "+" else if (v < 0) "−" else "±") + "%.${digits}f".format(abs(v))

/** Short readout under each pill name. */
fun effectValue(id: EffectId, c: Chain): String = when (id) {
    EffectId.PREAMP -> "${sgn(c.preamp.gainDb)} dB"
    EffectId.BALANCE -> c.balance.value.let { if (it == 0) "Centre" else if (it < 0) "L ${-it}" else "R $it" }
    EffectId.PEQ -> c.peq.filters.size.let { if (it == 0) "No filters" else "$it filters" }
    EffectId.BASS -> "${sgn(c.bass.gainDb)} dB"
    EffectId.TILT -> "${sgn(c.tilt.dbPerOctave)}/oct"
    EffectId.LOUDNESS -> "${c.loudness.amount}%"
    EffectId.COMPRESSOR -> "%.1f:1".format(c.compressor.ratio)
    EffectId.MULTIBAND -> "${c.multiband.bands} bands"
    EffectId.GATE -> "${c.gate.thresholdDb.toInt()} dB"
    EffectId.LIMITER -> "%.1f dBFS".format(c.limiter.ceilingDb)
    EffectId.VIRTUALIZER -> "${c.virtualizer.strength}%"
    EffectId.REVERB -> when { c.reverb.mix < 34 -> "Small room"; c.reverb.mix < 67 -> "Medium room"; else -> "Large room" }
}

private data class Line(val index: Int, val stage: Stage, val effects: List<EffectId>)

private fun lines(chain: Chain): List<Line> {
    var i = 0
    return chain.stageOrder().flatMap { stage ->
        EffectId.entries.filter { it.stage == stage }.chunked(2).map { Line(i++, stage, it) }
    }
}

/** Ids of the wires for the current chain: device → each connected pill in order → OUT. */
private fun wireIds(chain: Chain, live: Boolean): List<String> {
    if (!live) return listOf("bypass")
    val nodes = listOf("dev") + lines(chain).flatMap { l -> l.effects.filter { chain.isOn(it) }.map { it.name } } + "out"
    return nodes.zipWithNext { a, b -> "$a>$b" }
}

/**
 * The home screen's signal chain: the device pill on top, effect pills in processing order (max two per line),
 * and PCB-style wires linking every connected pill. Disconnected pills are dotted and the wire passes behind them.
 */
@Composable
fun ChainBoard(
    chain: Chain,
    live: Boolean,
    flowing: Boolean,
    outLabel: String,
    onToggle: (EffectId) -> Unit,
    onEdit: (EffectId) -> Unit,
    modifier: Modifier = Modifier,
    device: @Composable () -> Unit,
) {
    val rects = remember { mutableStateMapOf<String, Rect>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    fun Modifier.record(key: String) = onGloballyPositioned { rects[key] = Rect(it.positionInRoot(), it.size.toSize()) }

    val allLines = remember(chain.toneAfterDynamics) { lines(chain) }
    val ids = wireIds(chain, live)

    // Draw-in animation for wires that just appeared.
    val progress = remember { mutableStateMapOf<String, Animatable<Float, AnimationVector1D>>() }
    LaunchedEffect(ids) {
        (progress.keys - ids.toSet()).forEach { progress.remove(it) }
        ids.filter { it !in progress }.forEach { id ->
            val a = Animatable(0f)
            progress[id] = a
            launch { a.animateTo(1f, tween(550, easing = FastOutSlowInEasing)) }
        }
    }
    val phase by rememberInfiniteTransition(label = "flow").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "phase",
    )
    val measurer = rememberTextMeasurer()

    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawBehind {
                val local = rects.mapValues { (_, r) -> r.translate(-origin) }
                if (!live) {
                    drawBypass(local, measurer)
                } else {
                    drawWires(chain, allLines, local, progress, if (flowing) phase else null)
                }
            },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().record("dev")) { device() }
            Spacer(Modifier.height(30.dp))
            Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                for (stage in chain.stageOrder()) {
                    val stageLines = allLines.filter { it.stage == stage }
                    Box(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            for (line in stageLines) {
                                val solo = line.effects.size == 1
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 22.dp).record("line:${line.index}"),
                                    horizontalArrangement = if (solo) Arrangement.Center else Arrangement.spacedBy(16.dp),
                                ) {
                                    for (id in line.effects) {
                                        EffectPill(
                                            id = id,
                                            on = chain.isOn(id),
                                            live = live,
                                            value = effectValue(id, chain),
                                            onToggle = { onToggle(id) },
                                            onEdit = { onEdit(id) },
                                            modifier = (if (solo) Modifier.fillMaxWidth(0.5f).padding(horizontal = 4.dp) else Modifier.weight(1f))
                                                .record("pill:${id.name}"),
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            stage.label(chain).uppercase(),
                            style = Type.Stage,
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.CenterStart).verticalText(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(14.dp).background(Palette.Ground, CircleShape)
                        .border(1.5.dp, if (live) Palette.Beige else Palette.Beige3, CircleShape).record("out"),
                )
                Spacer(Modifier.height(7.dp))
                Text(outLabel.uppercase(), style = Type.Stage, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EffectPill(
    id: EffectId,
    on: Boolean,
    live: Boolean,
    value: String,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier,
) {
    val lit = on && live
    val outline by animateColorAsState(if (lit) Palette.Beige else Palette.Beige3, label = "outline")
    val text by animateColorAsState(if (lit) Palette.Beige else Palette.Beige2, label = "text")
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(46.dp)
            .alpha(if (live) 1f else 0.45f)
            .background(if (lit) Palette.PillOn else Palette.Ground, shape)
            .pillOutline(lit, outline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp, 46.dp)
                .clip(RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50))
                .clickable(role = Role.Switch, onClick = onToggle)
                .semantics {
                    contentDescription = "Connect ${id.title}"
                    stateDescription = if (on) "Connected" else "Disconnected"
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .then(
                        if (lit) Modifier.shadow(6.dp, CircleShape, ambientColor = Palette.Amber, spotColor = Palette.Amber)
                            .background(Palette.Beige, CircleShape)
                        else Modifier.border(1.4.dp, Palette.Beige3, CircleShape),
                    ),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50))
                .clickable(onClick = onEdit)
                .semantics { contentDescription = "Edit ${id.title}, $value" }
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(id.title, style = Type.PillName.copy(color = text), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = Type.Value.copy(color = if (lit) Palette.Beige2 else Palette.Beige3), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------- wires ----------

/** Orthogonal polyline with 45° chamfered corners, like PCB traces. */
private fun chamfer(points: List<Offset>, r: Float): Path {
    val p = Path()
    p.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size - 1) {
        val a = points[i - 1]; val c = points[i]; val b = points[i + 1]
        val l1 = hypot(c.x - a.x, c.y - a.y); val l2 = hypot(b.x - c.x, b.y - c.y)
        if (l1 < 0.5f || l2 < 0.5f) continue
        val rr = min(r, min(l1 / 2, l2 / 2))
        p.lineTo(c.x - (c.x - a.x) / l1 * rr, c.y - (c.y - a.y) / l1 * rr)
        p.lineTo(c.x + (b.x - c.x) / l2 * rr, c.y + (b.y - c.y) / l2 * rr)
    }
    val z = points.last()
    p.lineTo(z.x, z.y)
    return p
}

private fun DrawScope.drawWires(
    chain: Chain,
    lines: List<Line>,
    rects: Map<String, Rect>,
    progress: Map<String, Animatable<Float, AnimationVector1D>>,
    flowPhase: Float?,
) {
    val dev = rects["dev"] ?: return
    val out = rects["out"] ?: return
    val lineRects = lines.map { rects["line:${it.index}"] ?: return }
    val cx = dev.center.x

    data class Node(val id: String, val line: Int, val r: Rect?)
    val nodes = buildList {
        add(Node("dev", -1, null))
        for (l in lines) for (e in l.effects) if (chain.isOn(e)) rects["pill:${e.name}"]?.let { add(Node(e.name, l.index, it)) }
        add(Node("out", lines.size, null))
    }
    fun gapAbove(i: Int): Float {
        val top = if (i < lineRects.size) lineRects[i].top else out.top
        val prevBottom = if (i == 0) dev.bottom else lineRects[i - 1].bottom
        return (prevBottom + top) / 2
    }

    val stroke = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val flow = flowPhase?.let {
        Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(1.2.dp.toPx(), 15.dp.toPx()), -it * 16.2.dp.toPx(),
        ))
    }
    val r = 6.dp.toPx()

    for ((a, b) in nodes.zipWithNext()) {
        val ax = a.r?.center?.x ?: cx
        val ab = a.r?.bottom ?: dev.bottom
        val bx = b.r?.center?.x ?: cx
        val bt = b.r?.top ?: out.top
        val points = if (a.line == b.line && a.r != null && b.r != null) {
            listOf(Offset(a.r.right, a.r.center.y), Offset(b.r.left, b.r.center.y))
        } else {
            // Straight down the source column (behind switched-off pills), turning in one gap only.
            val y = gapAbove(if (a.id == "dev") 0 else b.line)
            listOf(Offset(ax, ab), Offset(ax, y), Offset(bx, y), Offset(bx, bt))
        }
        val path = chamfer(points, r)
        val p = progress["${a.id}>${b.id}"]?.value ?: 1f
        if (p >= 1f) {
            drawPath(path, Palette.Beige.copy(alpha = 0.85f), style = stroke)
            if (flow != null) drawPath(path, Palette.Amber.copy(alpha = 0.75f), style = flow)
        } else if (p > 0f) {
            val pm = PathMeasure().apply { setPath(path, false) }
            val partial = Path()
            pm.getSegment(0f, pm.length * p, partial, true)
            drawPath(partial, Palette.Beige.copy(alpha = 0.85f), style = stroke)
        }
    }
}

private fun DrawScope.drawBypass(rects: Map<String, Rect>, measurer: androidx.compose.ui.text.TextMeasurer) {
    val dev = rects["dev"] ?: return
    val out = rects["out"] ?: return
    val cx = dev.center.x
    val rail = size.width - 5.dp.toPx()
    val y1 = dev.bottom + 14.dp.toPx()
    val y2 = out.top - 14.dp.toPx()
    val path = chamfer(
        listOf(Offset(cx, dev.bottom), Offset(cx, y1), Offset(rail, y1), Offset(rail, y2), Offset(cx, y2), Offset(cx, out.top)),
        8.dp.toPx(),
    )
    drawPath(
        path, Palette.Beige4,
        style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))),
    )
    // Label sits on the rail itself, with a gap cut out of the dashes, so it never touches the pills.
    val label = measurer.measure("BYPASS", Type.Stage)
    val mid = Offset(rail, (y1 + y2) / 2)
    rotate(-90f, mid) {
        val pad = 6.dp.toPx()
        drawRect(
            Palette.Ground,
            topLeft = Offset(mid.x - label.size.width / 2f - pad, mid.y - label.size.height / 2f),
            size = androidx.compose.ui.geometry.Size(label.size.width + 2 * pad, label.size.height.toFloat()),
        )
        drawText(label, topLeft = Offset(mid.x - label.size.width / 2f, mid.y - label.size.height / 2f))
    }
}
