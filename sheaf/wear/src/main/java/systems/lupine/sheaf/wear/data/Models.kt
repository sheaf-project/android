package systems.lupine.sheaf.wear.data

import com.squareup.moshi.Json

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

data class WearGroup(
    val id: String,
    val name: String,
    val description: String?,
    val color: String?,
)

data class WearFront(
    val id: String,
    @Json(name = "member_ids") val memberIds: List<String>,
    @Json(name = "started_at") val startedAt: String?,
    // Per-member chain-aware fronting-since, populated when the system has
    // coalesce_contiguous_fronts enabled. Falls back to startedAt when absent.
    @Json(name = "member_since") val memberSince: Map<String, String> = emptyMap(),
    // Member ids whose member_since hit the server-side walk-back depth cap.
    @Json(name = "member_since_capped") val memberSinceCapped: List<String> = emptyList(),
)

internal data class TokenPair(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String,
)

class WearApiException(val code: Int, body: String? = null) : Exception(
    if (body != null) "API error: $code — $body" else "API error: $code"
)
