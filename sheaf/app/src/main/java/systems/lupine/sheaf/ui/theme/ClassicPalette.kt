package systems.lupine.sheaf.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The pre-iOS-alignment purple Android shipped through 0.1.14. Kept
 * as a deliberate alternative so users who liked the darker, more
 * saturated `#534AB7` can still pick it once the catalog is exposed.
 *
 * Reads slightly more "Material 3 stock dark" than the iOS-aligned
 * default — neutral PurpleGrey surfaces rather than the violet-tinted
 * `#0F0C29` that the new default uses.
 */
internal object ClassicPalette : SheafPalette {
    override val id: String = "classic"
    override val displayName: String = "Classic"

    private val Purple10  = Color(0xFF26215C)
    private val Purple20  = Color(0xFF3C3489)
    private val Purple40  = Color(0xFF534AB7)  // pre-alignment light primary
    private val Purple60  = Color(0xFF7F77DD)
    private val Purple80  = Color(0xFFAFA9EC)  // pre-alignment dark primary
    private val Purple90  = Color(0xFFCECBF6)
    private val Purple99  = Color(0xFFEEEDFE)

    private val PurpleGrey10 = Color(0xFF1A1826)
    private val PurpleGrey20 = Color(0xFF2D2B46)
    private val PurpleGrey80 = Color(0xFFCBC8E8)
    private val PurpleGrey90 = Color(0xFFE8E6F5)

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
        background           = Color(0xFFF7F7FB),
        onBackground         = PurpleGrey10,
        surface              = Color.White,
        onSurface            = PurpleGrey10,
        surfaceVariant       = PurpleGrey90,
        onSurfaceVariant     = PurpleGrey20,
        outline              = Color(0xFFC0BDE8),
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
        background           = Color(0xFF13121E),
        onBackground         = PurpleGrey80,
        surface              = Color(0xFF1E1D2E),
        onSurface            = PurpleGrey80,
        surfaceVariant       = PurpleGrey20,
        onSurfaceVariant     = PurpleGrey80,
        outline              = Color(0xFF453F6F),
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
