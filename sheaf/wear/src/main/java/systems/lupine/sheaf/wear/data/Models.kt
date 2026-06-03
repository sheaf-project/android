package systems.lupine.sheaf.wear.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WearMember(
    val id: String,
    val name: String,
    @Json(name = "display_name") val displayName: String?,
    val description: String?,
    val pronouns: String?,
    @Json(name = "avatar_url") val avatarUrl: String?,
    val color: String?,
    val emoji: String? = null,
) {
    val displayNameOrName: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: name

    val initials: String
        get() = displayNameOrName
            .split("\\s+".toRegex())
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }
}

@JsonClass(generateAdapter = true)
data class WearGroup(
    val id: String,
    val name: String,
    val description: String?,
    val color: String?,
)

@JsonClass(generateAdapter = true)
data class WearFront(
    val id: String,
    @Json(name = "member_ids") val memberIds: List<String>,
    @Json(name = "started_at") val startedAt: String?,
    // Null when the front is still ongoing; ISO timestamp once the entry
    // has been ended. Used by the history viewer to mark live entries
    // with a "+" suffix.
    @Json(name = "ended_at") val endedAt: String? = null,
    // Optional per-entry custom status (the "comment" / note on a front).
    // Phone surfaces it on the current-fronts card and history rows; wear
    // currently consumes it for the front-detail surface only — small
    // screen, limited room to fit it everywhere.
    @Json(name = "custom_status") val customStatus: String? = null,
    // Per-member chain-aware fronting-since, populated when the system has
    // coalesce_contiguous_fronts enabled. Falls back to startedAt when absent.
    @Json(name = "member_since") val memberSince: Map<String, String> = emptyMap(),
    // Member ids whose member_since hit the server-side walk-back depth cap.
    @Json(name = "member_since_capped") val memberSinceCapped: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
internal data class TokenPair(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String,
)

// Request bodies. Top-level (not nested inside WearApiClient) so Moshi codegen
// can generate adapters for them — KSP won't generate adapters for private
// nested classes.
@JsonClass(generateAdapter = true)
internal data class LoginBody(val email: String, val password: String)

@JsonClass(generateAdapter = true)
internal data class MemberCreateBody(
    val name: String,
    @Json(name = "display_name") val displayName: String?,
    val pronouns: String?,
)

@JsonClass(generateAdapter = true)
internal data class GroupMembersBody(
    @Json(name = "member_ids") val memberIds: List<String>,
)

class WearApiException(val code: Int, body: String? = null) : Exception(
    if (body != null) "API error: $code — $body" else "API error: $code"
)
