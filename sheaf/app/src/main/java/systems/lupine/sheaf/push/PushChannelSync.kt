package systems.lupine.sheaf.push

import android.util.Log
import systems.lupine.sheaf.data.api.SheafApiService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pulls the authenticated user's subscribed Sheaf notification channels
 * and feeds them to [PushNotificationChannels.syncSubscriptions] so the
 * Android system-settings page exposes one entry per Sheaf subscription
 * (mute / importance / silence independently).
 *
 * Best-effort: callers wrap in runCatching so a failed sync doesn't
 * block login or the redemption flow.
 */
@Singleton
class PushChannelSync @Inject constructor(
    private val api: SheafApiService,
    private val channels: PushNotificationChannels,
) {
    suspend fun sync() {
        runCatching { api.listReceivingChannels() }
            .onSuccess { channels.syncSubscriptions(it) }
            .onFailure { Log.w(TAG, "sync failed", it) }
    }

    private companion object {
        const val TAG = "PushChannelSync"
    }
}
