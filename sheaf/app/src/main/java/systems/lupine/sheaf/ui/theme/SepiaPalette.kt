package systems.lupine.sheaf.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Warm amber / sunset palette. Reads "soft / comforting" — fewer blue
 * tones, easier on the eyes in dim environments. Dark backgrounds are
 * warm-toned rather than cool-neutral so the whole UI feels of a piece
 * rather than amber accents over a cold dark.
 *
 * Hex values track Tailwind's amber scale for primary tones and stone
 * for warm-neutral surfaces.
 */
internal object SepiaPalette : SheafPalette {
    override val id: String = "sepia"
    override val displayName: String = "Sepia"

    private val Amber50  = Color(0xFFFFFBEB)
    private val Amber100 = Color(0xFFFEF3C7)
    private val Amber200 = Color(0xFFFDE68A)
    private val Amber300 = Color(0xFFFCD34D)
    private val Amber400 = Color(0xFFFBBF24)  // dark primary
    private val Amber500 = Color(0xFFF59E0B)
    private val Amber600 = Color(0xFFD97706)  // light primary
    private val Amber700 = Color(0xFFB45309)
    private val Amber800 = Color(0xFF92400E)
    private val Amber900 = Color(0xFF78350F)
    private val Amber950 = Color(0xFF451A03)

    private val Stone100 = Color(0xFFF5F5F4)
    private val Stone800 = Color(0xFF292524)
    private val Stone900 = Color(0xFF1C1917)
    private val Stone950 = Color(0xFF0C0A09)

    private val GreyText10 = Color(0xFF2A1A07)
    private val GreyText20 = Color(0xFF3D2A12)
    private val GreyText80 = Color(0xFFE7D6BE)
    private val GreyText90 = Color(0xFFF5EAD3)

    private val OrangeRed40 = Color(0xFFC2410C)
    private val OrangeRed80 = Color(0xFFFB923C)
    private val Red40  = Color(0xFFE24B4A)
    private val Red80  = Color(0xFFF09595)

    override val light = lightColorScheme(
        primary              = Amber600,
        onPrimary            = Color.White,
        primaryContainer     = Amber50,
        onPrimaryContainer   = Amber950,
        secondary            = Amber500,
        onSecondary          = Color.White,
        secondaryContainer   = Amber200,
        onSecondaryContainer = Amber800,
        tertiary             = OrangeRed40,
        onTertiary           = Color.White,
        background           = Amber50,
        onBackground         = GreyText10,
        surface              = Stone100,
        onSurface            = GreyText10,
        surfaceVariant       = GreyText90,
        onSurfaceVariant     = GreyText20,
        outline              = Amber200,
        error                = Red40,
        onError              = Color.White,
    )

    override val dark = darkColorScheme(
        primary              = Amber400,
        onPrimary            = Amber900,
        primaryContainer     = Amber700,
        onPrimaryContainer   = Amber50,
        secondary            = Amber300,
        onSecondary          = Amber950,
        secondaryContainer   = Amber800,
        onSecondaryContainer = Amber100,
        tertiary             = OrangeRed80,
        onTertiary           = Color(0xFF3A1A02),
        background           = Stone950,
        onBackground         = GreyText80,
        surface              = Stone900,
        onSurface            = GreyText80,
        surfaceVariant       = Stone800,
        onSurfaceVariant     = GreyText80,
        outline              = Color(0xFF5E3A1A),
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
