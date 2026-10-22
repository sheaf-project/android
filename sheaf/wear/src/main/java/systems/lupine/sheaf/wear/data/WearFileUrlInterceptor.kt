package systems.lupine.sheaf.wear.data

import coil.intercept.Interceptor
import coil.request.ImageResult

/**
 * Resolves a server-relative file path into something Coil can fetch, using
 * [resolveFileUrl]. The watch's counterpart to the phone's
 * `RelativeUrlInterceptor`, with the same rules.
 *
 * Both sources are read per request rather than captured once: the base URL
 * arrives when the watch is paired, and the CDN base is refreshed on each
 * sync, so a loader built at process start would otherwise hold whatever was
 * known before the watch had been signed in.
 */
class WearFileUrlInterceptor(
    private val auth: WearAuthManager,
    private val settings: WearSettingsStore,
) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        if (data is String) {
            val resolved = resolveFileUrl(
                raw = data,
                baseUrl = auth.baseUrl,
                cdnBase = settings.fileCdnBase,
            )
            if (resolved != null && resolved != data) {
                return chain.proceed(chain.request.newBuilder().data(resolved).build())
            }
        }
        return chain.proceed(chain.request)
    }
}
