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
