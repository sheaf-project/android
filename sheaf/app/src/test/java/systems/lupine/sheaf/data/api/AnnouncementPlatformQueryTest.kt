package systems.lupine.sheaf.data.api

import kotlinx.coroutines.runBlocking
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import systems.lupine.sheaf.di.NetworkModule
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The announcements request names what this client is.
 *
 * Worth a real request rather than reading the annotation back: the platform
 * is a Kotlin default argument on a Retrofit interface, which reaches the
 * proxy through a synthetic method, and a default that quietly failed to
 * apply would leave the app asking for the untargeted set only. Nothing would
 * look broken; announcements aimed at Android would simply never arrive.
 *
 * A bare socket rather than a mock web server, so the check costs no new
 * dependency: all it has to do is read one request line and answer `[]`.
 */
class AnnouncementPlatformQueryTest {

    @Test fun `the announcements request names android`() {
        val server = ServerSocket(0)
        var requestLine: String? = null

        val serving = thread {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                requestLine = reader.readLine()
                // Drain the headers so the client is not left writing into a
                // closed stream while we reply.
                while (true) {
                    val line = reader.readLine()
                    if (line.isNullOrEmpty()) break
                }
                socket.getOutputStream().write(
                    (
                        "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: 2\r\n" +
                            "Connection: close\r\n\r\n[]"
                        ).toByteArray()
                )
            }
        }

        try {
            val api = Retrofit.Builder()
                .baseUrl("http://127.0.0.1:${server.localPort}/")
                .addConverterFactory(MoshiConverterFactory.create(NetworkModule.provideMoshi()))
                .build()
                .create(SheafApiService::class.java)

            runBlocking { api.getAnnouncements() }
            serving.join(5_000)

            val line = requestLine
            assertTrue(line != null, "no request reached the server")
            assertEquals("GET /v1/announcements?platform=android HTTP/1.1", line)
        } finally {
            server.close()
        }
    }

    @Test fun `the platform is one the server knows`() {
        // An unknown name is a 422 by design, so a typo here would break every
        // announcement fetch rather than silently narrowing one.
        assertTrue(ANNOUNCEMENT_PLATFORM in listOf("web", "android", "ios", "watch"))
    }
}
