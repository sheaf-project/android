package systems.lupine.sheaf.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Pride-flag-inspired palette. Hero accent is rainbow pink, with the
 * secondary/tertiary slots picking up the warm and cool ends of the
 * flag so chips and tertiary surfaces complete the spectrum without
 * the chrome reading as a rainbow soup. Surfaces stay neutral.
 *
 * Style is "flag-coloured accents on a Sheaf-shaped UI", not "rainbow
 * background everywhere" — the latter is harder to read at length
 * and tends to undermine the seriousness of plural identity work.
 */
internal object PridePalette : SheafPalette {
    override val id: String = "pride"
    override val displayName: String = "Pride"

    // Pride hero hues, roughly aligned to the rainbow flag stripes.
    private val PrideRed     = Color(0xFFE53935)
    private val PrideOrange  = Color(0xFFF57C00)
    private val PrideYellow  = Color(0xFFFBC02D)
    private val PrideGreen   = Color(0xFF43A047)
    private val PrideBlue    = Color(0xFF1E88E5)
    private val PridePurple  = Color(0xFF8E24AA)
    private val PridePink    = Color(0xFFD81B60)
    private val PridePinkLt  = Color(0xFFF06292)

    private val GreyText10 = Color(0xFF1F0A18)
    private val GreyText20 = Color(0xFF3A1F30)
    private val GreyText80 = Color(0xFFEAD6E0)
    private val GreyText90 = Color(0xFFF5E8EF)

    private val Red40  = Color(0xFFE24B4A)
    private val Red80  = Color(0xFFF09595)

    // FAB / nav-indicator slots carry the rainbow hero colours
    // directly (primaryContainer = pink, secondaryContainer = yellow),
    // so the primary chrome reads as "rainbow Sheaf" instead of "dark
    // plum somewhere near pink". The lighter/darker variants in the
    // primary/secondary slots drive on-tonal-surface text rendering.

    override val light = lightColorScheme(
        primary              = PridePink,
        onPrimary            = Color.White,
        primaryContainer     = PridePinkLt,         // FAB: vivid rainbow pink
        onPrimaryContainer   = Color(0xFF3A0A1E),
        secondary            = PrideOrange,
        onSecondary          = Color.White,
        secondaryContainer   = PrideYellow,         // nav indicator: rainbow yellow
        onSecondaryContainer = Color(0xFF3D2F00),
        tertiary             = PrideBlue,
        onTertiary           = Color.White,
        background           = Color(0xFFFFF8FA),  // faint pink-tinted white
        onBackground         = GreyText10,
        surface              = Color.White,
        onSurface            = GreyText10,
        surfaceVariant       = GreyText90,
        onSurfaceVariant     = GreyText20,
        outline              = Color(0xFFE6BCC7),
        error                = Red40,
        onError              = Color.White,
    )

    override val dark = darkColorScheme(
        primary              = PridePinkLt,        // recognisable pink for tonal text
        onPrimary            = Color(0xFF3D0418),
        primaryContainer     = PridePink,          // FAB: deeper but still vivid pink
        onPrimaryContainer   = Color.White,
        secondary            = PrideYellow,        // recognisable yellow for tonal text
        onSecondary          = Color(0xFF3A2A00),
        secondaryContainer   = Color(0xFFD4A800),  // nav indicator: deeper amber, recognisably yellow
        onSecondaryContainer = Color(0xFF3A2A00),
        tertiary             = Color(0xFF64B5F6),
        onTertiary           = Color(0xFF002F58),
        background           = Color(0xFF1A0F1A),  // very dark plum
        onBackground         = GreyText80,
        surface              = Color(0xFF231423),
        onSurface            = GreyText80,
        surfaceVariant       = GreyText20,
        onSurfaceVariant     = GreyText80,
        outline              = Color(0xFF503245),
        error                = Red80,
        onError              = Color(0xFF690005),
    )

    private val Yellow10 = Color(0xFF221B00)
    private val Yellow40 = Color(0xFF7B5800)
    private val Yellow80 = Color(0xFFEFC032)
    private val Yellow90 = Color(0xFFFFDF9E)

    override val warningLight = WarningColors(container = Yellow90, onContainer = Yellow10)
    override val warningDark  = WarningColors(container = Yellow40, onContainer = Yellow80)

    // Unused accents but kept named so future iterations can wire
    // them in (e.g. a multi-coloured swatch preview).
    @Suppress("unused")
    private val unusedFlagHues = listOf(PrideRed, PrideOrange, PrideYellow, PrideGreen, PrideBlue, PridePurple)
}
