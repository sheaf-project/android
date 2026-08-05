package systems.lupine.sheaf.data.api

import retrofit2.HttpException
import systems.lupine.sheaf.data.model.FrontRead
import systems.lupine.sheaf.data.model.FrontReplace
import systems.lupine.sheaf.data.model.FrontUpdate

/**
 * Change which members are in one open front, leaving every other open front
 * alone.
 *
 * Prefers `POST /v1/fronts/{id}/replace`, which ends the old front and opens
 * its replacement in one transaction: each member's stint stays its own history
 * entry, and the whole change reads as one notification.
 *
 * Falls back to the in-place `PATCH` on a server that predates that endpoint.
 * Sheaf is multi-instance and self-hosters upgrade on their own schedule, so a
 * client that hard-required a brand-new endpoint would break add-to-front and
 * remove-from-front outright on any instance that hadn't caught up. The
 * fallback is what those servers always did, history split and all.
 *
 * A 404 is ambiguous (missing endpoint, or a front that genuinely isn't there),
 * and that is fine: if the front is really gone the PATCH fails the same way,
 * costing one extra request on a path that was already failing.
 */
suspend fun SheafApiService.replaceFrontMembers(
    frontId: String,
    memberIds: List<String>,
    startedAt: String? = null,
): FrontRead = try {
    replaceFront(frontId, FrontReplace(memberIds = memberIds, startedAt = startedAt))
} catch (e: HttpException) {
    if (e.code() == 404 || e.code() == 405) {
        updateFront(frontId, FrontUpdate(memberIds = memberIds))
    } else {
        throw e
    }
}
