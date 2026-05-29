package systems.lupine.sheaf.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * iOS-aligned violet palette. Shipped as the default from 0.1.15
 * onwards; the Android primary lines up with the iOS app's accent
 * (violet-500, `#8B5CF6`) so the two clients read as the same product.
 *
 * Token values previously lived in `Theme.kt` as file-level constants;
 * moved here so each palette owns its own colours and a future picker
 * can swap palettes without touching theme infrastructure.
 */
internal object PurplePalette : SheafPalette {
    override val id: String = "purple"
    override val displayName: String = "Purple"

    // ── Token aliases (kept close to other palettes' naming) ──────────────────
    //
    // Loosely mirror Material 3's tone levels — the values themselves track
    // Tailwind's violet scale rather than the MD3 default lookup, because
    // iOS picked these exact hexes.

    private val Purple10  = Color(0xFF2E1065)  // violet-950
    private val Purple20  = Color(0xFF4C1D95)  // violet-900
    private val Purple40  = Color(0xFF8B5CF6)  // violet-500, light primary
    private val Purple60  = Color(0xFF9466F8)  // interpolated for secondary
    private val Purple80  = Color(0xFFA78BFA)  // violet-400, dark primary
    private val Purple90  = Color(0xFFDDD6FE)  // violet-200, light secondaryContainer
    private val Purple99  = Color(0xFFF5F3FF)  // violet-50, light primaryContainer

    private val PurpleGrey10 = Color(0xFF1A1035)
    private val PurpleGrey20 = Color(0xFF2A1F50)
    private val PurpleGrey80 = Color(0xFFDCD6EE)
    private val PurpleGrey90 = Color(0xFFECE8F6)

    private val Teal40 = Color(0xFF1D9E75)
    private val Teal80 = Color(0xFF9FE1CB)

    private val Red40  = Color(0xFFE24B4A)
    private val Red80  = Color(0xFFF09595)

    override val light = lightColorScheme(
        primary              = Purple40,
        onPrimary            = Color.White,
        primaryContainer     = Purple99,
        onPrimaryContainer   = Purple10,
        secondary            = Purple60,
        onSecondary          = Color.White,
        secondaryContainer   = Purple90,
        onSecondaryContainer = Purple20,
        tertiary             = Teal40,
        onTertiary           = Color.White,
        background           = Color(0xFFF2F0FF),  // iOS light bgPrimary (lavender)
        onBackground         = PurpleGrey10,
        surface              = Color.White,
        onSurface            = PurpleGrey10,
        surfaceVariant       = PurpleGrey90,
        onSurfaceVariant     = PurpleGrey20,
        outline              = Color(0xFFC4B5FD),  // violet-300
        error                = Red40,
        onError              = Color.White,
    )

    override val dark = darkColorScheme(
        primary              = Purple80,
        onPrimary            = Purple20,
        primaryContainer     = Purple40,
        onPrimaryContainer   = Purple99,
        secondary            = Purple90,
        onSecondary          = Purple10,
        secondaryContainer   = Purple20,
        onSecondaryContainer = Purple90,
        tertiary             = Teal80,
        onTertiary           = Color(0xFF004D36),
        background           = Color(0xFF0F0C29),  // iOS dark bgPrimary
        onBackground         = PurpleGrey80,
        surface              = Color(0xFF1A1535),  // iOS dark bgSecondary
        onSurface            = PurpleGrey80,
        surfaceVariant       = PurpleGrey20,
        onSurfaceVariant     = PurpleGrey80,
        outline              = Color(0xFF4C2A85),  // violet-tinted, darker
        error                = Red80,
        onError              = Color(0xFF690005),
    )

    private val Yellow10 = Color(0xFF221B00)
    private val Yellow40 = Color(0xFF7B5800)
    private val Yellow80 = Color(0xFFEFC032)
    private val Yellow90 = Color(0xFFFFDF9E)

    override val warningLight = WarningColors(container = Yellow90, onContainer = Yellow10)
    override val warningDark  = WarningColors(container = Yellow40, onContainer = Yellow80)
}
