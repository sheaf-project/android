package systems.lupine.sheaf.datalayer

import android.content.Context
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.repository.PreferencesRepository
import javax.inject.Inject

@AndroidEntryPoint
class PhoneDataLayerService : WearableListenerService() {

    companion object {
        const val PATH_CREDENTIALS = "/sheaf/credentials"
        const val PATH_CREDENTIALS_REQUEST = "/sheaf/credentials/request"

        fun pushCredentials(
            context: Context,
            baseUrl: String,
            accessToken: String,
            refreshToken: String,
        ) {
            val request = PutDataMapRequest.create(PATH_CREDENTIALS).apply {
                dataMap.putString("base_url", baseUrl)
                dataMap.putString("access_token", accessToken)
                dataMap.putString("refresh_token", refreshToken)
                dataMap.putLong("updated_at", System.currentTimeMillis())
            }
            Wearable.getDataClient(context)
                .putDataItem(request.asPutDataRequest().setUrgent())
        }
    }

    @Inject lateinit var prefs: PreferencesRepository

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == PATH_CREDENTIALS_REQUEST) {
            scope.launch {
                val baseUrl = prefs.baseUrl.first() ?: return@launch
                val access = prefs.accessToken.first() ?: return@launch
                val refresh = prefs.refreshToken.first() ?: return@launch
                pushCredentials(applicationContext, baseUrl, access, refresh)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
