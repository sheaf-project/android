package systems.lupine.sheaf.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

/**
 * Aggregated fronting statistics for the user's system across a window.
 * Mirrors `sheaf.schemas.analytics.FrontingAnalytics` on the backend.
 *
 * `windowSeconds` is the full requested span in seconds; it lets the UI
 * normalise totals against the window for chart axes etc. Member-level
 * percentage is precomputed server-side on [MemberFrontingStats.percentOfWindow].
 */
@JsonClass(generateAdapter = true)
data class FrontingAnalytics(
    @Json(name = "since") val since: Date,
    @Json(name = "until") val until: Date,
    @Json(name = "tz") val tz: String,
    @Json(name = "window_seconds") val windowSeconds: Long,
    @Json(name = "members") val members: List<MemberFrontingStats>,
)

/**
 * Per-member fronting summary inside a [FrontingAnalytics] window.
 * Co-fronting double-counts: if Alice and Bob co-front for an hour,
 * both get +3600 here. Matches "how much did Alice front this month".
 *
 * `hourOfDaySeconds` is a 24-element list indexed 0..23 in the timezone
 * the endpoint was called with (the client passes the device tz).
 * Members who didn't front in the window come back with all-zero values
 * so the UI can list them without special-casing.
 */
@JsonClass(generateAdapter = true)
data class MemberFrontingStats(
    @Json(name = "member_id") val memberId: String,
    @Json(name = "is_custom_front") val isCustomFront: Boolean = false,
    @Json(name = "total_seconds") val totalSeconds: Long = 0,
    // Already a percent value (0..N), not a fraction. e.g. 52.93 means
    // "52.9% of window". Can exceed 100 because co-fronting double-counts:
    // if Alice and Bob front together for the entire window, both come
    // back at 100% and the sum across members is 200%.
    @Json(name = "percent_of_window") val percentOfWindow: Double = 0.0,
    @Json(name = "session_count") val sessionCount: Int = 0,
    @Json(name = "longest_session_seconds") val longestSessionSeconds: Long = 0,
    @Json(name = "hour_of_day_seconds") val hourOfDaySeconds: List<Long> = List(24) { 0L },
)
