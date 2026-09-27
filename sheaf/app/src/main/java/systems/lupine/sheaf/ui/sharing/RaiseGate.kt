package systems.lupine.sheaf.ui.sharing

import systems.lupine.sheaf.data.api.SheafApiService

/**
 * The re-auth posture for exposing actions, shared by every editor that can
 * raise a ceiling.
 *
 * Ceilings are edited where the data is edited (a member in the member editor,
 * an edge in the relationship editor), so the gate has to travel to each of
 * them rather than living on the sharing screen. `stepUpNeeded` mirrors the
 * server's own predicate so the credential sheet can open before the round
 * trip; the server's 403 still overrules it when the mirror drifts.
 */
data class RaiseGate(
    val authTier: String = "none",
    val totpEnabled: Boolean = false,
    val armed: Boolean = false,
    val graceDays: Int = 0,
    val publishingAvailable: Boolean = false,
) {
    // At tier `none` the re-auth verifies nothing, so there is no point
    // interrupting for it.
    val stepUpNeeded: Boolean get() = armed && authTier != "none"
}

suspend fun SheafApiService.loadRaiseGate(): RaiseGate {
    val safety = runCatching { getSystemSafety() }.getOrNull()
    val me = runCatching { getMe() }.getOrNull()
    return RaiseGate(
        authTier = safety?.settings?.authTier ?: "none",
        totpEnabled = me?.totpEnabled == true,
        armed = safety?.settings?.appliesToProfileVisibility ?: false,
        graceDays = safety?.settings?.gracePeriodDays ?: 0,
        publishingAvailable = me?.publicProfilesEnabled ?: false,
    )
}

/** A raise is only gated when it actually widens who can see something. */
fun isRaiseToPublic(current: String?, next: String): Boolean =
    next == "public" && current != "public"

/**
 * Whether Public may be chosen here at all.
 *
 * An instance with public profiles switched off refuses a raise to public with
 * a 403, so offering it is a guaranteed dead end. Two cases keep it offered
 * anyway:
 *
 * - [savedValue] is already `public`. The server refuses only an actual raise,
 *   and this is how the stored value still displays and, above all, how
 *   somebody LOWERS it. Nothing may stand between a user and reducing their
 *   own exposure.
 * - [gated] is false. Relationship edges between groups are never queried by
 *   the public projection, so the server stores public there as asked;
 *   disabling it would invent a restriction that does not exist.
 */
fun RaiseGate.offersPublic(savedValue: String?, gated: Boolean = true): Boolean =
    publishingAvailable || !gated || savedValue == "public"

/**
 * The one line shown where Public is offered but unavailable.
 *
 * Opens with the same clause as the sharing screen's card ("Public profiles
 * are turned off on this instance"), so somebody who meets this state on two
 * screens meets one explanation of it rather than two that almost agree.
 */
const val PUBLISHING_OFF_NOTE: String =
    "Public profiles are turned off on this instance, so nothing new can be set to " +
        "Public. Anything already public is kept, and you can still lower it."
