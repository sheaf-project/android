package systems.lupine.sheaf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.repository.PreferencesRepository
import systems.lupine.sheaf.data.repository.WatchSessionRepository
import systems.lupine.sheaf.datalayer.PhoneDataLayerService
import systems.lupine.sheaf.lock.AppLockManager
import systems.lupine.sheaf.lock.LockState
import systems.lupine.sheaf.push.PushChannelSync
import systems.lupine.sheaf.push.PushDeviceRegistrar
import systems.lupine.sheaf.ui.SheafApp
import systems.lupine.sheaf.ui.lock.AppLockScreen
import systems.lupine.sheaf.ui.notifications.PendingRedemptionHolder
import systems.lupine.sheaf.ui.theme.SheafTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var prefs: PreferencesRepository
    @Inject lateinit var lockManager: AppLockManager
    @Inject lateinit var watchSession: WatchSessionRepository
    @Inject lateinit var pushRegistrar: PushDeviceRegistrar
    @Inject lateinit var pushChannelSync: PushChannelSync
    @Inject lateinit var pendingRedemption: PendingRedemptionHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        captureRedemptionDeepLink(intent)
        // Belt-and-suspenders: provision and push the wear app's
        // companion-session credentials whenever the phone app launches
        // while logged in. Handles the upgrade case where an existing
        // user's wear app is still running with stale phone primary
        // tokens — we want it switched to its own child session before
        // either device's next refresh attempt.
        lifecycleScope.launch {
            if (prefs.accessToken.first() != null) {
                runCatching {
                    PhoneDataLayerService.pushWatchCredentials(
                        applicationContext, prefs, watchSession,
                    )
                }
                // Backstop the FCM-registration call in case onNewToken
                // fired before login (token cached, account swapped) or
                // an earlier registration failed and never retried.
                runCatching { pushRegistrar.registerCurrentToken() }
                // Reconcile Android NotificationChannels with the user's
                // subscribed Sheaf channels so each shows up as its own
                // entry in system settings.
                runCatching { pushChannelSync.sync() }
            }
        }
        setContent {
            val themeMode by prefs.themeMode.collectAsState(initial = "system")
            val themePalette by prefs.themePalette.collectAsState(initial = "purple")
            SheafTheme(themeMode = themeMode, themePalette = themePalette) {
                val lockState by lockManager.lockState.collectAsState()
                // Always render SheafApp so its NavController state survives a
                // re-lock when the app is backgrounded; the lock screen is an
                // opaque overlay on top of it.
                Box(modifier = Modifier.fillMaxSize()) {
                    SheafApp(pendingRedemption = pendingRedemption)
                    when (lockState) {
                        LockState.Loading -> Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        )
                        LockState.Locked -> AppLockScreen(onUnlock = { lockManager.unlock() })
                        LockState.Unlocked -> Unit
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Activity is launchMode=singleTop; deep-link intents while alive
        // arrive here instead of restarting the activity.
        setIntent(intent)
        captureRedemptionDeepLink(intent)
    }

    private fun captureRedemptionDeepLink(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        // Two entry shapes resolve here:
        //  - sheaf://notifications/redeem?code=...  (custom-scheme CTA,
        //    works from any instance's web redeem page)
        //  - https://sheaf.sh/redeem?code=...       (verified App Link on
        //    the canonical domain)
        val isCustomScheme = data.scheme == "sheaf" &&
            data.host == "notifications" &&
            data.pathSegments.firstOrNull() == "redeem"
        val isAppLink = data.scheme == "https" &&
            data.host == "sheaf.sh" &&
            data.pathSegments.firstOrNull() == "redeem"
        if (!isCustomScheme && !isAppLink) return
        val code = data.getQueryParameter("code")?.takeIf { it.isNotBlank() } ?: return
        // App Link form also carries the instance the link was minted for
        // (e.g. instance=https%3A%2F%2Ftest.sheaf.sh). Kept so the redeem
        // flow can refuse a link aimed at a different server than the one
        // this device is signed into.
        val instance = data.getQueryParameter("instance")?.takeIf { it.isNotBlank() }
        pendingRedemption.set(code, instance)
    }
}
