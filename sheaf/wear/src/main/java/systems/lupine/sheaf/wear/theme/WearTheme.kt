package systems.lupine.sheaf.wear.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

private val WearColors = Colors(
    primary          = Color(0xFF7F77DD),
    primaryVariant   = Color(0xFF534AB7),
    secondary        = Color(0xFF9FE1CB),
    secondaryVariant = Color(0xFF1D9E75),
    background       = Color(0xFF13121E),
    surface          = Color(0xFF1E1D2E),
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
