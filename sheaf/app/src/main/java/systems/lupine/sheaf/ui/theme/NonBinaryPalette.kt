package systems.lupine.sheaf.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Non-binary flag-inspired palette. The flag stripes are yellow,
 * white, purple, and black. Yellow doesn't make a viable Material 3
 * primary on white surfaces (contrast fails), so we lead with the
 * flag purple as primary and lift the yellow into the secondary slot
 * where it reads as a vivid accent without being load-bearing for
 * readability. The black stripe shows up as a deliberately darker
 * surface tint in dark mode.
 */
internal object NonBinaryPalette : SheafPalette {
    override val id: String = "nonbinary"
    override val displayName: String = "Non-binary"

    // Canonical NB flag hues.
    private val NbYellow       = Color(0xFFFCF434)
    private val NbYellowDeeper = Color(0xFFB69500)  // adapted for use as onPrimary / on-yellow text
    private val NbPurple       = Color(0xFF9C59D1)
    private val NbPurpleDeeper = Color(0xFF7B3FA4)
    private val NbBlack        = Color(0xFF1A1B23)

    private val GreyText10 = Color(0xFF1B102B)
    private val GreyText20 = Color(0xFF2D1F3E)
    private val GreyText80 = Color(0xFFE3D8EE)
    private val GreyText90 = Color(0xFFF1E8F7)

    private val Red40  = Color(0xFFE24B4A)
    private val Red80  = Color(0xFFF09595)

    override val light = lightColorScheme(
        primary              = NbPurpleDeeper,
        onPrimary            = Color.White,
        primaryContainer     = Color(0xFFF1E4FB),
        onPrimaryContainer   = Color(0xFF2E0F50),
        secondary            = NbYellowDeeper,
        onSecondary          = Color.White,
        secondaryContainer   = Color(0xFFFFF6B5),
        onSecondaryContainer = Color(0xFF3D2F00),
        tertiary             = Color(0xFF4A4A55),  // deliberate flag-black-as-neutral accent
        onTertiary           = Color.White,
        background           = Color(0xFFFCFAFD),
        onBackground         = GreyText10,
        surface              = Color.White,
        onSurface            = GreyText10,
        surfaceVariant       = GreyText90,
        onSurfaceVariant     = GreyText20,
        outline              = Color(0xFFD9C7E8),
        error                = Red40,
        onError              = Color.White,
    )

    override val dark = darkColorScheme(
        primary              = NbPurple,
        onPrimary            = Color(0xFF2E0F50),
        primaryContainer     = NbPurpleDeeper,
        onPrimaryContainer   = Color(0xFFF1E4FB),
        secondary            = NbYellow,
        onSecondary          = Color(0xFF3D2F00),
        secondaryContainer   = Color(0xFF5C4400),
        onSecondaryContainer = Color(0xFFFFF6B5),
        tertiary             = Color(0xFFD8C8E8),
        onTertiary           = Color(0xFF2A1F3E),
        background           = NbBlack,
        onBackground         = GreyText80,
        surface              = Color(0xFF22202B),
        onSurface            = GreyText80,
        surfaceVariant       = GreyText20,
        onSurfaceVariant     = GreyText80,
        outline              = Color(0xFF42384F),
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
