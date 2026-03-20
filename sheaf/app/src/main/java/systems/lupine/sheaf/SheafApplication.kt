package systems.lupine.sheaf

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import systems.lupine.sheaf.notification.FrontNotificationHelper
import javax.inject.Inject

@HiltAndroidApp
class SheafApplication : Application() {

    @Inject lateinit var notificationHelper: FrontNotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannel()
    }
}
