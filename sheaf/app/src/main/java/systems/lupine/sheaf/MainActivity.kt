package systems.lupine.sheaf

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
import dagger.hilt.android.AndroidEntryPoint
import systems.lupine.sheaf.data.repository.PreferencesRepository
import systems.lupine.sheaf.lock.AppLockManager
import systems.lupine.sheaf.lock.LockState
import systems.lupine.sheaf.ui.SheafApp
import systems.lupine.sheaf.ui.lock.AppLockScreen
import systems.lupine.sheaf.ui.theme.SheafTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var prefs: PreferencesRepository
    @Inject lateinit var lockManager: AppLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by prefs.themeMode.collectAsState(initial = "system")
            SheafTheme(themeMode = themeMode) {
                val lockState by lockManager.lockState.collectAsState()
                // Always render SheafApp so its NavController state survives a
                // re-lock when the app is backgrounded; the lock screen is an
                // opaque overlay on top of it.
                Box(modifier = Modifier.fillMaxSize()) {
                    SheafApp()
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
}
