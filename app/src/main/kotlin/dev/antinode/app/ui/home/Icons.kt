package dev.antinode.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Glyph(val paths: List<String>) {
    POWER(listOf("M12 3v8", "M6.3 7.3a8 8 0 1 0 11.4 0")),
    /** A chicken-head amp knob: the settings button. */
    KNOB(listOf("M5 12a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M12 12l3.5-3.5", "M12 2v1.5", "M2 12h1.5", "M20.5 12H22", "M4.9 4.9l1 1", "M18.1 5.9l1-1")),
}

@Composable
fun GlyphIcon(glyph: Glyph, tint: Color, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    val v = remember(glyph) {
        val b = ImageVector.Builder(glyph.name, 24.dp, 24.dp, 24f, 24f)
        glyph.paths.forEach {
            b.addPath(
                addPathNodes(it), fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        b.build()
    }
    Image(rememberVectorPainter(v), contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = modifier.size(size))
}
