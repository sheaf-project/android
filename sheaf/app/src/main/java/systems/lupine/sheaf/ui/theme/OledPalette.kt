package systems.lupine.sheaf.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * True-black dark variant of the default Purple palette, intended for
 * AMOLED displays where any non-black pixel actively consumes battery.
 * Surface and background are `#000000`; surface containers are very
 * dark `#0A0814` so cards remain perceptible against the background.
 *
 * Light mode mirrors [PurplePalette] — selecting OLED on a phone in
 * light mode shouldn't degrade the light experience. Closes task #64.
 */
internal object OledPalette : SheafPalette {
    override val id: String = "oled"
    override val displayName: String = "OLED"

    private val Purple10  = Color(0xFF2E1065)
    private val Purple20  = Color(0xFF4C1D95)
    private val Purple40  = Color(0xFF8B5CF6)
    private val Purple60  = Color(0xFF9466F8)
    private val Purple80  = Color(0xFFA78BFA)
    private val Purple90  = Color(0xFFDDD6FE)
    private val Purple99  = Color(0xFFF5F3FF)

    private val PurpleGrey10 = Color(0xFF1A1035)
    private val PurpleGrey20 = Color(0xFF2A1F50)
    private val PurpleGrey80 = Color(0xFFDCD6EE)
    private val PurpleGrey90 = Color(0xFFECE8F6)

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
        background           = Color(0xFFF2F0FF),
        onBackground         = PurpleGrey10,
        surface              = Color.White,
        onSurface            = PurpleGrey10,
        surfaceVariant       = PurpleGrey90,
        onSurfaceVariant     = PurpleGrey20,
        outline              = Color(0xFFC4B5FD),
        error                = Red40,
        onError              = Color.White,
    )

    override val dark = darkColorScheme(
        primary              = Purple80,
        onPrimary            = Purple20,
        primaryContainer     = Color(0xFF1A0F45),  // deeper than Purple40 so it still reads against #000
        onPrimaryContainer   = Purple99,
        secondary            = Purple90,
        onSecondary          = Purple10,
        secondaryContainer   = Color(0xFF14092E),
        onSecondaryContainer = Purple90,
        tertiary             = Teal80,
        onTertiary           = Color(0xFF004D36),
        background           = Color.Black,                  // OLED win
        onBackground         = PurpleGrey80,
        surface              = Color.Black,                  // OLED win
        onSurface            = PurpleGrey80,
        // Surface containers are not pure black so cards have a visible
        // edge against the background — the saving still applies to the
        // majority of pixels which are full background.
        surfaceVariant       = Color(0xFF0A0814),
        onSurfaceVariant     = PurpleGrey80,
        surfaceContainer     = Color(0xFF0A0814),
        surfaceContainerLow  = Color(0xFF05030A),
        surfaceContainerHigh = Color(0xFF10081D),
        outline              = Color(0xFF35206A),
        error                = Red80,
        onError              = Color(0xFF690005),
    )

    private val Yellow10 = Color(0xFF221B00)
    private val Yellow40 = Color(0xFF7B5800)
    private val Yellow80 = Color(0xFFEFC032)
    private val Yellow90 = Color(0xFFFFDF9E)

    override val warningLight = WarningColors(container = Yellow90, onContainer = Yellow10)
    override val warningDark  = WarningColors(container = Color(0xFF332600), onContainer = Yellow80)
}
