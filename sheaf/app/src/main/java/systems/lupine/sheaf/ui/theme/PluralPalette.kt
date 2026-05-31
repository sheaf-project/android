package systems.lupine.sheaf.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Plural-flag-inspired palette. Five-stripe flag: dark plum, deep
 * purple, lavender, mint, cream — a warm-to-cool-to-warm gradient
 * where the deep plum and cream form the bookends and the cool
 * purples / mint sit in the middle.
 *
 * Slot mapping:
 *  - The deep flag purple drives the primary text-on-tonal slot in
 *    light mode; the lavender takes primary in dark mode where it
 *    reads against the plum background.
 *  - FAB and nav-indicator (the *Container slots) carry the most
 *    recognisable mid-flag tones (lavender and mint) so the chrome
 *    reads as plural-flag rather than "another purple palette".
 *  - The cream stripe doesn't survive a white background, so the
 *    light mode pulls in a deeper cream-gold variant for tertiary
 *    contrast and lets the actual cream shine in dark mode tertiary.
 *  - The dark plum stripe becomes the dark-mode background, locking
 *    the flag's darkest tone into the most visible chrome surface.
 *
 * This is the on-brand palette for the app's audience so contrast
 * trade-offs lean toward "flag colours readable" over "novel design".
 */
internal object PluralPalette : SheafPalette {
    override val id: String = "plural"
    override val displayName: String = "Plural"

    // Canonical plural flag hues, dark-to-light.
    private val PluralDarkPlum = Color(0xFF2E0525)
    private val PluralPurple   = Color(0xFF543576)
    private val PluralLavender = Color(0xFF7674C2)
    private val PluralMint     = Color(0xFF89C8B0)
    private val PluralCream    = Color(0xFFF4ECBC)

    // Synthesised tonal variants for the slots where the flag stripe
    // itself doesn't meet WCAG contrast in the role.
    private val PluralMintDeeper  = Color(0xFF4A8A73)  // text-on-white safe mint
    private val PluralCreamDeeper = Color(0xFFB5A040)  // text-on-white safe cream-gold
    private val PluralPurpleDk    = Color(0xFF3A1F5A)  // deeper purple for surface tint

    private val GreyText10 = Color(0xFF1A0E1F)
    private val GreyText20 = Color(0xFF2E1F3E)
    private val GreyText80 = Color(0xFFE5DDEE)
    private val GreyText90 = Color(0xFFF1EAF7)

    private val Red40  = Color(0xFFE24B4A)
    private val Red80  = Color(0xFFF09595)

    override val light = lightColorScheme(
        primary              = PluralPurple,
        onPrimary            = Color.White,
        primaryContainer     = PluralLavender,         // FAB: mid-flag lavender
        onPrimaryContainer   = Color.White,
        secondary            = PluralMintDeeper,
        onSecondary          = Color.White,
        secondaryContainer   = PluralMint,             // nav indicator: flag mint
        onSecondaryContainer = Color(0xFF0F3D2E),
        tertiary             = PluralCreamDeeper,
        onTertiary           = Color.White,
        background           = Color(0xFFFFFDF5),      // very faint cream tint
        onBackground         = PluralDarkPlum,
        surface              = Color.White,
        onSurface            = GreyText10,
        surfaceVariant       = Color(0xFFF1E8F0),
        onSurfaceVariant     = GreyText20,
        outline              = Color(0xFFC5C2E0),
        error                = Red40,
        onError              = Color.White,
    )

    override val dark = darkColorScheme(
        primary              = PluralLavender,         // recognisable flag lavender
        onPrimary            = PluralDarkPlum,
        primaryContainer     = PluralPurple,           // FAB: deep flag purple
        onPrimaryContainer   = Color.White,
        secondary            = PluralMint,             // recognisable flag mint
        onSecondary          = Color(0xFF0F3D2E),
        secondaryContainer   = PluralMintDeeper,       // nav indicator: deeper mint
        onSecondaryContainer = Color.White,
        tertiary             = PluralCream,            // dark mode lets flag cream shine
        onTertiary           = PluralDarkPlum,
        background           = PluralDarkPlum,         // flag's darkest stripe as chrome
        onBackground         = Color(0xFFF0D8E0),
        surface              = Color(0xFF3A1234),
        onSurface            = Color(0xFFF0D8E0),
        surfaceVariant       = PluralPurpleDk,
        onSurfaceVariant     = Color(0xFFE0C8D8),
        outline              = Color(0xFF6E4868),
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
