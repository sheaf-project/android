package systems.lupine.sheaf.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Warm crimson palette. Reads "bold / energetic" without crossing into
 * "error / alert" territory; the primary lands deep enough to not be
 * confused with the destructive-action red used in error chrome, and
 * the tertiary picks up an orange complement so warm accents don't all
 * land on the same hue.
 *
 * Distinct from the Pride palette's flag-iconic red stripe — that one
 * is a flag reference, this one is a general warm-UI palette.
 *
 * Hex values track Tailwind's red and rose scales.
 */
internal object CrimsonPalette : SheafPalette {
    override val id: String = "crimson"
    override val displayName: String = "Crimson"

    private val Red50   = Color(0xFFFEF2F2)
    private val Red100  = Color(0xFFFEE2E2)
    private val Red200  = Color(0xFFFECACA)
    private val Red300  = Color(0xFFFCA5A5)
    private val Red400  = Color(0xFFF87171)   // dark primary
    private val Red500  = Color(0xFFEF4444)
    private val Red600  = Color(0xFFDC2626)   // light primary
    private val Red700  = Color(0xFFB91C1C)
    private val Red800  = Color(0xFF991B1B)
    private val Red900  = Color(0xFF7F1D1D)
    private val Red950  = Color(0xFF450A0A)

    private val Rose400 = Color(0xFFFB7185)
    private val Rose500 = Color(0xFFF43F5E)
    private val Rose700 = Color(0xFFBE123C)

    private val Orange500 = Color(0xFFF97316)
    private val Orange700 = Color(0xFFC2410C)

    private val GreyText10 = Color(0xFF1F0F0F)
    private val GreyText20 = Color(0xFF3A1F1F)
    private val GreyText80 = Color(0xFFEAD6D6)
    private val GreyText90 = Color(0xFFF5E8E8)

    // Error slot has to remain distinguishable from primary, which is
    // itself red. Pull the error tone toward a more orange-tinted red
    // and lift saturation so destructive-action chrome doesn't blend
    // into a confirmatory CTA.
    private val ErrorLight = Color(0xFFB42318)
    private val ErrorDark  = Color(0xFFFFB4AB)

    override val light = lightColorScheme(
        primary              = Red600,
        onPrimary            = Color.White,
        primaryContainer     = Red100,
        onPrimaryContainer   = Red900,
        secondary            = Rose500,
        onSecondary          = Color.White,
        secondaryContainer   = Color(0xFFFECDD3),  // rose-200
        onSecondaryContainer = Color(0xFF881337),  // rose-900
        tertiary             = Orange700,
        onTertiary           = Color.White,
        background           = Color(0xFFFEF8F8),  // faint pink-tinted white
        onBackground         = GreyText10,
        surface              = Color.White,
        onSurface            = GreyText10,
        surfaceVariant       = GreyText90,
        onSurfaceVariant     = GreyText20,
        outline              = Red200,
        error                = ErrorLight,
        onError              = Color.White,
    )

    override val dark = darkColorScheme(
        primary              = Red400,
        onPrimary            = Red900,
        primaryContainer     = Red700,
        onPrimaryContainer   = Red50,
        secondary            = Rose400,
        onSecondary          = Color(0xFF4C0519),
        secondaryContainer   = Rose700,
        onSecondaryContainer = Color(0xFFFFE4E6),
        tertiary             = Orange500,
        onTertiary           = Color(0xFF3D1A02),
        background           = Color(0xFF1A0F0F),  // warm-tinted dark
        onBackground         = GreyText80,
        surface              = Color(0xFF261515),
        onSurface            = GreyText80,
        surfaceVariant       = GreyText20,
        onSurfaceVariant     = GreyText80,
        outline              = Color(0xFF6E3838),
        error                = ErrorDark,
        onError              = Color(0xFF690005),
    )

    private val Yellow10 = Color(0xFF221B00)
    private val Yellow40 = Color(0xFF7B5800)
    private val Yellow80 = Color(0xFFEFC032)
    private val Yellow90 = Color(0xFFFFDF9E)

    override val warningLight = WarningColors(container = Yellow90, onContainer = Yellow10)
    override val warningDark  = WarningColors(container = Yellow40, onContainer = Yellow80)
}
