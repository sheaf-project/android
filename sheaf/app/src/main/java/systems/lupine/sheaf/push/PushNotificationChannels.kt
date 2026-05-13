package systems.lupine.sheaf.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import systems.lupine.sheaf.data.model.ReceivingChannelView
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android-platform notification channels for incoming push messages.
 * Two layers:
 *
 *  1. Fixed broad-category channels (front_change / reminders / system)
 *     created once at app startup. Used as fallbacks when the server
 *     payload doesn't tell us which Sheaf channel a delivery belongs
 *     to, and for messages whose Sheaf channel we don't have a local
 *     Android channel for yet.
 *
 *  2. Per-subscription channels, created on demand from the user's
 *     /v1/notifications/receiving list (one Android channel per Sheaf
 *     channel the account redeemed). Lets the user mute / change
 *     importance / disable badge dot for "Alice's fronts" without
 *     affecting "Bob's fronts".
 *
 * Channel ids mirror the server's vocabulary so the messaging service
 * can route by either `channel_id` (preferred, per-subscription) or
 * `event_type` (fallback, broad bucket) without a translation table.
 *
 * Distinct from the existing `sheaf_fronting` channel
 * (FrontNotificationHelper), which backs the local persistent
 * fronters notification — that one is purely client-side.
 */
@Singleton
class PushNotificationChannels @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun register() {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_FRONT_CHANGE,
                    "Front changes (general)",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Fallback channel for front-change pushes when the " +
                        "specific subscription isn't yet known."
                },
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    "Reminders (general)",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Fallback channel for reminder pushes when the " +
                        "specific reminder isn't yet known."
                },
                NotificationChannel(
                    CHANNEL_SYSTEM,
                    "System",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Account and security notices."
                },
            )
        )
    }

    /**
     * Reconcile per-subscription channels with the authoritative list
     * from `/v1/notifications/receiving`. Adds Android channels for any
     * Sheaf channel we don't have one for yet; removes Android channels
     * whose Sheaf channel no longer appears (revoked, deleted, etc.) so
     * the system settings page doesn't grow forever.
     *
     * The fixed broad-category channels are deliberately preserved on
     * delete so a fallback always exists.
     */
    fun syncSubscriptions(channels: List<ReceivingChannelView>) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return

        val wanted = channels.associateBy { channelIdFor(it.channelId) }
        val existing = mgr.notificationChannels
            .filter { it.id.startsWith(SUBSCRIPTION_PREFIX) }
            .associateBy { it.id }

        // Add or update labels for any subscription the user has redeemed.
        val toCreate = mutableListOf<NotificationChannel>()
        wanted.forEach { (id, view) ->
            val name = labelFor(view)
            val current = existing[id]
            // Importance is read-only after channel creation (the user is
            // the source of truth post-create), so we only set it on first
            // create; subsequent calls just refresh the human-readable
            // name/description.
            if (current == null) {
                toCreate += NotificationChannel(
                    id, name, importanceFor(view),
                ).apply {
                    description = descriptionFor(view)
                }
            } else if (current.name != name || current.description != descriptionFor(view)) {
                // Mutating an existing channel via createNotificationChannel
                // with the same id updates its label/description without
                // resetting user-tweaked importance/sound/vibration.
                toCreate += NotificationChannel(
                    id, name, current.importance,
                ).apply {
                    description = descriptionFor(view)
                }
            }
        }
        if (toCreate.isNotEmpty()) mgr.createNotificationChannels(toCreate)

        // Remove channels for subscriptions that no longer exist server-
        // side. The user keeps any tweaked importance on the broad-
        // category fallback channels.
        existing.keys
            .filter { it !in wanted }
            .forEach { gone ->
                runCatching { mgr.deleteNotificationChannel(gone) }
                    .onFailure { Log.w(TAG, "deleteNotificationChannel($gone)", it) }
            }
    }

    /** True if an Android channel for this server channel id exists. */
    fun hasChannelFor(serverChannelId: String): Boolean {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return false
        return mgr.getNotificationChannel(channelIdFor(serverChannelId)) != null
    }

    /** Android channel id for a given server channel id. */
    fun channelIdFor(serverChannelId: String): String = "$SUBSCRIPTION_PREFIX$serverChannelId"

    private fun labelFor(view: ReceivingChannelView): String {
        val sys = view.systemLabel?.takeIf { it.isNotBlank() }
        return if (sys != null) "${view.channelName} · $sys" else view.channelName
    }

    private fun descriptionFor(view: ReceivingChannelView): String {
        val sys = view.systemLabel?.takeIf { it.isNotBlank() }
        return if (sys != null) "Notifications from $sys's \"${view.channelName}\" channel."
        else "Notifications from the \"${view.channelName}\" channel."
    }

    // Receiving payload doesn't currently expose event_type, so without
    // a hint everything gets HIGH (matching the front-change default).
    // Wire eventType-based importance picking here if/when the backend
    // adds it to ReceivingChannelView.
    private fun importanceFor(@Suppress("UNUSED_PARAMETER") view: ReceivingChannelView): Int =
        NotificationManager.IMPORTANCE_HIGH

    companion object {
        const val CHANNEL_FRONT_CHANGE = "sheaf_front_change"
        const val CHANNEL_REMINDERS = "sheaf_reminders"
        const val CHANNEL_SYSTEM = "sheaf_system"

        // Prefix for the per-subscription channels we create dynamically
        // from /v1/notifications/receiving. Anything with this prefix is
        // safe for syncSubscriptions to delete; anything without is one
        // of the fixed broad-category channels and stays.
        const val CHANNEL_PREFIX_SUBSCRIPTION = "sheaf_ch_"
        private const val SUBSCRIPTION_PREFIX = CHANNEL_PREFIX_SUBSCRIPTION
        private const val TAG = "PushNotificationChannels"
    }
}
