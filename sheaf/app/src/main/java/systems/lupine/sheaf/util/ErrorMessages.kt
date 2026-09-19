package systems.lupine.sheaf.util

import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

fun Throwable.toUserMessage(fallback: String = "Something went wrong — please try again"): String =
    certificateProblem() ?: when (this) {
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

// These are IOExceptions too, so without this they read as "check your
// connection", which sends people debugging their network. Release builds only;
// debug builds accept any certificate.
private fun Throwable.certificateProblem(): String? {
    if (this is SSLPeerUnverifiedException) {
        return "The server's certificate doesn't match this address. Use the address the " +
            "certificate was issued for"
    }
    if (this !is SSLHandshakeException) return null
    val causes = generateSequence(cause) { it.cause }
    return when {
        causes.any { it is CertificateExpiredException || it is CertificateNotYetValidException } ->
            "The server's certificate has expired or isn't valid yet. Check this device's date " +
                "and time, or renew the certificate"
        causes.any { it is CertPathValidatorException || it is CertificateException } ->
            "The server's certificate isn't trusted. Sheaf doesn't use certificates installed " +
                "on this device, so the server needs one from a public certificate authority"
        else -> null
    }
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
