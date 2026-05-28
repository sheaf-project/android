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
        val token = pendingToken ?: runBlocking { prefs.accessToken.firstOrNull() }
        val cfClientId = runBlocking { prefs.cfClientId.firstOrNull() }
        val cfClientSecret = runBlocking { prefs.cfClientSecret.firstOrNull() }
        val builder = chain.request().newBuilder()
            .addHeader("X-Sheaf-Client", clientHeader)
        if (token != null) {
            builder.addHeader("Authorization", "Bearer $token")
        }
        if (cfClientId != null && cfClientSecret != null) {
            builder.addHeader("CF-Access-Client-Id", cfClientId)
            builder.addHeader("CF-Access-Client-Secret", cfClientSecret)
        }
        return chain.proceed(builder.build())
    }
}
