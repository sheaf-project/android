package systems.lupine.sheaf.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Saturated yellow palette. Reads "highlighter / bright" — distinct
 * from Sepia, which is warm-neutral-with-amber-accent. Where Sepia is
 * sunset comfort, Goldenrod is summer noon.
 *
 * Yellow on white is the classic contrast-fail pairing. We use the
 * deeper yellow-700 tone for primary (text-on-white safe) and let the
 * vivid yellow-400 live in primaryContainer where it's the FAB colour
 * with deep-amber on-text. A cool blue tertiary keeps the palette
 * from being monochromatic.
 *
 * Hex values track Tailwind's yellow scale.
 */
internal object GoldenrodPalette : SheafPalette {
    override val id: String = "goldenrod"
    override val displayName: String = "Goldenrod"

    private val Yellow50  = Color(0xFFFEFCE8)
    private val Yellow100 = Color(0xFFFEF9C3)
    private val Yellow200 = Color(0xFFFEF08A)
    private val Yellow300 = Color(0xFFFDE047)
    private val Yellow400 = Color(0xFFFACC15)   // dark primary / FAB hero
    private val Yellow500 = Color(0xFFEAB308)
    private val Yellow600 = Color(0xFFCA8A04)
    private val Yellow700 = Color(0xFFA16207)   // light primary (text-on-white safe)
    private val Yellow800 = Color(0xFF854D0E)
    private val Yellow900 = Color(0xFF713F12)
    private val Yellow950 = Color(0xFF422006)

    private val Amber600  = Color(0xFFD97706)
    private val Amber800  = Color(0xFF92400E)

    // Cool complement so the palette isn't monochrome. Blue-800 picks
    // up well against warm yellow surfaces without pulling the whole
    // palette away from "this is the yellow theme".
    private val Blue400   = Color(0xFF60A5FA)
    private val Blue800   = Color(0xFF1E40AF)

    private val GreyText10 = Color(0xFF1F1812)
    private val GreyText20 = Color(0xFF3A2F1F)
    private val GreyText80 = Color(0xFFEAE0D0)
    private val GreyText90 = Color(0xFFF5EFDE)

    private val Red40  = Color(0xFFE24B4A)
    private val Red80  = Color(0xFFF09595)

    override val light = lightColorScheme(
        primary              = Yellow700,
        onPrimary            = Color.White,
        primaryContainer     = Yellow400,           // FAB: highlighter yellow
        onPrimaryContainer   = Yellow950,           // very dark amber-brown
        secondary            = Amber600,
        onSecondary          = Color.White,
        secondaryContainer   = Yellow300,
        onSecondaryContainer = Yellow900,
        tertiary             = Blue800,
        onTertiary           = Color.White,
        background           = Color(0xFFFFFEF0),   // faint yellow-tinted white
        onBackground         = GreyText10,
        surface              = Color.White,
        onSurface            = GreyText10,
        surfaceVariant       = GreyText90,
        onSurfaceVariant     = GreyText20,
        outline              = Yellow200,
        error                = Red40,
        onError              = Color.White,
    )

    override val dark = darkColorScheme(
        primary              = Yellow400,
        onPrimary            = Yellow950,
        primaryContainer     = Yellow600,
        onPrimaryContainer   = Yellow50,
        secondary            = Yellow300,
        onSecondary          = Yellow950,
        secondaryContainer   = Yellow700,
        onSecondaryContainer = Yellow50,
        tertiary             = Blue400,
        onTertiary           = Color(0xFF0A1E3A),
        background           = Color(0xFF1F1A0A),   // warm-tinted dark, not neutral
        onBackground         = GreyText80,
        surface              = Color(0xFF2A2410),
        onSurface            = GreyText80,
        surfaceVariant       = GreyText20,
        onSurfaceVariant     = GreyText80,
        outline              = Color(0xFF6E5A20),
        error                = Red80,
        onError              = Color(0xFF690005),
    )

    // Warning slot is also amber, which would normally clash with the
    // primary in this palette. Push warnings toward a more orange tone
    // so toast/snackbar warnings stay visually distinct from chrome.
    private val WarnOrange10 = Color(0xFF2A1500)
    private val WarnOrange40 = Color(0xFF8B4000)
    private val WarnOrange80 = Color(0xFFFF8C42)
    private val WarnOrange90 = Color(0xFFFFD4B5)

    override val warningLight = WarningColors(container = WarnOrange90, onContainer = WarnOrange10)
    override val warningDark  = WarningColors(container = WarnOrange40, onContainer = WarnOrange80)
}
