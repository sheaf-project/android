package systems.lupine.sheaf.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Bi-flag-inspired palette. Iconic hues: magenta, purple, blue (the
 * flag's three stripes are 60% pink, 20% purple, 20% blue; pink reads
 * as the dominant identity colour).
 *
 * Same M3 slot trick as Trans/Pride/NonBinary: iconic flag colours
 * land in primaryContainer / secondaryContainer (FAB and nav-indicator
 * actually surface them) while deeper/lighter variants drive the
 * primary/secondary slots that need text-on-tonal contrast. The middle
 * purple stripe — the "overlap" colour that gives the flag its
 * symbolism — sits in the tertiary slot.
 */
internal object BiPalette : SheafPalette {
    override val id: String = "bi"
    override val displayName: String = "Bi"

    // Canonical bi flag hues.
    private val BiPink       = Color(0xFFD60270)
    private val BiPinkLifted = Color(0xFFEF4D8E)   // adapted for tonal text in dark
    private val BiPinkDeeper = Color(0xFFA8025A)   // adapted for text-on-white in light
    private val BiBlue       = Color(0xFF0038A8)
    private val BiBlueLifted = Color(0xFF4A6FD4)   // adapted for nav-indicator visibility
    private val BiPurple     = Color(0xFF9B4F96)   // overlap stripe (tertiary)
    private val BiPurpleLt   = Color(0xFFC28BBE)

    private val GreyText10 = Color(0xFF1F0A18)
    private val GreyText20 = Color(0xFF3A1F30)
    private val GreyText80 = Color(0xFFEAD6E0)
    private val GreyText90 = Color(0xFFF5E8EF)

    private val Red40  = Color(0xFFE24B4A)
    private val Red80  = Color(0xFFF09595)

    override val light = lightColorScheme(
        primary              = BiPinkDeeper,
        onPrimary            = Color.White,
        primaryContainer     = BiPink,              // FAB: flag magenta
        onPrimaryContainer   = Color.White,
        secondary            = BiBlue,
        onSecondary          = Color.White,
        secondaryContainer   = BiBlueLifted,        // nav indicator: clearly flag blue
        onSecondaryContainer = Color.White,
        tertiary             = BiPurple,            // overlap stripe
        onTertiary           = Color.White,
        background           = Color(0xFFFFF8FC),   // faint pink-tinted white
        onBackground         = GreyText10,
        surface              = Color.White,
        onSurface            = GreyText10,
        surfaceVariant       = GreyText90,
        onSurfaceVariant     = GreyText20,
        outline              = Color(0xFFE9C0D2),
        error                = Red40,
        onError              = Color.White,
    )

    override val dark = darkColorScheme(
        primary              = BiPinkLifted,        // recognisable pink for tonal text
        onPrimary            = Color(0xFF3D0418),
        primaryContainer     = BiPink,              // FAB: full flag magenta
        onPrimaryContainer   = Color.White,
        secondary            = BiBlueLifted,
        onSecondary          = Color.White,
        secondaryContainer   = BiBlue,              // nav indicator: full flag blue
        onSecondaryContainer = Color.White,
        tertiary             = BiPurpleLt,
        onTertiary           = Color(0xFF3D1A38),
        background           = Color(0xFF1A0F1A),   // deep plum bridging pink/purple/blue
        onBackground         = GreyText80,
        surface              = Color(0xFF261426),
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
}
