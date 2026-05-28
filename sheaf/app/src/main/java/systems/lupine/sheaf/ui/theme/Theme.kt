package systems.lupine.sheaf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ── Color tokens ──────────────────────────────────────────────────────────────
//
// Aligned to the iOS app's violet-500 (#8B5CF6) accent so the two clients
// look like the same product. Token names mirror Material 3 tone levels
// loosely — the values themselves track Tailwind's violet scale rather
// than the MD3 default lookup, because iOS picked those exact hexes and
// matching them keeps brand colour identical across platforms. A future
// theme picker (see task list) will swap these out per palette.

val Purple10  = Color(0xFF2E1065)  // violet-950
val Purple20  = Color(0xFF4C1D95)  // violet-900
val Purple40  = Color(0xFF8B5CF6)  // violet-500, iOS accent / light-mode primary
val Purple60  = Color(0xFF9466F8)  // interpolated for secondary
val Purple80  = Color(0xFFA78BFA)  // violet-400, iOS accentLight / dark-mode primary
val Purple90  = Color(0xFFDDD6FE)  // violet-200, light secondaryContainer
val Purple99  = Color(0xFFF5F3FF)  // violet-50, light primaryContainer

val PurpleGrey10 = Color(0xFF1A1035)  // iOS light textPrimary; high-contrast text on light surfaces
val PurpleGrey20 = Color(0xFF2A1F50)
val PurpleGrey80 = Color(0xFFDCD6EE)  // light-on-dark text, violet-tinted
val PurpleGrey90 = Color(0xFFECE8F6)

val Teal40 = Color(0xFF1D9E75)
val Teal80 = Color(0xFF9FE1CB)

val Red40  = Color(0xFFE24B4A)
val Red80  = Color(0xFFF09595)

val Yellow10 = Color(0xFF221B00)
val Yellow40 = Color(0xFF7B5800)
val Yellow80 = Color(0xFFEFC032)
val Yellow90 = Color(0xFFFFDF9E)

// ── Warning colors (no MD3 built-in) ─────────────────────────────────────────

@Immutable
data class WarningColors(val container: Color, val onContainer: Color)

val LocalWarningColors = staticCompositionLocalOf {
    WarningColors(container = Yellow90, onContainer = Yellow10)
}

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
    background       = Color(0xFFF2F0FF),  // matches iOS light bgPrimary (lavender)
    onBackground     = PurpleGrey10,
    surface          = Color.White,
    onSurface        = PurpleGrey10,
    surfaceVariant   = PurpleGrey90,
    onSurfaceVariant = PurpleGrey20,
    outline          = Color(0xFFC4B5FD),  // violet-300
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
    background       = Color(0xFF0F0C29),  // matches iOS dark bgPrimary
    onBackground     = PurpleGrey80,
    surface          = Color(0xFF1A1535),  // matches iOS dark bgSecondary
    onSurface        = PurpleGrey80,
    surfaceVariant   = PurpleGrey20,
    onSurfaceVariant = PurpleGrey80,
    outline          = Color(0xFF4C2A85),  // violet-tinted, darker
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
    val warningColors = if (darkTheme)
        WarningColors(container = Yellow40, onContainer = Yellow80)
    else
        WarningColors(container = Yellow90, onContainer = Yellow10)

    CompositionLocalProvider(LocalWarningColors provides warningColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = SheafTypography,
            shapes      = Shapes(extraSmall = RoundedCornerShape(12.dp)),
            content     = content,
        )
    }
}
