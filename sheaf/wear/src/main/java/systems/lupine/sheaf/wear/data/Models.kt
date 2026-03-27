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
)

internal data class TokenPair(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String,
)

class WearApiException(val code: Int, body: String? = null) : Exception(
    if (body != null) "API error: $code — $body" else "API error: $code"
)
