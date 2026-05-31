package systems.lupine.sheaf.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Pan-flag-inspired palette. Iconic hues: magenta-pink, yellow, cyan
 * (the flag's three equal stripes).
 *
 * Yellow is the contrast hazard here (same as Goldenrod): pure yellow
 * on white fails WCAG and the iconic flag yellow is *very* light. So
 * pink takes primary, cyan takes secondary, yellow lives in tertiary
 * (light mode uses a deeper amber variant for text contrast; dark
 * mode gets to use the iconic flag yellow directly because dark
 * surfaces give it room to read).
 *
 * Same M3 slot trick as the other flag palettes — iconic colours in
 * the *Container slots so FAB and nav-indicator surface them.
 */
internal object PanPalette : SheafPalette {
    override val id: String = "pan"
    override val displayName: String = "Pan"

    // Canonical pan flag hues.
    private val PanPink        = Color(0xFFFF1B8D)
    private val PanPinkLifted  = Color(0xFFFF6BB0)   // adapted for tonal text in dark
    private val PanPinkDeeper  = Color(0xFFC8005F)   // adapted for text-on-white in light
    private val PanYellow      = Color(0xFFFFD800)
    private val PanYellowDeep  = Color(0xFFB59800)   // adapted: text-on-white safe variant
    private val PanCyan        = Color(0xFF1BB3FF)
    private val PanCyanDeeper  = Color(0xFF1685C0)
    private val PanCyanLifted  = Color(0xFF5DCAFF)

    private val GreyText10 = Color(0xFF1F0A18)
    private val GreyText20 = Color(0xFF3A1F2E)
    private val GreyText80 = Color(0xFFEAD8E0)
    private val GreyText90 = Color(0xFFF5E8EF)

    private val Red40  = Color(0xFFE24B4A)
    private val Red80  = Color(0xFFF09595)

    override val light = lightColorScheme(
        primary              = PanPinkDeeper,
        onPrimary            = Color.White,
        primaryContainer     = PanPink,             // FAB: flag pink
        onPrimaryContainer   = Color.White,
        secondary            = PanCyanDeeper,
        onSecondary          = Color.White,
        secondaryContainer   = PanCyan,             // nav indicator: flag cyan
        onSecondaryContainer = Color(0xFF002F45),
        tertiary             = PanYellowDeep,
        onTertiary           = Color.White,
        background           = Color(0xFFFFFAFC),   // very faint pink-tinted white
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
        primary              = PanPinkLifted,
        onPrimary            = Color(0xFF3D0418),
        primaryContainer     = PanPink,             // FAB: full flag pink
        onPrimaryContainer   = Color.White,
        secondary            = PanCyanLifted,
        onSecondary          = Color(0xFF002F45),
        secondaryContainer   = PanCyan,             // nav indicator: full flag cyan
        onSecondaryContainer = Color(0xFF002F45),
        tertiary             = PanYellow,           // dark mode finally lets flag yellow read
        onTertiary           = Color(0xFF2E2200),
        background           = Color(0xFF1A0E16),   // warm-tinted dark
        onBackground         = GreyText80,
        surface              = Color(0xFF261424),
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
