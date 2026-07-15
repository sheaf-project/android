package systems.lupine.sheaf.data.api

import systems.lupine.sheaf.BuildConfig
import systems.lupine.sheaf.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val prefs: PreferencesRepository,
) : Interceptor {

    // In-memory token used during intermediate auth steps (TOTP, email verification)
    // so we never have to write to DataStore until fully authenticated.
    @Volatile var pendingToken: String? = null

    // Tracks the actual installed build version; used to make backend
    // logs and admin tooling answer "which Android version is this
    // request from" without guessing. Was previously hardcoded to
    // "Sheaf Android/1.0.0" — so every release looked the same on the
    // wire. Now driven from BuildConfig so the value rolls forward
    // automatically with each build, including dev builds (suffix
    // "-dev" via the gradle git-tag fallback).
    private val clientHeader = "Sheaf Android/${BuildConfig.VERSION_NAME}"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()
            .addHeader("X-Sheaf-Client", clientHeader)

        // Credentials go only to the instance's own origins. The Coil image
        // client shares this interceptor, and image URLs can point at an
        // external host (a remote avatar, an image embedded in a bio), so an
        // unconditional bearer / CF-Access header would hand the user's live
        // session to whatever server that image lives on.
        val trusted = runBlocking {
            isTrustedCredentialOrigin(
                request.url,
                prefs.baseUrl.firstOrNull(),
                prefs.fileCdnBase.firstOrNull(),
            )
        }
        if (trusted) {
            val token = pendingToken ?: runBlocking { prefs.accessToken.firstOrNull() }
            if (token != null) {
                builder.addHeader("Authorization", "Bearer $token")
            }
            val cfClientId = runBlocking { prefs.cfClientId.firstOrNull() }
            val cfClientSecret = runBlocking { prefs.cfClientSecret.firstOrNull() }
            if (cfClientId != null && cfClientSecret != null) {
                builder.addHeader("CF-Access-Client-Id", cfClientId)
                builder.addHeader("CF-Access-Client-Secret", cfClientSecret)
            }
        }
        return chain.proceed(builder.build())
    }
}
