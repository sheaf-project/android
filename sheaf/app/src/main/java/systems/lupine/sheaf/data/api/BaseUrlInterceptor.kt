package systems.lupine.sheaf.data.api

import systems.lupine.sheaf.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
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

        val newUrl = original.url.newBuilder()
            .scheme(baseUrl.scheme)
            .host(baseUrl.host)
            .port(baseUrl.port)
            .build()

        val newRequest = original.newBuilder().url(newUrl).build()
        return chain.proceed(newRequest)
    }
}
