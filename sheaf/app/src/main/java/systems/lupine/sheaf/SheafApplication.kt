package systems.lupine.sheaf

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import systems.lupine.sheaf.notification.FrontNotificationHelper
import javax.inject.Inject

@HiltAndroidApp
class SheafApplication : Application(), ImageLoaderFactory {

    @Inject lateinit var notificationHelper: FrontNotificationHelper
    @Inject lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannel()
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}
