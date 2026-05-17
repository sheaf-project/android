package systems.lupine.sheaf.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Body for POST /v1/notifications/redeem. For the unified `mobile_push`
 * destination type [pushSubscription] is rejected and the server uses the
 * session cookie (or Bearer auth, see the redeem 401 backend bug) to bind
 * `redeemed_by_account_id`; the recipient's account fans out to whichever
 * devices they've registered.
 */
@JsonClass(generateAdapter = true)
data class RedeemRequest(
    @Json(name = "activation_code") val activationCode: String,
    @Json(name = "push_subscription") val pushSubscription: PushSubscription? = null,
)

@JsonClass(generateAdapter = true)
data class PushSubscription(
    @Json(name = "endpoint") val endpoint: String,
    @Json(name = "keys") val keys: Map<String, String>,
)

/**
 * Response from POST /v1/notifications/redeem. [managementUrl] is empty
 * for mobile-push channels (no anonymous-capability /manage URL by
 * design — recipients manage in-app via the Receiving screen).
 */
@JsonClass(generateAdapter = true)
data class RedeemResponse(
    @Json(name = "management_url") val managementUrl: String,
    @Json(name = "channel_name") val channelName: String,
    @Json(name = "system_label") val systemLabel: String? = null,
)

/**
 * Account-bound channel from the recipient's perspective; returned by
 * `GET /v1/notifications/receiving`. Lists every channel where the
 * authenticated user is the redeemer.
 */
@JsonClass(generateAdapter = true)
data class ReceivingChannelView(
    @Json(name = "channel_id") val channelId: String,
    @Json(name = "channel_name") val channelName: String,
    @Json(name = "system_label") val systemLabel: String? = null,
    @Json(name = "destination_type") val destinationType: String,
    @Json(name = "destination_state") val destinationState: String,
    @Json(name = "redeemed_at") val redeemedAt: String? = null,
    @Json(name = "last_delivered_at") val lastDeliveredAt: String? = null,
    // True when the sender has paused the channel server-side. Distinct
    // from destinationState == "unsubscribed", which means the recipient
    // chose to leave. Defaults to false so older backend responses parse
    // the same as before.
    @Json(name = "paused_by_sender") val pausedBySender: Boolean = false,
)
