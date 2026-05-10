package systems.lupine.sheaf.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Request body for POST /v1/reminders. The shape mirrors the backend's
 * ReminderCreate / ReminderBase: many fields are conditionally relevant
 * based on `triggerType` (and `scheduleKind` within scheduled triggers).
 *
 * Use the same payload for partial updates via PATCH; the server's
 * ReminderUpdate accepts the same field set with all-optional semantics,
 * so Moshi's `@JsonClass(generateAdapter = true)` with nullable defaults
 * gives us partial-update behaviour for free.
 */
@JsonClass(generateAdapter = true)
data class ReminderWrite(
    @Json(name = "name") val name: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "body") val body: String? = null,
    @Json(name = "enabled") val enabled: Boolean? = null,
    @Json(name = "channel_id") val channelId: String? = null,

    @Json(name = "trigger_type") val triggerType: String? = null,
    @Json(name = "trigger_member_id") val triggerMemberId: String? = null,
    @Json(name = "trigger_event") val triggerEvent: String? = null,
    @Json(name = "delay_seconds") val delaySeconds: Int? = null,

    @Json(name = "schedule_kind") val scheduleKind: String? = null,
    @Json(name = "schedule_time") val scheduleTime: String? = null,
    @Json(name = "schedule_dow_mask") val scheduleDowMask: Int? = null,
    @Json(name = "schedule_dom") val scheduleDom: Int? = null,
    @Json(name = "schedule_tz") val scheduleTz: String? = null,
    @Json(name = "cron_expression") val cronExpression: String? = null,

    @Json(name = "scope") val scope: String? = null,
    @Json(name = "scope_member_ids") val scopeMemberIds: List<String>? = null,
    @Json(name = "digest_when_absent") val digestWhenAbsent: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class ReminderRead(
    @Json(name = "id") val id: String,
    @Json(name = "system_id") val systemId: String,
    @Json(name = "channel_id") val channelId: String,

    @Json(name = "name") val name: String,
    @Json(name = "title") val title: String,
    @Json(name = "body") val body: String? = null,
    @Json(name = "enabled") val enabled: Boolean,
    @Json(name = "trigger_type") val triggerType: String,

    @Json(name = "trigger_member_id") val triggerMemberId: String? = null,
    @Json(name = "trigger_event") val triggerEvent: String? = null,
    @Json(name = "delay_seconds") val delaySeconds: Int? = null,

    @Json(name = "schedule_kind") val scheduleKind: String? = null,
    @Json(name = "schedule_time") val scheduleTime: String? = null,
    @Json(name = "schedule_dow_mask") val scheduleDowMask: Int? = null,
    @Json(name = "schedule_dom") val scheduleDom: Int? = null,
    @Json(name = "schedule_tz") val scheduleTz: String? = null,
    @Json(name = "cron_expression") val cronExpression: String? = null,

    @Json(name = "scope") val scope: String = "system",
    @Json(name = "scope_member_ids") val scopeMemberIds: List<String> = emptyList(),
    @Json(name = "digest_when_absent") val digestWhenAbsent: Boolean = true,

    @Json(name = "last_fired_at") val lastFiredAt: String? = null,
    @Json(name = "pending_count") val pendingCount: Int = 0,
    @Json(name = "next_fire_at") val nextFireAt: String? = null,
)
