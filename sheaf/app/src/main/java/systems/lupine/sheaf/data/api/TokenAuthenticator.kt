package systems.lupine.sheaf.data.api

import com.squareup.moshi.Moshi
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
import systems.lupine.sheaf.data.repository.PreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenAuthenticator @Inject constructor(
    private val prefs: PreferencesRepository,
    private val moshi: Moshi,
) : Authenticator {

    // Synchronized to prevent concurrent refresh races: if two 401s arrive at once,
    // only one thread does the refresh; the other re-checks the stored token and retries.
    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        // Don't retry endpoints where 401 means "wrong password", not "expired token"
        val path = response.request.url.encodedPath
        if (path.endsWith("/auth/refresh") ||
            path.endsWith("/auth/delete-account") ||
            path.endsWith("/delete-confirmation")) return null

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
        val baseUrl = runBlocking { prefs.baseUrl.firstOrNull() } ?: return null

        val body = moshi.adapter(TokenRefresh::class.java)
            .toJson(TokenRefresh(refreshToken))
            .toRequestBody("application/json".toMediaType())

        val refreshRequest = Request.Builder()
            .url("$baseUrl/v1/auth/refresh")
            .post(body)
            .build()

        val refreshResponse = runCatching {
            OkHttpClient().newCall(refreshRequest).execute()
        }.getOrNull() ?: return null

        if (!refreshResponse.isSuccessful) {
            runBlocking { prefs.clearTokens() }
            return null
        }

        val tokens = runCatching {
            refreshResponse.body?.string()?.let {
                moshi.adapter(TokenResponse::class.java).fromJson(it)
            }
        }.getOrNull() ?: run {
            runBlocking { prefs.clearTokens() }
            return null
        }

        runBlocking { prefs.saveTokens(tokens.accessToken, tokens.refreshToken) }

        return response.request.newBuilder()
            .header("Authorization", "Bearer ${tokens.accessToken}")
            .build()
    }
}
