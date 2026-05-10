package systems.lupine.sheaf.push

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.PushDeviceRegistration
import systems.lupine.sheaf.data.model.PushDeviceUnregister
import systems.lupine.sheaf.data.repository.PreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the device-side of mobile push registration: turns the
 * current FCM (or no-op) token into POST/DELETE calls against the
 * backend's `/v1/devices/push` endpoints.
 *
 * Idempotent. Best-effort: every call swallows errors and logs them.
 * The server has its own resilience (LRU eviction, lazy 410 reap) so
 * a missed register-or-unregister doesn't strand the system.
 *
 * Triggered from three places:
 *  - [SheafMessagingService.onNewToken] when FCM rotates the token
 *  - [systems.lupine.sheaf.ui.auth.AuthViewModel] after login completes
 *    (so first install lands without waiting for rotation), and before
 *    logout (so the row is dropped while we still have a session)
 *  - [systems.lupine.sheaf.MainActivity] on every launch as a backstop
 *    for upgrades and missed-rotation cases — mirrors the wear
 *    companion-credential push pattern.
 */
@Singleton
class PushDeviceRegistrar @Inject constructor(
    private val api: SheafApiService,
    private val prefs: PreferencesRepository,
    private val tokenProvider: PushTokenProvider,
    @ApplicationContext private val context: Context,
) {
    /**
     * Fetches the current push token from the platform provider and
     * registers it. No-op when the provider returns null (.open
     * flavour, Firebase uninitialised, GMS missing, etc.) or the user
     * isn't logged in.
     */
    suspend fun registerCurrentToken() {
        if (tokenProvider.platform == PushPlatform.NONE) return
        if (prefs.accessToken.first() == null) return
        val token = tokenProvider.getToken() ?: return
        register(token)
    }

    /**
     * Registers a known token. Used by `onNewToken` where Firebase has
     * already handed us the fresh value.
     */
    suspend fun register(token: String) {
        if (tokenProvider.platform == PushPlatform.NONE) return
        if (prefs.accessToken.first() == null) return
        runCatching {
            api.registerPushDevice(
                PushDeviceRegistration(
                    platform = tokenProvider.platform.wireValue,
                    token = token,
                    installId = prefs.getOrCreatePushInstallId(),
                    appVersion = appVersion(),
                )
            )
        }.onFailure { Log.w(TAG, "push device registration failed", it) }
    }

    /**
     * Drops the current token from the server. Called from logout
     * before auth tokens are cleared (the DELETE endpoint requires a
     * session). Best-effort: failure leaves the row to be reaped
     * lazily on next delivery via the 410 / Unregistered path.
     */
    suspend fun unregisterCurrent() {
        if (tokenProvider.platform == PushPlatform.NONE) return
        val token = runCatching { tokenProvider.getToken() }.getOrNull() ?: return
        runCatching {
            api.unregisterPushDevice(PushDeviceUnregister(token))
        }.onFailure { Log.w(TAG, "push device unregister failed", it) }
    }

    private fun appVersion(): String? = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()

    private companion object {
        const val TAG = "PushDeviceRegistrar"
    }
}
