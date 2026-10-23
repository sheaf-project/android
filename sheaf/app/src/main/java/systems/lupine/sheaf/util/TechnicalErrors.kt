package systems.lupine.sheaf.util

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Whether errors should show their technical detail, readable from anywhere.
 *
 * [Throwable.toUserMessage] is an ordinary function called from view models,
 * repositories and interceptors, often while handling a failure that has
 * nothing to hand but the exception. Threading a preference through every one
 * of those call sites would mean touching most of the app to add one checkbox,
 * and would still leave the paths that format an error before any view model
 * exists.
 *
 * So the resolved value is mirrored here, the same shape the web app uses for
 * the same reason (its api-client is not a React component and cannot read a
 * hook). [PreferencesRepository] owns the stored value; this is a cache of it.
 *
 * Off until something says otherwise, which is also the right answer when
 * nobody is signed in.
 */
object TechnicalErrors {

    private val enabled = AtomicBoolean(false)

    /** True when the user asked to see status codes and server messages. */
    fun enabled(): Boolean = enabled.get()

    /** Called when the stored preference is read or changed. */
    fun set(value: Boolean) {
        enabled.set(value)
    }

    /**
     * Run [block] with the flag forced, then put it back. For tests, which
     * would otherwise leak state between cases through a process-wide object.
     */
    internal fun <T> withEnabled(value: Boolean, block: () -> T): T {
        val previous = enabled.getAndSet(value)
        return try {
            block()
        } finally {
            enabled.set(previous)
        }
    }

    /**
     * Where the account-wide copy lives in the client-settings blob.
     *
     * Nested under `developer` rather than sitting at the top level so the
     * namespace has room for the next preference of this kind, and so it
     * matches the shape the web app writes under its own client id. The blob
     * is per-client, so turning this on in a browser does not turn it on here:
     * a phone is where a status code in a toast is least wanted, and the two
     * clients disagreeing about that is the point rather than a gap.
     */
    const val SETTINGS_GROUP = "developer"
    const val SETTINGS_KEY = "show_technical_errors"

    /**
     * Read the stored value out of a client-settings blob, or null when the
     * account has never set it.
     *
     * Null matters: absent is not the same as false. Absent means "this device
     * decides", so a local preference survives a sync with an account that has
     * never touched the setting, rather than being reset to off on every load.
     */
    fun fromClientSettings(settings: Map<String, Any?>?): Boolean? =
        ((settings?.get(SETTINGS_GROUP) as? Map<*, *>)?.get(SETTINGS_KEY) as? Boolean)

    /** The patch body for writing it back, as one nested group. */
    fun toClientSettings(enabled: Boolean): Map<String, Any> =
        mapOf(SETTINGS_GROUP to mapOf(SETTINGS_KEY to enabled))
}
