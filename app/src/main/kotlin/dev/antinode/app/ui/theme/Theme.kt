package dev.antinode.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.antinode.app.R

/** Black chassis, beige silkscreen, amber for live signal. Matches mockup/home.html. */
object Palette {
    val Bezel = Color(0xFF050505)
    val Ground = Color(0xFF0A0A09)
    val Panel = Color(0xFF121110)
    val Panel2 = Color(0xFF191814)
    val PillOn = Color(0xFF13120F)
    val Beige = Color(0xFFD8CBAF)
    val Beige2 = Color(0xFFA39A86)
    val Beige3 = Color(0xFF6B6455)
    val Beige4 = Color(0xFF3A362E)
    val Trace = Color(0xFF2B2823)
    val Amber = Color(0xFFE2A24E)
}

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Engraved amp front-panel lettering for headings and stage labels. */
val Cinzel = FontFamily(variable(R.font.cinzel, 500), variable(R.font.cinzel, 600))
val Archivo = FontFamily(variable(R.font.archivo, 400), variable(R.font.archivo, 500), variable(R.font.archivo, 600))
val PlexMono = FontFamily(Font(R.font.plex_mono_regular, FontWeight.Normal), Font(R.font.plex_mono_medium, FontWeight.Medium))

object Type {
    val Stage = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight(500), fontSize = 10.sp, letterSpacing = 0.22.em, color = Palette.Beige3)
    val Eyebrow = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight(500), fontSize = 11.sp, letterSpacing = 0.22.em, color = Palette.Beige3)
    val SheetTitle = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight(600), fontSize = 19.sp, letterSpacing = 0.06.em, color = Palette.Beige)
    val PillName = TextStyle(fontFamily = Archivo, fontWeight = FontWeight(500), fontSize = 13.sp)
    val Value = TextStyle(fontFamily = PlexMono, fontSize = 10.sp)
    val Body = TextStyle(fontFamily = Archivo, fontSize = 13.sp, color = Palette.Beige2)
    val Mono = TextStyle(fontFamily = PlexMono, fontSize = 11.sp, color = Palette.Beige2)
    val DeviceName = TextStyle(fontFamily = Archivo, fontWeight = FontWeight(600), fontSize = 15.sp, color = Palette.Beige)
}

@Composable
fun AntinodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Beige, onPrimary = Palette.Ground,
            secondary = Palette.Amber, onSecondary = Palette.Ground,
            background = Palette.Ground, onBackground = Palette.Beige,
            surface = Palette.Panel, onSurface = Palette.Beige,
            surfaceVariant = Palette.Panel2, onSurfaceVariant = Palette.Beige2,
            surfaceContainer = Palette.Panel, surfaceContainerHigh = Palette.Panel2, surfaceContainerLow = Palette.Panel,
            outline = Palette.Beige3, outlineVariant = Palette.Beige4,
            error = Color(0xFFE07A5F),
        ),
        typography = Typography(
            bodyLarge = TextStyle(fontFamily = Archivo, fontSize = 15.sp),
            bodyMedium = TextStyle(fontFamily = Archivo, fontSize = 13.sp),
            labelLarge = TextStyle(fontFamily = Archivo, fontWeight = FontWeight(600), fontSize = 13.sp),
            titleLarge = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight(600), fontSize = 19.sp),
        ),
        content = content,
    )
}
