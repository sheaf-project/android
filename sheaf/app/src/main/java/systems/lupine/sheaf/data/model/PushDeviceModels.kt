package systems.lupine.sheaf.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Body for POST /v1/devices/push. The server is idempotent on
 * (account_id, platform, token); if (account_id, install_id) already
 * has a different token, the existing row is updated in place
 * (token rotation case).
 */
@JsonClass(generateAdapter = true)
data class PushDeviceRegistration(
    @Json(name = "platform") val platform: String,
    @Json(name = "token") val token: String,
    @Json(name = "install_id") val installId: String? = null,
    @Json(name = "app_version") val appVersion: String? = null,
)

@JsonClass(generateAdapter = true)
data class PushDeviceUnregister(
    @Json(name = "token") val token: String,
)

@JsonClass(generateAdapter = true)
data class PushDeviceListEntry(
    @Json(name = "id") val id: String,
    @Json(name = "platform") val platform: String,
    @Json(name = "app_version") val appVersion: String?,
    @Json(name = "install_id") val installId: String?,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "last_seen_at") val lastSeenAt: String,
)
