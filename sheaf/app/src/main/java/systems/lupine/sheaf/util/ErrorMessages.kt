package systems.lupine.sheaf.util

import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun Throwable.toUserMessage(fallback: String = "Something went wrong — please try again"): String =
    when (this) {
        is HttpException -> {
            val body = runCatching { response()?.errorBody()?.string() ?: "" }.getOrDefault("")
            if (isCloudflareResponse(body)) {
                cloudflareMessage(code())
            } else {
                httpMessage(code(), fallback)
            }
        }
        is UnknownHostException -> "Can't reach the server — check your internet connection"
        is ConnectException -> "Unable to connect — check your internet connection"
        is SocketTimeoutException -> "Connection timed out — please try again"
        is IOException -> "Network error — check your connection and try again"
        else -> fallback
    }

private fun isCloudflareResponse(body: String): Boolean {
    val lower = body.lowercase()
    return "cloudflare" in lower || "cf-ray" in lower
}

private fun cloudflareMessage(code: Int): String = when (code) {
    403 -> "Access blocked by Cloudflare — check your access credentials in settings"
    else -> "Connection blocked by Cloudflare — please try again later"
}

private fun httpMessage(code: Int, fallback: String): String = when (code) {
    400 -> "Invalid request"
    401 -> "Session expired — please log in again"
    403 -> "Access denied"
    404 -> "Not found"
    408 -> "Request timed out — please try again"
    409 -> fallback
    422 -> "Invalid data provided"
    429 -> "Too many requests — please wait a moment and try again"
    500 -> "Server error — please try again later"
    502 -> "Server unavailable — please try again later"
    503 -> "Service temporarily unavailable — please try again later"
    504 -> "Server timed out — please try again later"
    520 -> "The server returned an unexpected response — please try again"
    521 -> "The server is offline — please try again later"
    522 -> "Connection timed out — the server took too long to respond"
    523 -> "The server is unreachable — please try again later"
    524 -> "The server timed out — please try again later"
    525 -> "SSL configuration error — please contact support"
    526 -> "SSL certificate error — please contact support"
    else -> fallback
}
