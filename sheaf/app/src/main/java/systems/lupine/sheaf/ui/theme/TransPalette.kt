package systems.lupine.sheaf.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Trans-flag-inspired palette. The flag's iconic hues — light blue,
 * pink, white — don't all work as Material 3 primary tones (white
 * fails on a light surface, the lighter flag pink fails contrast as
 * a button colour) so we use a deeper pink as primary, the flag blue
 * as secondary, and pick a darker variant of the flag blue for
 * tertiary so chips and accents stay distinct without leaving the
 * trans-flag colour family.
 */
internal object TransPalette : SheafPalette {
    override val id: String = "trans"
    override val displayName: String = "Trans"

    // Canonical trans flag hues (slightly desaturated when used as
    // surfaces to stay legible against the rest of the UI).
    private val TransBlue       = Color(0xFF55CDFC)
    private val TransPink       = Color(0xFFF7A8B8)
    private val TransPinkDeeper = Color(0xFFE16A8C)  // adapted for use as a primary on white
    private val TransBlueDeeper = Color(0xFF2A9FD6)

    private val GreyText10 = Color(0xFF1B1A1F)
    private val GreyText20 = Color(0xFF2D2B36)
    private val GreyText80 = Color(0xFFE5E1EE)
    private val GreyText90 = Color(0xFFF1EDF7)

    private val Red40  = Color(0xFFE24B4A)
    private val Red80  = Color(0xFFF09595)

    // FAB / nav-indicator pinks and blues. M3 renders the FAB from
    // primaryContainer and the bottom-nav selection blob from
    // secondaryContainer, so to read as "trans" those two slots need
    // to carry the *actual flag pink and blue*, not muted-into-the-
    // background derivatives. The primary/secondary slots that drive
    // text-on-tonal-surfaces still pick the lighter / deeper variants
    // for contrast.

    override val light = lightColorScheme(
        primary              = TransPinkDeeper,  // pink-on-white for tonal accents
        onPrimary            = Color.White,
        primaryContainer     = TransPink,        // FAB: flag pink, recognisable
        onPrimaryContainer   = Color(0xFF3D0418),
        secondary            = TransBlueDeeper,
        onSecondary          = Color.White,
        secondaryContainer   = TransBlue,        // nav indicator: flag blue
        onSecondaryContainer = Color(0xFF062F45),
        // Soft pink-blue bridge picks up the white stripe of the flag
        // as a near-neutral tone, rather than randomly inserting a
        // third unrelated hue. Tinted slightly cool so it leans toward
        // the flag blue without competing with the secondary slot.
        tertiary             = Color(0xFFA0B6CC),
        onTertiary           = Color.White,
        background           = Color(0xFFFFFAFC),  // very faint pink-tinted white
        onBackground         = GreyText10,
        surface              = Color.White,
        onSurface            = GreyText10,
        surfaceVariant       = GreyText90,
        onSurfaceVariant     = GreyText20,
        outline              = Color(0xFFE9C0CC),
        error                = Red40,
        onError              = Color.White,
    )

    override val dark = darkColorScheme(
        primary              = TransPink,         // recognisable flag pink for tonal accents
        onPrimary            = Color(0xFF3D0418),
        primaryContainer     = TransPinkDeeper,   // FAB: deeper flag pink, still clearly pink
        onPrimaryContainer   = Color.White,
        secondary            = TransBlue,         // recognisable flag blue for tonal accents
        onSecondary          = Color(0xFF002F45),
        secondaryContainer   = TransBlueDeeper,   // nav indicator: deeper flag blue
        onSecondaryContainer = Color.White,
        // Lighter version of the same near-neutral pink-blue bridge
        // for dark mode; reads as "soft flag white" without breaking
        // contrast on the dark surface.
        tertiary             = Color(0xFFC9D6E2),
        onTertiary           = Color(0xFF1B2638),
        background           = Color(0xFF161220),
        onBackground         = GreyText80,
        surface              = Color(0xFF1F1A2D),
        onSurface            = GreyText80,
        surfaceVariant       = GreyText20,
        onSurfaceVariant     = GreyText80,
        outline              = Color(0xFF45354A),
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
