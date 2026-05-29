package systems.lupine.sheaf.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// ── Warning colours (no Material 3 built-in slot) ────────────────────────────

@Immutable
data class WarningColors(val container: Color, val onContainer: Color)

val LocalWarningColors = staticCompositionLocalOf {
    // Fallback for previews + any composable that's used outside SheafTheme.
    // Real values come from the active palette's warningLight / warningDark.
    WarningColors(container = Color(0xFFFFDF9E), onContainer = Color(0xFF221B00))
}

// ── Theme entry point ────────────────────────────────────────────────────────

/**
 * Root theme composable.
 *
 * The user's themeMode preference ("system" / "light" / "dark") and
 * themePalette preference ("purple" / future entries) are orthogonal:
 * the palette decides *which* colours, the mode decides whether to use
 * the light or dark variant of those colours.
 *
 * Material You is a special case — it derives colours from the system
 * wallpaper at render time rather than carrying them as constants. When
 * that palette is active we call `dynamicLight/DarkColorScheme(context)`
 * instead of reading from the [SheafPalette].
 */
@Composable
fun SheafTheme(
    themeMode: String = "system",
    themePalette: String = SheafPalette.default.id,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        "dark"  -> true
        "light" -> false
        else    -> isSystemInDarkTheme()
    }
    val palette = SheafPalette.fromId(themePalette)
    val context = LocalContext.current

    val colorScheme = when {
        palette.id == SheafPalette.MATERIAL_YOU_ID &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)

        darkTheme -> palette.dark
        else      -> palette.light
    }

    val warningColors = if (darkTheme) palette.warningDark else palette.warningLight

    CompositionLocalProvider(LocalWarningColors provides warningColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = SheafTypography,
            shapes      = Shapes(extraSmall = RoundedCornerShape(12.dp)),
            content     = content,
        )
    }
}
