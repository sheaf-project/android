package systems.lupine.sheaf

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import systems.lupine.sheaf.lock.AppLockManager
import systems.lupine.sheaf.notification.FrontNotificationHelper
import javax.inject.Inject

@HiltAndroidApp
class SheafApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject lateinit var notificationHelper: FrontNotificationHelper
    @Inject lateinit var imageLoader: ImageLoader
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var lockManager: AppLockManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannel()
        lockManager.start()
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}
