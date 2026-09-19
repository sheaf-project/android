package systems.lupine.sheaf.ui.sharing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/**
 * Three different 403s come back from a raise and each wants a different thing
 * from the UI. Re-auth clears exactly one of them, so mixing them up either
 * sends the user in circles typing a password that cannot help, or swallows a
 * genuine credential prompt.
 */
class ShareErrorsTest {

    private fun error(code: Int, detail: String?): HttpException {
        val body = if (detail == null) "" else """{"detail":${quote(detail)}}"""
        return HttpException(
            Response.error<Any>(
                body.toResponseBody("application/json".toMediaTypeOrNull()),
                okhttp3.Response.Builder()
                    .code(code)
                    .message("error")
                    .protocol(Protocol.HTTP_1_1)
                    .request(Request.Builder().url("https://example.invalid/v1/share-grants").build())
                    .build(),
            ),
        )
    }

    private fun quote(s: String) = "\"" + s.replace("\"", "\\\"") + "\""

    @Test
    fun `missing password is a step-up prompt, not a failure`() {
        assertIs<ShareError.StepUpRequired>(
            error(400, "Password required").toShareError("fallback"),
        )
    }

    @Test
    fun `missing totp is a step-up prompt`() {
        assertIs<ShareError.StepUpRequired>(
            error(400, "TOTP code required").toShareError("fallback"),
        )
    }

    @Test
    fun `wrong password keeps the sheet open with the reason`() {
        val err = error(403, "Incorrect password").toShareError("fallback")
        assertIs<ShareError.BadCredentials>(err)
        assertEquals("Incorrect password", err.message)
    }

    @Test
    fun `instance switched off is not a credentials problem`() {
        val detail = "Public profiles and share links are turned off on this instance, so " +
            "nothing new can be published and nothing can be set to show more."
        assertIs<ShareError.PublishingUnavailable>(error(403, detail).toShareError("fallback"))
    }

    @Test
    fun `missing adult attestation is its own state`() {
        assertIs<ShareError.AttestationRequired>(
            error(403, "Confirm you are 18 or older before creating a share link or public profile.")
                .toShareError("fallback"),
        )
    }

    @Test
    fun `pending deletion comes back as 409`() {
        assertIs<ShareError.PendingDeletion>(error(409, null).toShareError("fallback"))
    }

    @Test
    fun `an ordinary 400 is not mistaken for a step-up`() {
        val err = error(400, "Send exposure raises and lowerings as separate requests")
            .toShareError("fallback")
        assertIs<ShareError.Other>(err)
    }

    @Test
    fun `raise detection only fires on the way up`() {
        kotlin.test.assertTrue(isRaiseToPublic("private", "public"))
        kotlin.test.assertTrue(isRaiseToPublic("friends", "public"))
        kotlin.test.assertTrue(isRaiseToPublic(null, "public"))
        kotlin.test.assertFalse(isRaiseToPublic("public", "public"))
        kotlin.test.assertFalse(isRaiseToPublic("public", "private"))
        kotlin.test.assertFalse(isRaiseToPublic("private", "friends"))
    }

    @Test
    fun `step up is pointless at tier none`() {
        kotlin.test.assertFalse(RaiseGate(authTier = "none", armed = true).stepUpNeeded)
        kotlin.test.assertTrue(RaiseGate(authTier = "password", armed = true).stepUpNeeded)
        kotlin.test.assertFalse(RaiseGate(authTier = "password", armed = false).stepUpNeeded)
    }
}
