package systems.lupine.sheaf.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Emerald-green palette. Reads "calm / focus" — softer than the
 * default purple, useful for users who'd rather not have the brand
 * accent throughout the UI. Backgrounds carry a faint green tint so
 * cards still feel like the same product rather than a stock
 * Material 3 green theme.
 *
 * Hex values track Tailwind's emerald scale; that scale is well-
 * validated for WCAG AA contrast across the tones used here.
 */
internal object MintPalette : SheafPalette {
    override val id: String = "mint"
    override val displayName: String = "Mint"

    private val Emerald50  = Color(0xFFECFDF5)
    private val Emerald100 = Color(0xFFD1FAE5)
    private val Emerald200 = Color(0xFFA7F3D0)
    private val Emerald300 = Color(0xFF6EE7B7)
    private val Emerald400 = Color(0xFF34D399)  // dark primary
    private val Emerald500 = Color(0xFF10B981)
    private val Emerald600 = Color(0xFF059669)  // light primary
    private val Emerald700 = Color(0xFF047857)
    private val Emerald800 = Color(0xFF065F46)
    private val Emerald900 = Color(0xFF064E3B)
    private val Emerald950 = Color(0xFF022C22)

    private val GreyText10 = Color(0xFF0F1F1A)
    private val GreyText80 = Color(0xFFD6E8E0)
    private val GreyText20 = Color(0xFF1F3A30)
    private val GreyText90 = Color(0xFFEAF4EF)

    private val Teal40 = Color(0xFF0D9488)
    private val Teal80 = Color(0xFF99F6E4)
    private val Red40  = Color(0xFFE24B4A)
    private val Red80  = Color(0xFFF09595)

    override val light = lightColorScheme(
        primary              = Emerald600,
        onPrimary            = Color.White,
        primaryContainer     = Emerald50,
        onPrimaryContainer   = Emerald950,
        secondary            = Emerald500,
        onSecondary          = Color.White,
        secondaryContainer   = Emerald200,
        onSecondaryContainer = Emerald800,
        tertiary             = Teal40,
        onTertiary           = Color.White,
        background           = Emerald50,
        onBackground         = GreyText10,
        surface              = Color.White,
        onSurface            = GreyText10,
        surfaceVariant       = GreyText90,
        onSurfaceVariant     = GreyText20,
        outline              = Emerald200,
        error                = Red40,
        onError              = Color.White,
    )

    override val dark = darkColorScheme(
        primary              = Emerald400,
        onPrimary            = Emerald900,
        primaryContainer     = Emerald700,
        onPrimaryContainer   = Emerald50,
        secondary            = Emerald300,
        onSecondary          = Emerald950,
        secondaryContainer   = Emerald800,
        onSecondaryContainer = Emerald100,
        tertiary             = Teal80,
        onTertiary           = Color(0xFF003736),
        background           = Color(0xFF071F18),
        onBackground         = GreyText80,
        surface              = Color(0xFF0C2A21),
        onSurface            = GreyText80,
        surfaceVariant       = GreyText20,
        onSurfaceVariant     = GreyText80,
        outline              = Color(0xFF1F4E3D),
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
