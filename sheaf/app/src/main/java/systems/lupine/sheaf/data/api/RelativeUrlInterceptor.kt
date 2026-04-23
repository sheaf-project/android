package systems.lupine.sheaf.data.api

import coil.intercept.Interceptor
import coil.request.ImageResult
import systems.lupine.sheaf.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coil interceptor that rewrites image URLs so they're served from the
 * instance's `file_cdn_base` when configured:
 *   - Absolute URLs whose host matches the API base URL are rewritten to use
 *     the CDN.
 *   - Leading-slash relative paths (e.g. `/avatars/...`) are resolved against
 *     the CDN if set, otherwise against the API base URL.
 *   - Bare relative paths (e.g. `avatars/xyz.jpg`) are treated as
 *     server-relative and resolved the same way.
 */
@Singleton
class RelativeUrlInterceptor @Inject constructor(
    private val prefs: PreferencesRepository,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        if (data is String && data.isNotBlank()) {
            val cdnBase = prefs.fileCdnBase.firstOrNull()?.trimEnd('/')?.ifBlank { null }
            val baseUrl = prefs.baseUrl.firstOrNull()?.trimEnd('/')
            val preferredBase = cdnBase ?: baseUrl
            val resolved: String? = when {
                cdnBase != null && baseUrl != null && data.startsWith("$baseUrl/") ->
                    cdnBase + data.removePrefix(baseUrl)
                data.startsWith("/") -> preferredBase?.let { "$it$data" }
                !data.contains("://") -> preferredBase?.let { "$it/$data" }
                else -> null
            }
            if (resolved != null && resolved != data) {
                val newRequest = chain.request.newBuilder()
                    .data(resolved)
                    .build()
                return chain.proceed(newRequest)
            }
        }
        return chain.proceed(chain.request)
    }
}
