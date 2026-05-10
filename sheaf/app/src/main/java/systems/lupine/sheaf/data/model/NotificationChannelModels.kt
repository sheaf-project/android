package systems.lupine.sheaf.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ── Watch tokens ─────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class WatchTokenCreate(
    @Json(name = "label") val label: String? = null,
)

@JsonClass(generateAdapter = true)
data class WatchTokenRead(
    @Json(name = "id") val id: String,
    @Json(name = "system_id") val systemId: String,
    @Json(name = "label") val label: String? = null,
    @Json(name = "revoked_at") val revokedAt: String? = null,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    @Json(name = "channel_count") val channelCount: Int = 0,
)

// ── Notification channels (owner-side) ───────────────────────────────────────

@JsonClass(generateAdapter = true)
data class NotificationChannelCreate(
    @Json(name = "name") val name: String,
    @Json(name = "destination_type") val destinationType: String,
    @Json(name = "destination_config") val destinationConfig: Map<String, Any> = emptyMap(),
    @Json(name = "trigger_on_start") val triggerOnStart: Boolean = true,
    @Json(name = "trigger_on_stop") val triggerOnStop: Boolean = false,
    @Json(name = "trigger_on_cofront_change") val triggerOnCofrontChange: Boolean = false,
    @Json(name = "payload_sensitivity") val payloadSensitivity: String = "full",
)

@JsonClass(generateAdapter = true)
data class NotificationChannelRead(
    @Json(name = "id") val id: String,
    @Json(name = "watch_token_id") val watchTokenId: String,
    @Json(name = "name") val name: String,
    @Json(name = "destination_type") val destinationType: String,
    @Json(name = "destination_state") val destinationState: String,
    @Json(name = "trigger_on_start") val triggerOnStart: Boolean = false,
    @Json(name = "trigger_on_stop") val triggerOnStop: Boolean = false,
    @Json(name = "trigger_on_cofront_change") val triggerOnCofrontChange: Boolean = false,
    @Json(name = "payload_sensitivity") val payloadSensitivity: String = "full",
    @Json(name = "activation_code_expires_at") val activationCodeExpiresAt: String? = null,
    @Json(name = "redeemed_at") val redeemedAt: String? = null,
    @Json(name = "last_delivered_at") val lastDeliveredAt: String? = null,
    @Json(name = "created_at") val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class NotificationChannelCreateResponse(
    @Json(name = "channel") val channel: NotificationChannelRead,
    @Json(name = "activation_url") val activationUrl: String? = null,
    @Json(name = "activation_expires_at") val activationExpiresAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class ReissueActivationResponse(
    @Json(name = "activation_url") val activationUrl: String,
    @Json(name = "activation_expires_at") val activationExpiresAt: String,
)
