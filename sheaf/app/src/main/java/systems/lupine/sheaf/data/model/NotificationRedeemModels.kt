package systems.lupine.sheaf.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Body for POST /v1/notifications/redeem. For mobile push channels
 * (FCM / APNS_DEV / APNS_PROD) [pushSubscription] is rejected and the
 * server uses the session cookie to bind `redeemed_by_account_id`.
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
