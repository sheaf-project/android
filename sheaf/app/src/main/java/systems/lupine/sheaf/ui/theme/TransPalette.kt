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

    override val light = lightColorScheme(
        primary              = TransPinkDeeper,
        onPrimary            = Color.White,
        primaryContainer     = Color(0xFFFCE1E7),
        onPrimaryContainer   = Color(0xFF3A0A1E),
        secondary            = TransBlueDeeper,
        onSecondary          = Color.White,
        secondaryContainer   = Color(0xFFD9F1FB),
        onSecondaryContainer = Color(0xFF062F45),
        tertiary             = Color(0xFFC084FC),  // light purple bridge between the two flag hues
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
        primary              = TransPink,         // brighter on the dark surface
        onPrimary            = Color(0xFF3D0418),
        primaryContainer     = Color(0xFF5E1230),
        onPrimaryContainer   = Color(0xFFFCE1E7),
        secondary            = TransBlue,
        onSecondary          = Color(0xFF002F45),
        secondaryContainer   = Color(0xFF0E4D6E),
        onSecondaryContainer = Color(0xFFD9F1FB),
        tertiary             = Color(0xFFD8B4FE),
        onTertiary           = Color(0xFF3A0E5C),
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
