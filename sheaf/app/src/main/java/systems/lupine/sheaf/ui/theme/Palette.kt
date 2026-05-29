package systems.lupine.sheaf.ui.theme

import androidx.compose.material3.ColorScheme

/**
 * A named theme palette. Each palette pairs a light and dark
 * [ColorScheme], plus the warning-tone overrides Material 3 doesn't
 * have a slot for. [SheafTheme] picks the per-mode scheme from the
 * active palette based on the user's themeMode preference.
 *
 * The id is stable across releases and persisted in DataStore so the
 * user's selection survives renames in displayName. Adding a new
 * palette = implement [SheafPalette] + register in [SheafPalette.all].
 *
 * Special case: Material You (Android 12+) doesn't have a static
 * scheme — it derives one from the system wallpaper at render time.
 * That palette returns sentinel schemes here; [SheafTheme] detects
 * the [SheafPalette.Companion.MATERIAL_YOU_ID] and calls
 * `dynamicLight/DarkColorScheme(context)` instead.
 */
internal interface SheafPalette {
    val id: String
    val displayName: String
    val light: ColorScheme
    val dark: ColorScheme
    val warningLight: WarningColors
    val warningDark: WarningColors

    companion object {
        /** Sentinel id checked in [SheafTheme] to trigger dynamic-color resolution. */
        const val MATERIAL_YOU_ID: String = "material_you"

        /** The shipped palette catalog, in picker display order. */
        val all: List<SheafPalette> = listOf(
            PurplePalette,
            ClassicPalette,
            OledPalette,
            MaterialYouPalette,
            MintPalette,
            OceanPalette,
            SepiaPalette,
            PridePalette,
            TransPalette,
            NonBinaryPalette,
        )

        /** Fallback if a persisted id no longer matches anything in [all]. */
        val default: SheafPalette = PurplePalette

        fun fromId(id: String?): SheafPalette =
            id?.let { stored -> all.firstOrNull { it.id == stored } } ?: default
    }
}
