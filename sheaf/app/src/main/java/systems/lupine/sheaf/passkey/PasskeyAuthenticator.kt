package systems.lupine.sheaf.passkey

import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.domerrors.InvalidStateError
import androidx.credentials.exceptions.domerrors.SecurityError
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import javax.inject.Inject
import javax.inject.Singleton

/** A passkey ceremony failed for a reason worth showing; [message] is user-facing. */
class PasskeyException(message: String) : Exception(message)

/**
 * The device side of a passkey ceremony, over Credential Manager.
 *
 * Both calls take the server's options JSON as-is and return the credential's
 * JSON as-is, or null when the person backed out of the system sheet, which is
 * not an error. [context] has to be an Activity, since the sheet is shown on it.
 */
@Singleton
class PasskeyAuthenticator @Inject constructor() {

    suspend fun create(context: Context, optionsJson: String): String? {
        val response = try {
            CredentialManager.create(context)
                .createCredential(context, CreatePublicKeyCredentialRequest(optionsJson))
        } catch (e: CreateCredentialCancellationException) {
            return null
        } catch (e: CreateCredentialException) {
            throw PasskeyException(createMessage(e))
        }
        return (response as? CreatePublicKeyCredentialResponse)?.registrationResponseJson
            ?: throw PasskeyException(GENERIC_CREATE)
    }

    suspend fun get(context: Context, optionsJson: String): String? {
        val request = GetCredentialRequest(listOf(GetPublicKeyCredentialOption(optionsJson)))
        val response = try {
            CredentialManager.create(context).getCredential(context, request)
        } catch (e: GetCredentialCancellationException) {
            return null
        } catch (e: GetCredentialException) {
            throw PasskeyException(getMessage(e))
        }
        return (response.credential as? PublicKeyCredential)?.authenticationResponseJson
            ?: throw PasskeyException(GENERIC_GET)
    }

    private fun createMessage(e: CreateCredentialException): String = when {
        e is CreateCredentialProviderConfigurationException -> NO_PROVIDER
        e is CreatePublicKeyCredentialDomException && e.domError is InvalidStateError ->
            "This device already has a passkey for your account"
        e is CreatePublicKeyCredentialDomException && e.domError is SecurityError -> NOT_VOUCHED
        else -> GENERIC_CREATE
    }

    private fun getMessage(e: GetCredentialException): String = when {
        e is NoCredentialException -> "No passkey for this server was found on this device"
        e is GetCredentialProviderConfigurationException -> NO_PROVIDER
        e is GetPublicKeyCredentialDomException && e.domError is SecurityError -> NOT_VOUCHED
        else -> GENERIC_GET
    }

    private companion object {
        const val NO_PROVIDER = "Passkeys aren't supported on this device"
        // The platform checks the server's /.well-known/assetlinks.json before it
        // lets the app use a passkey for that domain. Self-hosted instances only
        // publish one when the operator sets PASSKEY_ANDROID_APPS.
        const val NOT_VOUCHED = "This server hasn't approved this app for passkeys. " +
            "Its operator needs to add the app to PASSKEY_ANDROID_APPS"
        const val GENERIC_CREATE = "Couldn't create a passkey"
        const val GENERIC_GET = "Couldn't use a passkey"
    }
}
