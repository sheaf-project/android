package systems.lupine.sheaf.data.repository

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BaseUrlErrorTest {

    // Release builds: no network-security-config, so the platform default
    // (cleartext blocked) applies to every host.
    @Test fun `release rejects cleartext to a real host`() {
        assertNotNull(baseUrlError("http://example.org", cleartextPermitted = false))
    }

    @Test fun `release rejects cleartext to loopback too`() {
        // Nothing in a release build can reach it, so accepting it would just
        // save an address that fails every request.
        assertNotNull(baseUrlError("http://localhost:8000", cleartextPermitted = false))
    }

    @Test fun `release accepts https`() {
        assertNull(baseUrlError("https://example.org", cleartextPermitted = false))
    }

    @Test fun `bare host is fine - it normalises to https`() {
        assertNull(baseUrlError("example.org", cleartextPermitted = false))
    }

    @Test fun `https with a path prefix is fine`() {
        assertNull(baseUrlError("https://example.org/sheaf", cleartextPermitted = false))
    }

    @Test fun `debug allows cleartext to loopback hosts`() {
        assertNull(baseUrlError("http://localhost:8000", cleartextPermitted = true))
        assertNull(baseUrlError("http://127.0.0.1:8000", cleartextPermitted = true))
        assertNull(baseUrlError("http://10.0.2.2:8000", cleartextPermitted = true))
    }

    @Test fun `debug still rejects cleartext to a non-loopback host`() {
        // The debug network-security-config only whitelists the loopback set,
        // so an http LAN address fails there as well.
        assertNotNull(baseUrlError("http://192.168.1.10:8000", cleartextPermitted = true))
    }

    @Test fun `empty input is not an error - the caller substitutes a default`() {
        assertNull(baseUrlError("", cleartextPermitted = false))
        assertNull(baseUrlError("   ", cleartextPermitted = false))
    }

    @Test fun `unparseable input is rejected`() {
        assertNotNull(baseUrlError("https://", cleartextPermitted = false))
    }
}
