package systems.lupine.sheaf.data.api

import com.squareup.moshi.Moshi
import dagger.Lazy
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import systems.lupine.sheaf.data.repository.AccountDataWiper
import systems.lupine.sheaf.data.repository.PreferencesRepository
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * The authenticator must not react to a 401 from a foreign host. The shared
 * client fetches avatars (e.g. for widgets) from hosts that aren't ours, and a
 * refresh-and-retry there would both burn a refresh-token rotation and hand the
 * new bearer to that host.
 */
class TokenAuthenticatorTest {

    private val prefs = mockk<PreferencesRepository>(relaxed = true).apply {
        every { baseUrl } returns flowOf("https://app.sheaf.sh")
    }
    private val authenticator = TokenAuthenticator(
        prefs = prefs,
        moshi = Moshi.Builder().build(),
        lazyClient = Lazy { mockk<OkHttpClient>() },
        accountDataWiper = Lazy { mockk<AccountDataWiper>() },
    )

    private fun response401(url: String) = Response.Builder()
        .request(Request.Builder().url(url).header("Authorization", "Bearer old").build())
        .protocol(Protocol.HTTP_1_1)
        .code(401).message("Unauthorized")
        .build()

    @Test fun `a 401 from a foreign host does not trigger a refresh`() {
        val result = authenticator.authenticate(null, response401("https://images.example.com/a.png"))

        assertNull(result)
        // It short-circuits before ever touching the refresh token.
        verify(exactly = 0) { prefs.refreshToken }
    }

    @Test fun `a 401 from the image CDN is also ignored`() {
        val result = authenticator.authenticate(null, response401("https://cdn.sheaf.sh/avatars/x.png"))
        assertNull(result)
        verify(exactly = 0) { prefs.refreshToken }
    }
}
