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

/**
 * What to show somebody when a call failed.
 *
 * Two registers, chosen by the "Show technical error details" preference
 * ([TechnicalErrors]). Off, which is the default, every failure reads as a
 * short sentence about what to do next. On, an HTTP failure reads
 * `[<status>] <the server's own message>` and anything else names the
 * exception behind it, which is what a bug report needs and what the web app
 * has shown behind the same toggle.
 *
 * [preferDetail] is for the handful of endpoints whose 4xx detail IS the
 * actionable content, written for the account holder about their own settings
 * ("set your system to public first"). The friendly summaries exist because
 * most server details are noise to somebody who did not cause them, but
 * replacing one of these with "Invalid request" throws away the only thing
 * that says what to do next. Use it where the detail names a step the reader
 * can take, not as a general escape hatch. Same meaning as the argument of the
 * same name on the web helper.
 */
fun Throwable.toUserMessage(
    fallback: String = "Something went wrong - please try again",
    preferDetail: Boolean = false,
): String {
    val technical = TechnicalErrors.enabled()
    certificateProblem()?.let { return if (technical) it.withCause(this) else it }
    return when (this) {
        is HttpException -> {
            // Cloudflare answers for the server without being it, so that
            // check stays ahead of the status table in both registers: a 403
            // from the edge is not "access denied" by Sheaf.
            val friendly = if (isCloudflareResponse(errorBodyText())) {
                cloudflareMessage(code())
            } else {
                httpMessage(code(), fallback)
            }
            val detail = errorDetail()
            when {
                // The status is half the value of this mode: "[404] Not found"
                // says which layer answered, where "Not found" alone does not.
                technical -> "[${code()}] ${detail ?: friendly}"
                preferDetail && detail != null -> detail
                else -> friendly
            }
        }
        is UnknownHostException ->
            "Can't reach the server - check your internet connection".orCause(technical, this)
        is ConnectException ->
            "Unable to connect - check your internet connection".orCause(technical, this)
        is SocketTimeoutException ->
            "Connection timed out - please try again".orCause(technical, this)
        is IOException ->
            "Network error - check your connection and try again".orCause(technical, this)
        else -> fallback.orCause(technical, this)
    }
}

/**
 * The exception's own name and message, appended when the technical register
 * is on.
 *
 * There is no status or server message to show for anything that is not an
 * HTTP response, and the friendly line is already more use than
 * `Throwable.message` by itself: "Network error" beats a bare hostname. What a
 * bug report is missing is which exception it actually was, because several
 * distinct causes collapse into one sentence above.
 */
private fun String.orCause(technical: Boolean, error: Throwable): String =
    if (technical) withCause(error) else this

private fun String.withCause(error: Throwable): String {
    val name = error::class.simpleName ?: return this
    val detail = error.message?.takeIf { it.isNotBlank() }
    return if (detail == null) "$this ($name)" else "$this ($name: $detail)"
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
