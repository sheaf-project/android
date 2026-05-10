package systems.lupine.sheaf.push

/**
 * Provides the device's push transport token. Flavour-specific: the .play
 * impl talks to FCM; the .open impl returns null until UnifiedPush lands.
 *
 * Returns null when no token is available (provider not configured,
 * Google Play Services missing, Firebase not initialised, etc). Callers
 * must tolerate null and avoid blowing up: the right behaviour is "this
 * device just doesn't have push working today".
 */
interface PushTokenProvider {
    val platform: PushPlatform
    suspend fun getToken(): String?
}

enum class PushPlatform(val wireValue: String) {
    FCM("fcm"),
    NONE("none"),
}
