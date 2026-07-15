package systems.lupine.sheaf.data.api

import com.squareup.moshi.Moshi
import dagger.Lazy
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import systems.lupine.sheaf.data.model.TokenRefresh
import systems.lupine.sheaf.data.model.TokenResponse
import systems.lupine.sheaf.data.repository.AccountDataWiper
import systems.lupine.sheaf.data.repository.PreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenAuthenticator @Inject constructor(
    private val prefs: PreferencesRepository,
    private val moshi: Moshi,
    private val lazyClient: Lazy<OkHttpClient>,
    // Lazy so the OkHttp graph doesn't have to build the Room-backed wiper up
    // front; it's only needed on the rare forced-logout path.
    private val accountDataWiper: Lazy<AccountDataWiper>,
) : Authenticator {

    // Synchronized to prevent concurrent refresh races: if two 401s arrive at once,
    // only one thread does the refresh; the other re-checks the stored token and retries.
    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        // Don't retry endpoints where 401 means something other than "expired
        // access token". /auth/refresh and /auth/delete-account treat 401 as
        // "wrong password / bad refresh". /notifications/redeem returns 401
        // when the server can't bind the request to a session via the auth
        // path it expects (separate from our Bearer flow) — refreshing won't
        // help and was previously spinning to MAX_FOLLOW_UPS.
        val path = response.request.url.encodedPath
        if (path.endsWith("/auth/refresh") ||
            path.endsWith("/auth/delete-account") ||
            path.endsWith("/delete-confirmation") ||
            path.endsWith("/notifications/redeem")) return null

        val storedAccessToken = runBlocking { prefs.accessToken.firstOrNull() }

        // If another thread already refreshed while we were waiting for the lock,
        // the stored token will differ from what was on the failed request — just retry.
        val failedRequestToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        if (failedRequestToken != null && failedRequestToken != storedAccessToken && storedAccessToken != null) {
            return response.request.newBuilder()
                .header("Authorization", "Bearer $storedAccessToken")
                .build()
        }

        val refreshToken = runBlocking { prefs.refreshToken.firstOrNull() } ?: return null
        val baseUrl = runBlocking { prefs.baseUrl.firstOrNull() }?.trimEnd('/') ?: return null

        val body = moshi.adapter(TokenRefresh::class.java)
            .toJson(TokenRefresh(refreshToken))
            .toRequestBody("application/json".toMediaType())

        val refreshRequest = Request.Builder()
            .url("$baseUrl/v1/auth/refresh")
            .post(body)
            .build()

        // Reuse the configured client (preserves SSL trust-all in debug, timeouts, etc.)
        // but strip the authenticator to prevent recursion if the refresh endpoint itself 401s.
        val refreshClient = lazyClient.get().newBuilder()
            .authenticator(Authenticator.NONE)
            .build()

        val refreshResponse = runCatching {
            refreshClient.newCall(refreshRequest).execute()
        }.getOrNull() ?: return null

        if (!refreshResponse.isSuccessful) {
            // Only a 401 means the refresh token is genuinely invalid/expired.
            // Any other failure (5xx, 503, etc.) is transient — don't destroy the session.
            if (refreshResponse.code == 401) {
                // Forced logout: clear the tokens AND wipe the account's cache
                // and offline queue, so the next account signed in on this device
                // can't inherit them (clearTokens alone left both behind).
                runBlocking {
                    prefs.clearTokens()
                    accountDataWiper.get().wipe()
                }
            }
            return null
        }

        val tokens = runCatching {
            refreshResponse.body?.string()?.let {
                moshi.adapter(TokenResponse::class.java).fromJson(it)
            }
        }.getOrNull() ?: return null

        runBlocking { prefs.saveTokens(tokens.accessToken, tokens.refreshToken) }

        return response.request.newBuilder()
            .header("Authorization", "Bearer ${tokens.accessToken}")
            .build()
    }
}
