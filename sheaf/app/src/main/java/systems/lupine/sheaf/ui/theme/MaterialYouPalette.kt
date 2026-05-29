package systems.lupine.sheaf.ui.theme

/**
 * Wallpaper-derived dynamic colour scheme (Android 12+). The actual
 * colours can't live here as constants — they're computed at render
 * time from the system wallpaper. SheafTheme detects this palette by
 * id and calls `dynamicLight/DarkColorScheme(context)` instead of
 * reading `light` / `dark` below.
 *
 * The sentinel `light` / `dark` schemes here are the default Purple
 * palette's schemes; they're what gets returned on pre-S devices
 * (where dynamic colour isn't available) so selecting Material You
 * on an older phone degrades gracefully to the default palette
 * rather than rendering as garbage.
 */
internal object MaterialYouPalette : SheafPalette {
    override val id: String = SheafPalette.MATERIAL_YOU_ID
    override val displayName: String = "Material You"

    override val light = PurplePalette.light
    override val dark = PurplePalette.dark

    override val warningLight = PurplePalette.warningLight
    override val warningDark = PurplePalette.warningDark
}
