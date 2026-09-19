package systems.lupine.sheaf.ui.sharing

import com.squareup.moshi.Moshi
import retrofit2.HttpException
import systems.lupine.sheaf.util.toUserMessage

/**
 * Why an exposing call bounced.
 *
 * Three different 403s can come back from a raise and they want three
 * different responses from the UI, so the detail string is what separates
 * them. Re-auth clears exactly one of them.
 */
sealed interface ShareError {
    /** Server wants credentials it did not get. Open the step-up sheet. */
    data object StepUpRequired : ShareError

    /** Credentials were supplied and rejected. Keep the sheet open. */
    data class BadCredentials(val message: String) : ShareError

    /** The 18+ declaration has not been made yet. */
    data object AttestationRequired : ShareError

    /** Operator has the public surface switched off. Re-auth will not help. */
    data class PublishingUnavailable(val message: String) : ShareError

    /** Account is on its way out. The owner can clear this one themselves. */
    data class PendingDeletion(val message: String) : ShareError

    data class Other(val message: String) : ShareError
}

/**
 * Classify a failed sharing call.
 *
 * The 400 match mirrors web's `isStepUpRequiredError` (api-errors.ts): the
 * server asks for a missing credential with 400 and rejects a wrong one with
 * 403, so "required" and "incorrect" are genuinely different states rather
 * than two spellings of the same one.
 */
fun Throwable.toShareError(fallback: String): ShareError {
    if (this !is HttpException) return ShareError.Other(toUserMessage(fallback))
    val detail = errorDetail()
    return when {
        code() == 400 && (detail == "Password required" || detail == "TOTP code required") ->
            ShareError.StepUpRequired
        code() == 409 -> ShareError.PendingDeletion(
            detail ?: "This account is scheduled for deletion, so nothing new can be published. " +
                "Cancel the deletion first.",
        )
        code() == 403 && detail != null && detail.startsWith("Confirm you are 18") ->
            ShareError.AttestationRequired
        code() == 403 && detail != null && "turned off on this instance" in detail ->
            ShareError.PublishingUnavailable(detail)
        code() == 403 -> ShareError.BadCredentials(detail ?: "Incorrect password or authenticator code")
        else -> ShareError.Other(detail ?: httpFallback(fallback))
    }
}

// Moshi rather than org.json: the classification is the interesting part of
// this file and org.json is an Android framework stub off-device, so parsing
// with it would make these branches untestable on the JVM.
private val errorBodyAdapter by lazy {
    Moshi.Builder().build().adapter<Map<String, Any?>>(
        com.squareup.moshi.Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Any::class.java,
        ),
    )
}

// The body is a one-shot stream, so read it once here rather than letting
// toUserMessage race us for it.
private fun HttpException.errorDetail(): String? = runCatching {
    val body = response()?.errorBody()?.string().orEmpty()
    if (body.isBlank()) return@runCatching null
    (errorBodyAdapter.fromJson(body)?.get("detail") as? String)?.takeIf { it.isNotBlank() }
}.getOrNull()

private fun HttpException.httpFallback(fallback: String): String = when (code()) {
    401 -> "Session expired, please log in again"
    404 -> "Not found"
    429 -> "Too many requests, please wait a moment"
    else -> fallback
}

fun ShareError.message(fallback: String): String = when (this) {
    is ShareError.StepUpRequired -> fallback
    is ShareError.BadCredentials -> message
    is ShareError.AttestationRequired -> "Confirm you are 18 or older before publishing."
    is ShareError.PublishingUnavailable -> message
    is ShareError.PendingDeletion -> message
    is ShareError.Other -> message
}
