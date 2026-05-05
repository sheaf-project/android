package systems.lupine.sheaf.util

import android.util.Base64
import org.json.JSONObject

// Tiny JWT helper. We only ever read claims from tokens the Sheaf server
// minted for us, so signature verification is the server's job; this just
// pulls the payload out for read-only use (e.g. extracting the `sid` claim
// to identify the current session in /v1/auth/sessions, since that endpoint
// detects the current session by cookie and doesn't see our bearer token).
fun decodeJwtPayload(jwt: String): JSONObject? {
    val parts = jwt.split('.')
    if (parts.size < 2) return null
    return try {
        val padded = parts[1].padEnd(parts[1].length + (4 - parts[1].length % 4) % 4, '=')
        val bytes = Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)
        JSONObject(String(bytes, Charsets.UTF_8))
    } catch (_: Throwable) {
        null
    }
}

fun extractSessionId(jwt: String?): String? {
    if (jwt.isNullOrBlank()) return null
    return decodeJwtPayload(jwt)?.optString("sid")?.takeIf { it.isNotBlank() }
}
