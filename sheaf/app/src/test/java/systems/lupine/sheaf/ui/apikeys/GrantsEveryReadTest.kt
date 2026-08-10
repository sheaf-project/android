package systems.lupine.sheaf.ui.apikeys

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Decides whether picking data export deserves a warning. Getting it wrong in
 * either direction is bad in a quiet way: too eager and people learn to click
 * through the warning, too lax and a key that reads the whole account is handed
 * out without anyone being told.
 */
class GrantsEveryReadTest {

    private val readable = ALL_SCOPE_RESOURCES.filterNot { it.writeOnly }

    private fun allReadable(level: ApiScopeLevel = ApiScopeLevel.READ) =
        readable.associate { it.key to level }

    @Test fun `nothing picked does not count as reading everything`() {
        assertFalse(grantsEveryRead(emptyMap()))
    }

    @Test fun `export alone does not count`() {
        // The case the warning exists for: one narrow-looking pick that in fact
        // reaches the whole account.
        assertFalse(grantsEveryRead(mapOf("export" to ApiScopeLevel.READ)))
    }

    @Test fun `read on every readable resource counts`() {
        assertTrue(grantsEveryRead(allReadable()))
    }

    @Test fun `higher levels also count as read`() {
        // Write and delete imply read server-side, so they satisfy this too.
        assertTrue(grantsEveryRead(allReadable(ApiScopeLevel.WRITE)))
        assertTrue(grantsEveryRead(allReadable(ApiScopeLevel.DELETE)))
    }

    @Test fun `one missing readable resource is enough to warn`() {
        readable.forEach { held ->
            val levels = allReadable() - held.key
            assertFalse(
                grantsEveryRead(levels),
                "withholding ${held.key} should still warrant the warning",
            )
        }
    }

    @Test fun `an explicit none is the same as absent`() {
        val levels = allReadable() + ("members" to ApiScopeLevel.NONE)
        assertFalse(grantsEveryRead(levels))
    }

    @Test fun `write-only resources are not required`() {
        // Data import grants no read, so demanding it would mean a key that
        // genuinely reads everything still got warned at.
        val writeOnly = ALL_SCOPE_RESOURCES.filter { it.writeOnly }
        assertTrue(writeOnly.isNotEmpty(), "expected at least one write-only resource")
        writeOnly.forEach { r ->
            assertTrue(
                r.key !in allReadable().keys,
                "${r.key} is write-only and should not be in the readable set",
            )
        }
        assertTrue(grantsEveryRead(allReadable()))
    }
}
