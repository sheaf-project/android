package systems.lupine.sheaf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import systems.lupine.sheaf.data.repository.PreferencesRepository
import systems.lupine.sheaf.ui.SheafApp
import systems.lupine.sheaf.ui.theme.SheafTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by prefs.themeMode.collectAsState(initial = "system")
            SheafTheme(themeMode = themeMode) {
                SheafApp()
            }
        }
    }
}
