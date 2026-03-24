package systems.lupine.sheaf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Color tokens ──────────────────────────────────────────────────────────────

val Purple10  = Color(0xFF26215C)
val Purple20  = Color(0xFF3C3489)
val Purple40  = Color(0xFF534AB7)
val Purple60  = Color(0xFF7F77DD)
val Purple80  = Color(0xFFAFA9EC)
val Purple90  = Color(0xFFCECBF6)
val Purple99  = Color(0xFFEEEDFE)

val PurpleGrey10 = Color(0xFF1A1826)
val PurpleGrey20 = Color(0xFF2D2B46)
val PurpleGrey80 = Color(0xFFCBC8E8)
val PurpleGrey90 = Color(0xFFE8E6F5)

val Teal40 = Color(0xFF1D9E75)
val Teal80 = Color(0xFF9FE1CB)

val Red40  = Color(0xFFE24B4A)
val Red80  = Color(0xFFF09595)

// ── Color schemes ─────────────────────────────────────────────────────────────

internal val LightColorScheme = lightColorScheme(
    primary          = Purple40,
    onPrimary        = Color.White,
    primaryContainer = Purple99,
    onPrimaryContainer = Purple10,
    secondary        = Purple60,
    onSecondary      = Color.White,
    secondaryContainer = Purple90,
    onSecondaryContainer = Purple20,
    tertiary         = Teal40,
    onTertiary       = Color.White,
    background       = Color(0xFFF7F7FB),
    onBackground     = PurpleGrey10,
    surface          = Color.White,
    onSurface        = PurpleGrey10,
    surfaceVariant   = PurpleGrey90,
    onSurfaceVariant = PurpleGrey20,
    outline          = Color(0xFFC0BDE8),
    error            = Red40,
    onError          = Color.White,
)

internal val DarkColorScheme = darkColorScheme(
    primary          = Purple80,
    onPrimary        = Purple20,
    primaryContainer = Purple40,
    onPrimaryContainer = Purple99,
    secondary        = Purple90,
    onSecondary      = Purple10,
    secondaryContainer = Purple20,
    onSecondaryContainer = Purple90,
    tertiary         = Teal80,
    onTertiary       = Color(0xFF004D36),
    background       = Color(0xFF13121E),
    onBackground     = PurpleGrey80,
    surface          = Color(0xFF1E1D2E),
    onSurface        = PurpleGrey80,
    surfaceVariant   = PurpleGrey20,
    onSurfaceVariant = PurpleGrey80,
    outline          = Color(0xFF453F6F),
    error            = Red80,
    onError          = Color(0xFF690005),
)

// ── Theme entry point ─────────────────────────────────────────────────────────

@Composable
fun SheafTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        "dark"  -> true
        "light" -> false
        else    -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = SheafTypography,
        content     = content,
    )
}
