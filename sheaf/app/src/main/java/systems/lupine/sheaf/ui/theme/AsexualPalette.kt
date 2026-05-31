package systems.lupine.sheaf.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Ace-flag-inspired palette. Four-stripe flag: black, grey, white,
 * purple. Mostly monochrome, with purple as the single chromatic
 * accent.
 *
 * Slot mapping is unavoidably purple-dominant — there's only one
 * non-neutral hue in the flag, so it carries primary, primary-
 * container, and tertiary (as a desaturated variant). The grey
 * stripe gets the secondary slot (chips and nav indicator read as
 * the flag grey rather than randomly drifting toward blue). The
 * black stripe shows up as the dark-mode background, and the white
 * stripe drives light-mode surfaces.
 */
internal object AsexualPalette : SheafPalette {
    override val id: String = "asexual"
    override val displayName: String = "Asexual"

    // Canonical ace flag hues.
    private val AcePurple        = Color(0xFF810081)
    private val AcePurpleLifted  = Color(0xFFB233B2)   // adapted for tonal text in dark
    private val AcePurpleDeeper  = Color(0xFF5C005C)   // adapted for text-on-white in light
    private val AceGrey          = Color(0xFFA4A4A4)
    private val AceGreyDeeper    = Color(0xFF6E6E6E)
    private val AceCharcoal      = Color(0xFF1A1A1A)   // flag black, lifted off pure for surface readability

    // Desaturated bridge purple for the tertiary slot — sits between
    // the strong flag purple and the neutral grey so chips and
    // tertiary surfaces have somewhere to live without introducing a
    // third unrelated hue.
    private val AceMuted         = Color(0xFF7E6680)
    private val AceMutedLt       = Color(0xFFC8A8C8)

    private val GreyText10 = Color(0xFF1A0E1A)
    private val GreyText20 = Color(0xFF2E1F2E)
    private val GreyText80 = Color(0xFFE0DCE0)
    private val GreyText90 = Color(0xFFF0ECF0)

    private val Red40  = Color(0xFFE24B4A)
    private val Red80  = Color(0xFFF09595)

    override val light = lightColorScheme(
        primary              = AcePurpleDeeper,
        onPrimary            = Color.White,
        primaryContainer     = AcePurple,              // FAB: flag purple
        onPrimaryContainer   = Color.White,
        secondary            = AceGreyDeeper,
        onSecondary          = Color.White,
        secondaryContainer   = AceGrey,                // nav indicator: flag grey
        onSecondaryContainer = Color(0xFF1A1A1A),
        tertiary             = AceMuted,
        onTertiary           = Color.White,
        background           = Color.White,
        onBackground         = GreyText10,
        surface              = Color(0xFFF5F5F5),      // very light grey, picks up the grey stripe
        onSurface            = GreyText10,
        surfaceVariant       = GreyText90,
        onSurfaceVariant     = GreyText20,
        outline              = Color(0xFFC4A4C4),
        error                = Red40,
        onError              = Color.White,
    )

    override val dark = darkColorScheme(
        primary              = AcePurpleLifted,
        onPrimary            = Color(0xFF2A002A),
        primaryContainer     = AcePurple,              // FAB: flag purple
        onPrimaryContainer   = Color.White,
        secondary            = AceGrey,                // recognisable flag grey
        onSecondary          = Color(0xFF1A1A1A),
        secondaryContainer   = AceGreyDeeper,          // nav indicator: deeper grey, still grey
        onSecondaryContainer = Color.White,
        tertiary             = AceMutedLt,
        onTertiary           = Color(0xFF2A1F2E),
        background           = AceCharcoal,            // flag black as chrome
        onBackground         = GreyText80,
        surface              = Color(0xFF242424),
        onSurface            = GreyText80,
        surfaceVariant       = GreyText20,
        onSurfaceVariant     = GreyText80,
        outline              = Color(0xFF555055),
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
