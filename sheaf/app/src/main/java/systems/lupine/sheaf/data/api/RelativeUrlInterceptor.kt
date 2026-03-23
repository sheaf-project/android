package systems.lupine.sheaf.data.api

import coil.intercept.Interceptor
import coil.request.ImageResult
import systems.lupine.sheaf.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coil interceptor that resolves relative image URLs (e.g. `/v1/files/...`)
 * to absolute URLs by prepending the configured API base URL.
 *
 * This is needed because the API returns relative paths for avatar URLs,
 * which the browser resolves via page origin but mobile needs explicitly.
 */
@Singleton
class RelativeUrlInterceptor @Inject constructor(
    private val prefs: PreferencesRepository,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        if (data is String && data.startsWith("/")) {
            val baseUrl = prefs.baseUrl.firstOrNull()?.trimEnd('/')
            if (baseUrl != null) {
                val resolved = "$baseUrl$data"
                val newRequest = chain.request.newBuilder()
                    .data(resolved)
                    .build()
                return chain.proceed(newRequest)
            }
        }
        return chain.proceed(chain.request)
    }
}
