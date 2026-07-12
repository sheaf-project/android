package systems.lupine.sheaf.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.ZoneId

/**
 * Display-timezone preference, two tiers mirroring web's useTimezone:
 *
 *  - Account default (`System.timezone`): syncs across the account's devices;
 *    null = "automatic" (each device uses its own clock). Set via PATCH
 *    /v1/systems/me and cached locally in [PreferencesRepository.accountTimezone].
 *  - Device override ([PreferencesRepository.timezoneOverride]): shadows the
 *    account value on this device only. Absent = follow the account, [TZ_AUTO]
 *    = pin this device to its own clock even if the account has a fixed zone,
 *    or an IANA zone name.
 *
 * Resolution: device override > account default > device-local.
 */

// Device-override sentinel: "follow this device's own clock", distinct from
// "follow the account default" (which is the absence of any override).
const val TZ_AUTO = "auto"

/**
 * The effective [ZoneId] to render timestamps in, resolving the two tiers.
 * Falls back to the device's own zone for "automatic" and for any stored zone
 * that no longer parses (a stale IANA name after a tzdata change).
 */
fun resolveDisplayZoneId(accountTimezone: String?, deviceOverride: String?): ZoneId {
    val name = when {
        deviceOverride == TZ_AUTO -> null
        !deviceOverride.isNullOrBlank() -> deviceOverride
        else -> accountTimezone
    }
    return name?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
}

/** True if [id] is a zone this device can resolve. */
fun isValidTimeZoneId(id: String): Boolean =
    runCatching { ZoneId.of(id) }.isSuccess

data class CommonZone(val label: String, val zone: String)

// Friendly shortcuts for the most-reached zones, each mapped to a canonical
// city zone so DST is observed correctly where the region uses it (Eastern
// follows EST/EDT, UK follows GMT/BST, ...). Mirrors web's timezone-select.
val COMMON_ZONES: List<CommonZone> = listOf(
    CommonZone("UTC", "UTC"),
    CommonZone("Eastern Time (US & Canada)", "America/New_York"),
    CommonZone("Central Time (US & Canada)", "America/Chicago"),
    CommonZone("Mountain Time (US & Canada)", "America/Denver"),
    CommonZone("Pacific Time (US & Canada)", "America/Los_Angeles"),
    CommonZone("Alaska Time", "America/Anchorage"),
    CommonZone("Hawaii Time", "Pacific/Honolulu"),
    CommonZone("UK / Ireland (London)", "Europe/London"),
    CommonZone("Central European (Paris, Berlin)", "Europe/Paris"),
    CommonZone("Eastern European (Athens, Helsinki)", "Europe/Athens"),
    CommonZone("India (Kolkata)", "Asia/Kolkata"),
    CommonZone("China (Shanghai)", "Asia/Shanghai"),
    CommonZone("Japan (Tokyo)", "Asia/Tokyo"),
    CommonZone("Australia Eastern (Sydney)", "Australia/Sydney"),
)

private val COMMON_ZONE_IDS: Set<String> = COMMON_ZONES.mapTo(HashSet()) { it.zone }

/** The friendly label for a zone id (e.g. "Eastern Time (US & Canada)"), or the
 *  raw id if it isn't one of the [COMMON_ZONES]. */
fun friendlyZoneLabel(zoneId: String): String =
    COMMON_ZONES.firstOrNull { it.zone == zoneId }?.label ?: zoneId

/** Sorted IANA zone ids for the picker's "All time zones" section, with the
 *  [COMMON_ZONES] removed so each zone appears exactly once (its friendly label
 *  is shown in the Common group instead). Unlike web we don't need to add
 *  fixed-offset extras: Java's tz database already includes Etc/GMT*, EST, etc. */
fun allTimeZoneIds(): List<String> =
    ZoneId.getAvailableZoneIds().asSequence().filterNot { it in COMMON_ZONE_IDS }.sorted().toList()

/**
 * The resolved display zone, provided app-wide at the root so any composable
 * can render timestamps in it without threading it through. Defaults to the
 * device's own zone (which is also what "automatic" collapses to).
 */
val LocalDisplayTimeZone = staticCompositionLocalOf { ZoneId.systemDefault() }
