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

// ── Layer-2 / Layer-3 rule specs ─────────────────────────────────────────────
//
// rule: "include" | "exclude"
// include_private (groups only): "inherit" | "yes" | "no"
//
// Kept as plain strings (rather than enums) to match the backend's pydantic
// Literal types verbatim — Moshi serialises them straight through and the
// UI maps them with when-expressions.

@JsonClass(generateAdapter = true)
data class GroupRuleSpec(
    @Json(name = "group_id") val groupId: String,
    @Json(name = "rule") val rule: String,
    @Json(name = "include_private") val includePrivate: String = "inherit",
)

@JsonClass(generateAdapter = true)
data class MemberRuleSpec(
    @Json(name = "member_id") val memberId: String,
    @Json(name = "rule") val rule: String,
)

@JsonClass(generateAdapter = true)
data class QuietHoursSpec(
    @Json(name = "start") val start: String, // "HH:MM"
    @Json(name = "end") val end: String,
    @Json(name = "tz") val tz: String = "UTC", // IANA name
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
    // True when destination_state == 'disabled' is because the *owner*
    // paused (not because the recipient unsubscribed). Mirrors web's
    // ChannelRead.paused_by_sender; defaults preserve compatibility with
    // older responses that didn't include the field.
    @Json(name = "paused_by_sender") val pausedBySender: Boolean = false,
    @Json(name = "destination_config") val destinationConfig: Map<String, Any> = emptyMap(),
    @Json(name = "base_all_members") val baseAllMembers: Boolean = false,
    @Json(name = "base_include_private") val baseIncludePrivate: Boolean = false,
    @Json(name = "trigger_on_start") val triggerOnStart: Boolean = false,
    @Json(name = "trigger_on_stop") val triggerOnStop: Boolean = false,
    @Json(name = "trigger_on_cofront_change") val triggerOnCofrontChange: Boolean = false,
    @Json(name = "cofront_redaction") val cofrontRedaction: String = "count",
    @Json(name = "payload_sensitivity") val payloadSensitivity: String = "full",
    @Json(name = "debounce_seconds") val debounceSeconds: Int = 30,
    @Json(name = "aggregation_window_seconds") val aggregationWindowSeconds: Int = 0,
    @Json(name = "quiet_hours") val quietHours: QuietHoursSpec? = null,
    @Json(name = "group_rules") val groupRules: List<GroupRuleSpec> = emptyList(),
    @Json(name = "member_rules") val memberRules: List<MemberRuleSpec> = emptyList(),
    @Json(name = "activation_code_expires_at") val activationCodeExpiresAt: String? = null,
    @Json(name = "redeemed_at") val redeemedAt: String? = null,
    @Json(name = "last_delivered_at") val lastDeliveredAt: String? = null,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "pending_delete_at") val pendingDeleteAt: String? = null,
)

/**
 * PATCH /v1/channels/{channelId}. Every field nullable so the JSON wire-
 * representation only carries the keys the caller actually set — backend
 * differentiates "absent" (don't touch) from "null" (clear, where allowed)
 * via Pydantic's model_fields_set.
 *
 * Note: backend rejects explicit null on most fields (only quiet_hours
 * accepts null = clear). The UI keeps the ones it isn't editing out of
 * the request entirely by leaving them as `null` here, which Moshi omits
 * by default. Whenever the user toggles a control, the corresponding
 * field is set to a non-null value.
 */
@JsonClass(generateAdapter = true)
data class NotificationChannelUpdate(
    @Json(name = "name") val name: String? = null,
    @Json(name = "destination_config") val destinationConfig: Map<String, Any>? = null,
    @Json(name = "webhook_secret") val webhookSecret: String? = null,
    @Json(name = "base_all_members") val baseAllMembers: Boolean? = null,
    @Json(name = "base_include_private") val baseIncludePrivate: Boolean? = null,
    @Json(name = "trigger_on_start") val triggerOnStart: Boolean? = null,
    @Json(name = "trigger_on_stop") val triggerOnStop: Boolean? = null,
    @Json(name = "trigger_on_cofront_change") val triggerOnCofrontChange: Boolean? = null,
    @Json(name = "cofront_redaction") val cofrontRedaction: String? = null,
    @Json(name = "payload_sensitivity") val payloadSensitivity: String? = null,
    @Json(name = "debounce_seconds") val debounceSeconds: Int? = null,
    @Json(name = "aggregation_window_seconds") val aggregationWindowSeconds: Int? = null,
    @Json(name = "quiet_hours") val quietHours: QuietHoursSpec? = null,
    @Json(name = "group_rules") val groupRules: List<GroupRuleSpec>? = null,
    @Json(name = "member_rules") val memberRules: List<MemberRuleSpec>? = null,
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

@JsonClass(generateAdapter = true)
data class TestDispatchResponse(
    @Json(name = "delivered") val delivered: Boolean,
    @Json(name = "error") val error: String? = null,
)
