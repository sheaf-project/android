package systems.lupine.sheaf.data.api

import systems.lupine.sheaf.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network-layer backstop that strips credentials from any hop not going to the
 * configured API origin.
 *
 * [AuthInterceptor] is an application interceptor, so it only sees the original
 * request and adds credentials once, scoped to the API origin. But two things
 * happen below it, per network hop, that it can't police:
 *
 *  - A cross-origin redirect. OkHttp drops `Authorization` when the host
 *    changes, but not custom headers, so `CF-Access-*` would follow an
 *    API -> external redirect to the external host.
 *  - A [TokenAuthenticator] retry. On a 401 from a foreign host (e.g. a widget
 *    fetching an avatar via the shared client) it would re-attach a freshly
 *    minted bearer directly on the retried request.
 *
 * As a NETWORK interceptor this runs on every hop, including redirect follow-ups
 * and authenticator retries, so it is the last line that guarantees the session
 * never leaves for a host that isn't ours.
 */
@Singleton
class CredentialGuardInterceptor @Inject constructor(
    private val prefs: PreferencesRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val isApiOrigin = runBlocking { originMatches(request.url, prefs.baseUrl.firstOrNull()) }
        if (isApiOrigin) return chain.proceed(request)

        val stripped = request.newBuilder()
            .removeHeader("Authorization")
            .removeHeader("CF-Access-Client-Id")
            .removeHeader("CF-Access-Client-Secret")
            .removeHeader("Cookie")
            .build()
        return chain.proceed(stripped)
    }
}
