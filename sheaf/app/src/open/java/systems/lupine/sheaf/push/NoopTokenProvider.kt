package systems.lupine.sheaf.push

import javax.inject.Inject
import javax.inject.Singleton

/**
 * .open flavour stub. The .open distribution ships without Google Play
 * Services / Firebase, so push isn't available via FCM. UnifiedPush is
 * the planned alternative; until that lands, this provider returns
 * null and the device-registration flow stays a no-op.
 */
@Singleton
class NoopTokenProvider @Inject constructor() : PushTokenProvider {
    override val platform: PushPlatform = PushPlatform.NONE
    override suspend fun getToken(): String? = null
}
