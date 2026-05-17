package systems.lupine.sheaf.wear.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

// Wear OS Play review expects #000000 backgrounds for AMOLED and
// always-on display friendliness — pixels that are fully black draw
// no current. The previous Color(0xFF13121E) read as "dark blue" to
// review, and got us rejected. Surface stays at solid black too;
// component-level contrast comes from the primary/secondary chips
// and the surfaceVariant computation Wear MaterialTheme does
// internally.
private val WearColors = Colors(
    primary          = Color(0xFF7F77DD),
    primaryVariant   = Color(0xFF534AB7),
    secondary        = Color(0xFF9FE1CB),
    secondaryVariant = Color(0xFF1D9E75),
    background       = Color.Black,
    surface          = Color.Black,
    onPrimary        = Color.White,
    onSecondary      = Color(0xFF004D36),
    onBackground     = Color(0xFFCBC8E8),
    onSurface        = Color(0xFFCBC8E8),
    onError          = Color.White,
)

@Composable
fun SheafWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = WearColors,
        content = content,
    )
}
