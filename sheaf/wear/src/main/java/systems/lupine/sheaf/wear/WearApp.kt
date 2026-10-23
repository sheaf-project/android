package systems.lupine.sheaf.wear

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import systems.lupine.sheaf.wear.data.WearAuthManager
import systems.lupine.sheaf.wear.data.WearFileUrlInterceptor
import systems.lupine.sheaf.wear.data.WearSettingsStore

/**
 * Teaches Coil where this instance's files actually live.
 *
 * The API returns avatars as server-relative paths, so handing one straight to
 * `AsyncImage` gave Coil a string with no scheme or host, which it failed on
 * silently: every image avatar on the watch was an empty circle. The phone has
 * resolved these since the CDN work; the watch had nothing equivalent.
 *
 * Done here, on the loader, rather than at each `MemberAvatar` call site:
 * seven screens draw avatars and none of them should have to know about
 * `file_cdn_base`.
 */
class WearApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(
                    WearFileUrlInterceptor(
                        auth = WearAuthManager(this@WearApp),
                        settings = WearSettingsStore(this@WearApp),
                    ),
                )
            }
            .build()
}
