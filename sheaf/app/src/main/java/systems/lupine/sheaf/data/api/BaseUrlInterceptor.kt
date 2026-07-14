package systems.lupine.sheaf.data.api

import systems.lupine.sheaf.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BaseUrlInterceptor @Inject constructor(
    private val prefs: PreferencesRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        // Only swap hosts for Retrofit's placeholder URL. Requests that already
        // carry a real host (e.g. Coil image loads to the CDN) must pass through
        // untouched, otherwise we'd rewrite the CDN host back to the API host.
        if (original.url.host != "localhost") return chain.proceed(original)

        val baseUrl = runBlocking { prefs.baseUrl.firstOrNull() }
            ?.trimEnd('/')
            ?.toHttpUrlOrNull()
            ?: return chain.proceed(original)

        val newRequest = original.newBuilder().url(applyBaseUrl(original.url, baseUrl)).build()
        return chain.proceed(newRequest)
    }
}

/**
 * Rewrite Retrofit's placeholder URL onto the configured instance.
 *
 * Endpoint paths are absolute ("/v1/members"), so the original rewrite only
 * swapped scheme/host/port. That silently dropped any path prefix on the base
 * URL: an instance hosted at https://example.org/sheaf/ had every request sent
 * to https://example.org/v1/... instead, i.e. at whatever lives on the domain
 * root. Carry the base URL's path prefix through. The query string rides along
 * on the original builder untouched.
 */
internal fun applyBaseUrl(original: HttpUrl, base: HttpUrl): HttpUrl {
    // HttpUrl always reports at least "/", so an origin-root base contributes "".
    val prefix = base.encodedPath.trimEnd('/')
    return original.newBuilder()
        .scheme(base.scheme)
        .host(base.host)
        .port(base.port)
        .encodedPath(prefix + original.encodedPath)
        .build()
}
