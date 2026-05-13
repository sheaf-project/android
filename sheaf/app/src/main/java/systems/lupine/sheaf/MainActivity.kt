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
            SheafTheme(themeMode = themeMode) {
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
        if (data.scheme != "sheaf") return
        if (data.host != "notifications") return
        if (data.pathSegments.firstOrNull() != "redeem") return
        val code = data.getQueryParameter("code")?.takeIf { it.isNotBlank() } ?: return
        pendingRedemption.set(code)
    }
}
