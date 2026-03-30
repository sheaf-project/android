package systems.lupine.sheaf.data.api

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

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = pendingToken ?: runBlocking { prefs.accessToken.firstOrNull() }
        val builder = chain.request().newBuilder()
            .addHeader("X-Sheaf-Client", "Sheaf Android/1.0.0")
        if (token != null) {
            builder.addHeader("Authorization", "Bearer $token")
        }
        return chain.proceed(builder.build())
    }
}
