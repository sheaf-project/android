package systems.lupine.sheaf.data.api

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import systems.lupine.sheaf.data.repository.PreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the backend's `sheaf_trusted_device` cookie across app launches so
 * that "remember this device" actually skips TOTP on subsequent logins.
 *
 * Scope is intentionally narrow: only this one cookie is stored. The other
 * cookies the server sets (`sheaf_session`, `sheaf_refresh`) are shadow state
 * for browsers — mobile uses bearer tokens out of the JSON `TokenResponse`
 * for those flows, so persisting them would be redundant and confusing.
 *
 * The cookie is only attached to requests under the path the server sets it
 * for (`/v1/auth/`), matching standard cookie semantics. In practice it's
 * only read by `/v1/auth/login`.
 *
 * ──────────────────────────────────────────────────────────────────────────
 * Alternative protocol (not implemented)
 * ──────────────────────────────────────────────────────────────────────────
 * If we ever decide cookies on a bearer-token client are too leaky a shape,
 * the cleaner alternative is a backend change: have the login endpoint
 * return the trusted-device token in the `TokenResponse` body when the
 * caller advertises a non-cookie client (e.g. via `X-Sheaf-Client: android`),
 * and accept it as `X-Sheaf-Trusted-Device` on subsequent login requests.
 * That keeps mobile fully bearer-token-shaped and lets the server keep the
 * cookie path for browsers without splitting the auth model. iOS would need
 * to migrate alongside since it currently relies on `URLSession`'s default
 * cookie storage.
 */
@Singleton
class TrustedDeviceCookieJar @Inject constructor(
    private val prefs: PreferencesRepository,
) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // Only store the cookie when it came from our own API origin, so a
        // response from some other host can't seed it.
        if (!isApiOrigin(url)) return
        val tdc = cookies.firstOrNull { it.name == COOKIE_NAME } ?: return
        runBlocking {
            prefs.saveTrustedDeviceCookie(tdc.value, tdc.expiresAt)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        // Host-scope as well as path-scope: the trusted-device token is bound to
        // the instance that issued it and must not ride along to another host
        // (e.g. after the user points the app at a different server).
        if (!url.encodedPath.startsWith(COOKIE_PATH)) return emptyList()
        if (!isApiOrigin(url)) return emptyList()
        val value = prefs.trustedDeviceCookieBlocking() ?: return emptyList()
        return listOf(
            Cookie.Builder()
                .name(COOKIE_NAME)
                .value(value)
                .domain(url.host)
                .path(COOKIE_PATH)
                .build()
        )
    }

    private fun isApiOrigin(url: HttpUrl): Boolean =
        originMatches(url, runBlocking { prefs.baseUrl.firstOrNull() })

    private companion object {
        const val COOKIE_NAME = "sheaf_trusted_device"
        const val COOKIE_PATH = "/v1/auth/"
    }
}
