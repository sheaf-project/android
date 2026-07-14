package systems.lupine.sheaf.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Decides whether unpinning a revision demands re-auth. Wrong in one direction
 * it is a bypassable safety gate; wrong in the other it demands a TOTP code
 * from someone who has no authenticator and cannot proceed at all.
 */
class RevisionSafetyTest {

    @Test fun `no grace period means no queue and no re-auth`() {
        val safety = RevisionSafety(
            authTier = "both",
            totpEnabled = true,
            appliesToRevisions = true,
            gracePeriodDays = 0,
        )
        assertFalse(safety.willQueueUnpin)
        assertFalse(safety.needsPassword)
        assertFalse(safety.needsTotp)
    }

    @Test fun `safety that does not apply to revisions gates nothing`() {
        val safety = RevisionSafety(
            authTier = "both",
            totpEnabled = true,
            appliesToRevisions = false,
            gracePeriodDays = 7,
        )
        assertFalse(safety.willQueueUnpin)
        assertFalse(safety.needsPassword)
        assertFalse(safety.needsTotp)
    }

    @Test fun `password tier asks for a password only`() {
        val safety = RevisionSafety("password", totpEnabled = true, appliesToRevisions = true, gracePeriodDays = 7)
        assertTrue(safety.willQueueUnpin)
        assertTrue(safety.needsPassword)
        assertFalse(safety.needsTotp)
    }

    @Test fun `totp tier asks for a code only`() {
        val safety = RevisionSafety("totp", totpEnabled = true, appliesToRevisions = true, gracePeriodDays = 7)
        assertFalse(safety.needsPassword)
        assertTrue(safety.needsTotp)
    }

    @Test fun `both tier asks for both`() {
        val safety = RevisionSafety("both", totpEnabled = true, appliesToRevisions = true, gracePeriodDays = 7)
        assertTrue(safety.needsPassword)
        assertTrue(safety.needsTotp)
    }

    @Test fun `totp is not demanded from an account without an authenticator`() {
        // Otherwise the dialog's confirm button can never enable and the user is
        // locked out of their own unpin.
        val totpTier = RevisionSafety("totp", totpEnabled = false, appliesToRevisions = true, gracePeriodDays = 7)
        assertFalse(totpTier.needsTotp)

        val bothTier = RevisionSafety("both", totpEnabled = false, appliesToRevisions = true, gracePeriodDays = 7)
        assertTrue(bothTier.needsPassword)
        assertFalse(bothTier.needsTotp)
    }

    @Test fun `tier none queues but asks for nothing`() {
        val safety = RevisionSafety("none", totpEnabled = true, appliesToRevisions = true, gracePeriodDays = 3)
        assertTrue(safety.willQueueUnpin)
        assertFalse(safety.needsPassword)
        assertFalse(safety.needsTotp)
    }

    @Test fun `an unrecognised tier does not silently demand credentials`() {
        val safety = RevisionSafety("magic", totpEnabled = true, appliesToRevisions = true, gracePeriodDays = 3)
        assertFalse(safety.needsPassword)
        assertFalse(safety.needsTotp)
    }

    @Test fun `defaults gate nothing`() {
        val safety = RevisionSafety()
        assertFalse(safety.willQueueUnpin)
        assertFalse(safety.needsPassword)
        assertFalse(safety.needsTotp)
    }
}
