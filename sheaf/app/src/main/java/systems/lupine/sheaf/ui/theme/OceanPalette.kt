package systems.lupine.sheaf.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Sky-blue palette. Reads "professional / clean" and stays a long way
 * away from the purple default for users who want a visually different
 * Sheaf. Backgrounds carry a faint blue tint so the chrome doesn't
 * read as stock-Android-blue.
 *
 * Hex values track Tailwind's sky scale.
 */
internal object OceanPalette : SheafPalette {
    override val id: String = "ocean"
    override val displayName: String = "Ocean"

    private val Sky50  = Color(0xFFF0F9FF)
    private val Sky100 = Color(0xFFE0F2FE)
    private val Sky200 = Color(0xFFBAE6FD)
    private val Sky300 = Color(0xFF7DD3FC)
    private val Sky400 = Color(0xFF38BDF8)  // dark primary
    private val Sky500 = Color(0xFF0EA5E9)
    private val Sky600 = Color(0xFF0284C7)  // light primary
    private val Sky700 = Color(0xFF0369A1)
    private val Sky800 = Color(0xFF075985)
    private val Sky900 = Color(0xFF0C4A6E)
    private val Sky950 = Color(0xFF082F49)

    private val GreyText10 = Color(0xFF0B1929)
    private val GreyText20 = Color(0xFF1A3047)
    private val GreyText80 = Color(0xFFD3E4F4)
    private val GreyText90 = Color(0xFFE8F2FA)

    private val Cyan40 = Color(0xFF0891B2)
    private val Cyan80 = Color(0xFF67E8F9)
    private val Red40  = Color(0xFFE24B4A)
    private val Red80  = Color(0xFFF09595)

    override val light = lightColorScheme(
        primary              = Sky600,
        onPrimary            = Color.White,
        primaryContainer     = Sky50,
        onPrimaryContainer   = Sky950,
        secondary            = Sky500,
        onSecondary          = Color.White,
        secondaryContainer   = Sky200,
        onSecondaryContainer = Sky800,
        tertiary             = Cyan40,
        onTertiary           = Color.White,
        background           = Sky50,
        onBackground         = GreyText10,
        surface              = Color.White,
        onSurface            = GreyText10,
        surfaceVariant       = GreyText90,
        onSurfaceVariant     = GreyText20,
        outline              = Sky200,
        error                = Red40,
        onError              = Color.White,
    )

    override val dark = darkColorScheme(
        primary              = Sky400,
        onPrimary            = Sky900,
        primaryContainer     = Sky700,
        onPrimaryContainer   = Sky50,
        secondary            = Sky300,
        onSecondary          = Sky950,
        secondaryContainer   = Sky800,
        onSecondaryContainer = Sky100,
        tertiary             = Cyan80,
        onTertiary           = Color(0xFF003E47),
        background           = Color(0xFF071525),
        onBackground         = GreyText80,
        surface              = Color(0xFF0E2238),
        onSurface            = GreyText80,
        surfaceVariant       = GreyText20,
        onSurfaceVariant     = GreyText80,
        outline              = Color(0xFF1F4A75),
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
