package systems.lupine.sheaf.ui.components

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayTimeZoneTest {

    private val ny = ZoneId.of("America/New_York")
    private val london = ZoneId.of("Europe/London")
    private val device = ZoneId.systemDefault()

    @Test fun `no account and no override falls back to the device zone`() {
        assertEquals(device, resolveDisplayZoneId(accountTimezone = null, deviceOverride = null))
    }

    @Test fun `account default is used when there is no override`() {
        assertEquals(ny, resolveDisplayZoneId(accountTimezone = "America/New_York", deviceOverride = null))
    }

    @Test fun `device override wins over the account default`() {
        assertEquals(
            london,
            resolveDisplayZoneId(accountTimezone = "America/New_York", deviceOverride = "Europe/London"),
        )
    }

    @Test fun `auto override pins to the device zone even with a fixed account default`() {
        assertEquals(
            device,
            resolveDisplayZoneId(accountTimezone = "America/New_York", deviceOverride = TZ_AUTO),
        )
    }

    @Test fun `a stored zone that no longer parses falls back to the device zone`() {
        assertEquals(device, resolveDisplayZoneId(accountTimezone = "Mars/Olympus_Mons", deviceOverride = null))
        assertEquals(device, resolveDisplayZoneId(accountTimezone = null, deviceOverride = "Not/AZone"))
    }

    @Test fun `blank override is treated as follow-the-account`() {
        assertEquals(ny, resolveDisplayZoneId(accountTimezone = "America/New_York", deviceOverride = ""))
    }

    @Test fun `common zones map to a friendly label`() {
        assertEquals("Eastern Time (US & Canada)", friendlyZoneLabel("America/New_York"))
        assertEquals("UK / Ireland (London)", friendlyZoneLabel("Europe/London"))
    }

    @Test fun `a non-common zone keeps its raw id as its label`() {
        assertEquals("Europe/Berlin", friendlyZoneLabel("Europe/Berlin"))
    }

    @Test fun `the all-zones list excludes common zones and includes others`() {
        val all = allTimeZoneIds()
        // Common zones live in the Common group only, so they're removed here.
        assert(!all.contains("America/New_York")) { "common zone should be excluded from the full list" }
        assert(all.contains("Europe/Berlin")) { "a non-common zone should be present" }
    }

    @Test fun `every common zone id resolves`() {
        COMMON_ZONES.forEach { assert(isValidTimeZoneId(it.zone)) { "unresolvable common zone: ${it.zone}" } }
    }
}
