package systems.lupine.sheaf.wear.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores the watch's companion-session credentials (access + refresh tokens,
 * base URL) encrypted at rest via [EncryptedSharedPreferences], backed by a
 * hardware-bound master key in the Android Keystore. Previously these lived
 * in plaintext SharedPreferences, readable from a rooted watch or over adb;
 * the tokens are scoped to a revocable secondary session, but encrypting
 * them keeps a lifted file from yielding a live session.
 *
 * Cross-instance change notification (e.g. [WearDataLayerService] saving
 * phone-pushed creds while [MainActivity] is observing the auth flow on the
 * login screen) can't ride on EncryptedSharedPreferences' own change
 * listener: that callback reports the *encrypted* key name, so a
 * `key == "access_token"` check never matches. Instead a tiny non-secret
 * "signal" prefs holds a monotonic version counter; writers bump it and
 * readers re-derive auth state from the encrypted store when it changes. No
 * secrets ever touch the plaintext signal file.
 */
class WearAuthManager(context: Context) {

    private val appContext = context.applicationContext

    private val secure: SharedPreferences = buildSecurePrefs(appContext)

    // Plain prefs, non-secret: just a bump counter used to fan out
    // "credentials changed" to other in-process instances. EncryptedSP
    // doesn't surface usable keys to OnSharedPreferenceChangeListener, so
    // the listener has to ride on an unencrypted file.
    private val signal: SharedPreferences =
        appContext.getSharedPreferences(SIGNAL_FILE, Context.MODE_PRIVATE)

    init {
        migrateLegacyPlaintextIfNeeded()
    }

    private fun isCredentialed() =
        !secureString("access_token").isNullOrBlank() &&
        !secureString("base_url").isNullOrBlank()

    private val _isAuthenticatedFlow = MutableStateFlow(isCredentialed())
    val isAuthenticatedFlow: StateFlow<Boolean> = _isAuthenticatedFlow.asStateFlow()

    val isAuthenticated: Boolean get() = _isAuthenticatedFlow.value

    // React to credential writes from other instances (e.g. the data-layer
    // service handling a phone push) via the non-secret signal counter.
    private val signalListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CREDS_VERSION) {
                _isAuthenticatedFlow.value = isCredentialed()
            }
        }

    init {
        signal.registerOnSharedPreferenceChangeListener(signalListener)
    }

    val baseUrl: String
        get() = secureString("base_url") ?: ""

    val accessToken: String?
        get() = secureString("access_token")

    val refreshToken: String?
        get() = secureString("refresh_token")

    fun saveCredentials(baseUrl: String, accessToken: String, refreshToken: String) {
        secure.edit()
            .putString("base_url", baseUrl)
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .apply()
        // Applying credentials means we're signed in again, so drop any manual
        // sign-out latch (a phone push or a manual watch login both land here).
        signal.edit().putBoolean(KEY_MANUALLY_SIGNED_OUT, false).apply()
        _isAuthenticatedFlow.value = true
        notifyCredsChanged()
    }

    /**
     * True when the user explicitly signed out on the watch. Latched so the
     * app doesn't silently re-request credentials from the phone or re-apply
     * the still-present cached DataItem on the next start. Cleared by
     * [saveCredentials] (a fresh sign-in) or [clearManualSignOut] (an explicit
     * re-sync).
     */
    val manuallySignedOut: Boolean
        get() = signal.getBoolean(KEY_MANUALLY_SIGNED_OUT, false)

    /** Explicit watch-side sign-out: latch the intent, then clear credentials. */
    fun signOut() {
        signal.edit().putBoolean(KEY_MANUALLY_SIGNED_OUT, true).apply()
        clearCredentials()
    }

    /** Drop the manual sign-out latch, e.g. when the user asks to re-sync. */
    fun clearManualSignOut() {
        signal.edit().putBoolean(KEY_MANUALLY_SIGNED_OUT, false).apply()
    }

    fun clearCredentials() {
        secure.edit()
            .remove("base_url")
            .remove("access_token")
            .remove("refresh_token")
            .apply()
        _isAuthenticatedFlow.value = false
        notifyCredsChanged()
    }

    private fun notifyCredsChanged() {
        signal.edit()
            .putLong(KEY_CREDS_VERSION, signal.getLong(KEY_CREDS_VERSION, 0L) + 1)
            .apply()
    }

    /** Defensive read: a corrupt single entry shouldn't crash the API client. */
    private fun secureString(key: String): String? =
        runCatching { secure.getString(key, null) }.getOrNull()

    /**
     * One-time move of credentials from the old plaintext "wear_auth" file
     * into the encrypted store, then wipe the plaintext copy. Keeps existing
     * users signed in across the upgrade instead of forcing a re-pair.
     */
    private fun migrateLegacyPlaintextIfNeeded() {
        val legacy = appContext.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
        val legacyAccess = legacy.getString("access_token", null)
        val legacyBase = legacy.getString("base_url", null)
        if (legacyAccess.isNullOrBlank() && legacyBase.isNullOrBlank()) return

        // Only copy across if the encrypted store isn't already populated,
        // so we don't clobber newer creds with a stale plaintext leftover.
        if (secureString("access_token").isNullOrBlank()) {
            secure.edit()
                .putString("base_url", legacyBase)
                .putString("access_token", legacyAccess)
                .putString("refresh_token", legacy.getString("refresh_token", null))
                .apply()
            Log.i(TAG, "migrated legacy plaintext credentials into encrypted store")
        }
        // Scrub the plaintext file regardless; its contents must not linger.
        legacy.edit().clear().apply()
        runCatching { appContext.deleteSharedPreferences(LEGACY_FILE) }
    }

    private fun buildSecurePrefs(ctx: Context): SharedPreferences =
        runCatching { createEncrypted(ctx) }
            .getOrElse { first ->
                // The master key / keystore is in a bad state, most often a
                // device-transfer that copied the prefs file but not the
                // hardware-bound key, leaving an undecryptable store. Nuke
                // both and rebuild; worst case the user re-pairs the watch.
                Log.w(TAG, "encrypted prefs init failed, rebuilding", first)
                runCatching { ctx.deleteSharedPreferences(SECURE_FILE) }
                runCatching {
                    val ks = java.security.KeyStore.getInstance(ANDROID_KEYSTORE)
                    ks.load(null)
                    ks.deleteEntry(MASTER_KEY_ALIAS)
                }
                createEncrypted(ctx)
            }

    private fun createEncrypted(ctx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx, MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx,
            SECURE_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private companion object {
        const val TAG = "WearAuthManager"
        const val LEGACY_FILE = "wear_auth"
        const val SECURE_FILE = "wear_auth_secure"
        const val SIGNAL_FILE = "wear_auth_signal"
        const val KEY_CREDS_VERSION = "creds_version"
        const val KEY_MANUALLY_SIGNED_OUT = "manually_signed_out"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        // Dedicated alias rather than MasterKey's default so a rebuild here
        // doesn't disturb any other keystore-backed material.
        const val MASTER_KEY_ALIAS = "sheaf_wear_master_key"
    }
}
