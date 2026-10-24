package systems.lupine.sheaf.ui.passkeys

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Rule
import retrofit2.HttpException
import retrofit2.Response
import systems.lupine.sheaf.MainDispatcherRule
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.AuthConfig
import systems.lupine.sheaf.data.model.PasskeyOptions
import systems.lupine.sheaf.data.model.PasskeyRead
import systems.lupine.sheaf.data.model.PasskeyRegisterComplete
import systems.lupine.sheaf.data.model.RawJson
import systems.lupine.sheaf.data.model.UserRead
import systems.lupine.sheaf.passkey.PasskeyAuthenticator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PasskeysViewModelTest {

    @get:Rule val dispatcher = MainDispatcherRule()

    private val activity = mockk<Context>()

    private fun config(available: Boolean) = AuthConfig(
        registrationMode = "open",
        inviteCodesEnabled = false,
        emailVerification = "none",
        emailEnabled = false,
        baseUrl = "https://sheaf.example",
        passkeysAvailable = available,
    )

    private val user = UserRead(
        id = "u1",
        email = "a@example.com",
        totpEnabled = true,
        isAdmin = false,
        tier = "free",
        accountStatus = "active",
        emailVerified = true,
        createdAt = "2026-01-01T00:00:00Z",
        lastLoginAt = null,
    )

    private fun passkey(id: String) = PasskeyRead(
        id = id,
        rpId = "sheaf.example",
        createdAt = "2026-10-01T00:00:00Z",
    )

    private fun api(available: Boolean = true): SheafApiService = mockk {
        coEvery { getAuthConfig() } returns config(available)
        coEvery { listPasskeys() } returns listOf(passkey("p1"))
        coEvery { getMe() } returns user
        coEvery { beginPasskeyRegistration(any()) } returns PasskeyOptions(RawJson("""{"o":1}"""))
    }

    private fun httpError(code: Int, detail: String) = HttpException(
        Response.error<Unit>(code, """{"detail":"$detail"}""".toResponseBody("application/json".toMediaType()))
    )

    @Test fun `an instance without passkeys never lists them`() = runTest {
        val service = api(available = false)
        val vm = PasskeysViewModel(service, mockk())
        advanceUntilIdle()

        assertFalse(vm.state.value.available)
        coVerify(exactly = 0) { service.listPasskeys() }
    }

    @Test fun `a new passkey is sent with the device's credential and joins the list`() = runTest {
        val service = api()
        val body = slot<PasskeyRegisterComplete>()
        coEvery { service.completePasskeyRegistration(capture(body)) } returns passkey("p2")
        val device = mockk<PasskeyAuthenticator> {
            coEvery { create(activity, """{"o":1}""") } returns """{"id":"cred"}"""
        }
        val vm = PasskeysViewModel(service, device)
        advanceUntilIdle()

        vm.addPasskey(activity, "pw", "123456", "  My phone ")
        advanceUntilIdle()

        assertEquals("""{"id":"cred"}""", body.captured.credential.json)
        assertEquals("My phone", body.captured.nickname)
        assertEquals(listOf("p1", "p2"), vm.state.value.passkeys.map { it.id })
        assertEquals(1, vm.state.value.addCompleted)
    }

    @Test fun `backing out of the system sheet is not an error`() = runTest {
        val service = api()
        val device = mockk<PasskeyAuthenticator> { coEvery { create(any(), any()) } returns null }
        val vm = PasskeysViewModel(service, device)
        advanceUntilIdle()

        vm.addPasskey(activity, "pw", "123456", "")
        advanceUntilIdle()

        assertNull(vm.state.value.addError)
        assertFalse(vm.state.value.isAdding)
        coVerify(exactly = 0) { service.completePasskeyRegistration(any()) }
    }

    @Test fun `a wrong password is reported in the dialog`() = runTest {
        val service = api()
        coEvery { service.beginPasskeyRegistration(any()) } throws httpError(401, "Invalid password")
        val vm = PasskeysViewModel(service, mockk())
        advanceUntilIdle()

        vm.addPasskey(activity, "wrong", "123456", "")
        advanceUntilIdle()

        assertEquals("Incorrect password", vm.state.value.addError)
        assertEquals(0, vm.state.value.addCompleted)
    }

    @Test fun `a key already on another account says so in the server's words`() = runTest {
        val service = api()
        coEvery { service.completePasskeyRegistration(any()) } throws
            httpError(409, "That passkey is already registered on an account.")
        val device = mockk<PasskeyAuthenticator> { coEvery { create(any(), any()) } returns "{}" }
        val vm = PasskeysViewModel(service, device)
        advanceUntilIdle()

        vm.addPasskey(activity, "pw", "123456", "")
        advanceUntilIdle()

        assertEquals("That passkey is already registered on an account.", vm.state.value.addError)
    }
}
