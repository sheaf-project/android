package systems.lupine.sheaf

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.repository.PreferencesRepository
import systems.lupine.sheaf.lock.AppLockManager
import systems.lupine.sheaf.notification.FrontNotificationHelper
import systems.lupine.sheaf.push.PushNotificationChannels
import systems.lupine.sheaf.util.TechnicalErrors
import javax.inject.Inject

@HiltAndroidApp
class SheafApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject lateinit var notificationHelper: FrontNotificationHelper
    @Inject lateinit var pushChannels: PushNotificationChannels
    @Inject lateinit var imageLoader: ImageLoader
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var lockManager: AppLockManager
    @Inject lateinit var prefs: PreferencesRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannel()
        pushChannels.register()
        lockManager.start()
        seedTechnicalErrorsPreference()
    }

    /**
     * Put the stored "show technical error details" choice where error
     * formatting can see it, before anything has a chance to fail.
     *
     * Read from the device rather than the account on purpose. The account's
     * copy arrives with the first successful load, and the failures this
     * preference exists to explain are exactly the ones where that load does
     * not happen: a preference that has to be fetched before it applies is off
     * precisely when somebody is trying to find out why nothing is loading.
     *
     * Collected for the life of the process, not read once, so a change made
     * on this device, or adopted from the account mid-session, takes effect on
     * the next error rather than the next launch.
     */
    private fun seedTechnicalErrorsPreference() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            prefs.showTechnicalErrors.collect { TechnicalErrors.set(it) }
        }
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}
