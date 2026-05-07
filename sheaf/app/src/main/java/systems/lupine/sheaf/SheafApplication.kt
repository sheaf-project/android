package systems.lupine.sheaf

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import systems.lupine.sheaf.icon.IconCoordinator
import systems.lupine.sheaf.lock.AppLockManager
import systems.lupine.sheaf.notification.FrontNotificationHelper
import javax.inject.Inject

@HiltAndroidApp
class SheafApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject lateinit var notificationHelper: FrontNotificationHelper
    @Inject lateinit var imageLoader: ImageLoader
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var lockManager: AppLockManager
    @Inject lateinit var iconCoordinator: IconCoordinator

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannel()
        lockManager.start()
        iconCoordinator.start()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // System dark-mode flip while the app is in memory: re-evaluate the
        // launcher icon if the user's theme is set to "system". No-op for
        // explicit light/dark since IconCoordinator's no-op guard catches it.
        iconCoordinator.refresh()
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}
