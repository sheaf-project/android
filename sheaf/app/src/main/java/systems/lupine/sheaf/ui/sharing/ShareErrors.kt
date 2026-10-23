package systems.lupine.sheaf.ui.sharing

import retrofit2.HttpException
import systems.lupine.sheaf.util.errorDetail
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
        // Not one of the states above, so it is an ordinary failure being
        // reported as a message, and it goes through the same formatter as
        // every other one. preferDetail keeps what this branch already did
        // (these endpoints answer with prose worth reading) while letting the
        // technical register add the status, which it could not before: the
        // preference used to do nothing on any screen that classified its
        // errors through here, so the same setting said different things
        // depending on which call had failed.
        //
        // The classified states above keep their own wording. They are states
        // with their own UI, not messages, and a status code in the step-up
        // sheet's "incorrect password" line would read as a fault rather than
        // as a prompt.
        else -> ShareError.Other(toUserMessage(fallback, preferDetail = true))
    }
}

// The detail reader moved to util/ApiErrorDetail.kt, which peeks the body
// instead of consuming it. That retires the race this file used to work
// around: toUserMessage now reads the detail too, for the technical-errors
// preference, and two readers on one exception is the normal case rather than
// something to avoid.

// The per-status wording this file used to carry has gone: toUserMessage
// already maps every one of those codes, and keeping a second table meant the
// same status could read two ways depending on which screen asked.

fun ShareError.message(fallback: String): String = when (this) {
    is ShareError.StepUpRequired -> fallback
    is ShareError.BadCredentials -> message
    is ShareError.AttestationRequired -> "Confirm you are 18 or older before publishing."
    is ShareError.PublishingUnavailable -> message
    is ShareError.PendingDeletion -> message
    is ShareError.Other -> message
}
