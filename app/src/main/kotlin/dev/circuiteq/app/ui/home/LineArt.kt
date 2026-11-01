package dev.circuiteq.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.circuiteq.app.ui.theme.Palette
import dev.circuiteq.core.model.Art

/** Beige line drawings of each output type, drawn on a 40×40 grid (same as the mockup's SVGs). */
private val PATHS: Map<Art, List<String>> = mapOf(
    Art.DAC to listOf(
        "M17 9h6a4 4 0 0 1 4 4v14a4 4 0 0 1 -4 4h-6a4 4 0 0 1 -4 -4v-14a4 4 0 0 1 4 -4z",
        "M17 9V5h6v4", "M18.7 15a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0 -2.6 0", "M20 31v6",
    ),
    Art.IEM to listOf(
        "M10 23c0-7 5-12 12-12 5 0 8 3 8 7 0 6-6 11-13 11-4 0-7-2-7-6z", "M27 13l5-5", "M13 28c-2 3-2 6 0 9",
    ),
    Art.OVER_EAR to listOf(
        "M8 25v-5a12 12 0 0 1 24 0v5",
        "M8 22h2a3 3 0 0 1 3 3v7a3 3 0 0 1 -3 3h-2a3 3 0 0 1 -3 -3v-7a3 3 0 0 1 3 -3z",
        "M30 22h2a3 3 0 0 1 3 3v7a3 3 0 0 1 -3 3h-2a3 3 0 0 1 -3 -3v-7a3 3 0 0 1 3 -3z",
    ),
    Art.BUDS to listOf(
        "M7 16a6 6 0 1 0 12 0a6 6 0 1 0 -12 0", "M13 22v12", "M21 16a6 6 0 1 0 12 0a6 6 0 1 0 -12 0", "M27 22v12",
    ),
    Art.WIRED to listOf(
        "M9 20c0-6 4-10 10-10 4 0 7 2 7 6 0 5-5 9-11 9-4 0-6-2-6-5z", "M15 25c1 4 4 6 8 6h6",
        "M30.5 28h4a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1 -1.5 1.5h-4a1.5 1.5 0 0 1 -1.5 -1.5v-3a1.5 1.5 0 0 1 1.5 -1.5z",
    ),
    Art.SPEAKER to listOf(
        "M16 4h8a4 4 0 0 1 4 4v24a4 4 0 0 1 -4 4h-8a4 4 0 0 1 -4 -4v-24a4 4 0 0 1 4 -4z", "M17 29h6M17 32h6",
    ),
    Art.MONITORS to listOf(
        "M7 8h8a2 2 0 0 1 2 2v20a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2v-20a2 2 0 0 1 2 -2z",
        "M7 24a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M9.2 13a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0",
        "M25 8h8a2 2 0 0 1 2 2v20a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2v-20a2 2 0 0 1 2 -2z",
        "M25 24a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M27.2 13a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0",
    ),
)

val Art.label: String
    get() = when (this) {
        Art.DAC -> "DAC"
        Art.IEM -> "IEM"
        Art.OVER_EAR -> "Over-ear"
        Art.BUDS -> "Wireless buds"
        Art.WIRED -> "Wired"
        Art.SPEAKER -> "Speaker"
        Art.MONITORS -> "Monitors"
    }

private fun vector(art: Art): ImageVector {
    val b = ImageVector.Builder(art.name, 40.dp, 40.dp, 40f, 40f)
    for (d in PATHS.getValue(art)) {
        b.addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Palette.Beige),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

@Composable
fun ArtIcon(art: Art, size: Dp = 38.dp, modifier: Modifier = Modifier) {
    val v = remember(art) { vector(art) }
    Image(rememberVectorPainter(v), contentDescription = art.label, modifier = modifier.size(size))
}
