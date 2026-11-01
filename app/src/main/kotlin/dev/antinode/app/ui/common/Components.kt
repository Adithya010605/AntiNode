package dev.antinode.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antinode.app.ui.theme.Palette
import dev.antinode.app.ui.theme.Type
import kotlinx.coroutines.launch

/** Dotted pill outline for disconnected parts, solid when [solid]. */
fun Modifier.pillOutline(solid: Boolean, color: Color, width: Dp = 1.5.dp): Modifier = drawBehind {
    val w = width.toPx()
    val r = size.height / 2
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = w, pathEffect = if (solid) null else PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.5.dp.toPx()))),
    )
}

/** Rotates content 90° counter-clockwise, swapping its measured width and height. */
fun Modifier.verticalText(): Modifier = layout { measurable, _ ->
    val p = measurable.measure(Constraints())
    layout(p.height, p.width) {
        p.placeWithLayer(-(p.width - p.height) / 2, (p.width - p.height) / 2) { rotationZ = -90f }
    }
}

class SwipeState(val offset: Animatable<Float, *>)

/** Horizontal swipe that reports -1 (previous) or +1 (next) and gives back a drag offset for the content. */
@Composable
fun Modifier.swipeToCycle(state: SwipeState, threshold: Dp = 44.dp, onStep: (Int) -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onStep)
    return pointerInput(Unit) {
        val limit = threshold.toPx()
        detectHorizontalDragGestures(
            onDragEnd = {
                val dx = state.offset.value
                if (dx < -limit) step(1) else if (dx > limit) step(-1)
                scope.launch { state.offset.animateTo(0f) }
            },
            onDragCancel = { scope.launch { state.offset.animateTo(0f) } },
        ) { change, amount ->
            change.consume()
            scope.launch { state.offset.snapTo(state.offset.value + amount) }
        }
    }
}

@Composable
fun rememberSwipeState() = remember { SwipeState(Animatable(0f)) }

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Palette.Beige3) {
    Text(text.uppercase(), modifier = modifier, style = Type.Eyebrow.copy(color = color))
}

/** Pill switch: dotted when off, solid with a filled knob when on. */
@Composable
fun CircuitSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val knob by animateDpAsState(if (checked) 21.dp else 0.dp, label = "knob")
    Box(
        modifier
            .size(50.dp, 28.dp)
            .pillOutline(checked, if (checked) Palette.Beige else Palette.Beige3)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(5.dp),
    ) {
        Box(
            Modifier
                .offset(x = knob)
                .size(18.dp)
                .then(
                    if (checked) Modifier.background(Palette.Beige, CircleShape)
                    else Modifier.border(1.4.dp, Palette.Beige3, CircleShape),
                ),
        )
    }
}

@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, solid: Boolean = false, enabled: Boolean = true) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "alpha")
    Box(
        modifier
            .height(34.dp)
            .then(
                if (solid) Modifier.background(Palette.Beige.copy(alpha = alpha), RoundedCornerShape(50))
                else Modifier.border(1.dp, Palette.Beige3.copy(alpha = alpha), RoundedCornerShape(50)),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.PillName.copy(color = (if (solid) Palette.Ground else Palette.Beige).copy(alpha = alpha), fontSize = Type.PillName.fontSize))
    }
}

/** Two-option segmented control. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().border(1.dp, Palette.Beige4, RoundedCornerShape(50)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(32.dp)
                    .then(if (on) Modifier.background(Palette.Beige, RoundedCornerShape(50)) else Modifier)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Type.PillName.copy(fontSize = Type.Body.fontSize, color = if (on) Palette.Ground else Palette.Beige2))
            }
        }
    }
}

/** Labelled slider with a value readout, as in the mockup's effect sheets. */
@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    readout: String,
    onChange: (Float) -> Unit,
    steps: Int = 0,
) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = Type.Body)
            Text(readout, style = Type.Mono.copy(color = Palette.Beige))
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Palette.Beige, activeTrackColor = Palette.Beige, inactiveTrackColor = Palette.Beige4,
                activeTickColor = Palette.Ground, inactiveTickColor = Palette.Beige3,
            ),
        )
    }
}

@Composable
fun circuitFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Beige, unfocusedBorderColor = Palette.Beige4,
    focusedTextColor = Palette.Beige, unfocusedTextColor = Palette.Beige,
    cursorColor = Palette.Amber, focusedLabelColor = Palette.Beige2, unfocusedLabelColor = Palette.Beige3,
)

/** Small dialog asking for one line of text. */
@Composable
fun TextPrompt(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Panel,
        title = { Text(title, style = Type.SheetTitle.copy(fontSize = Type.SheetTitle.fontSize * 0.85f)) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                textStyle = TextStyle(fontFamily = Type.Body.fontFamily, fontSize = Type.DeviceName.fontSize),
                colors = circuitFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(text) }),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirm, color = Palette.Beige) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.Beige2) } },
    )
}
