package systems.lupine.sheaf.ui.history

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When deleting a history entry has to ask for credentials first.
 *
 * The delete used to be a bare DELETE with no body, which the server refuses
 * with 400 "Password required" under any auth tier. Confirmed against a live
 * instance with Fronts NOT ticked: the tier guards every deletion on its own,
 * and the ticked category only decides whether the deletion is then held for
 * the grace period. So the prompt keys on the tier, never on the category.
 */
class FrontDeleteSafetyTest {

    @Test fun `no tier means no prompt`() {
        assertFalse(FrontDeleteSafety(authTier = "none").needsCredentials)
    }

    @Test fun `every tier other than none prompts`() {
        for (tier in listOf("password", "totp", "both")) {
            assertTrue(
                FrontDeleteSafety(authTier = tier).needsCredentials,
                "$tier should ask for credentials",
            )
        }
    }

    @Test fun `the ticked category does not decide whether to prompt`() {
        // The trap: Fronts unticked reads as "fronts are not safeguarded", but
        // the server still demands the credential. Keying on the category
        // would send another bare delete straight into a 400.
        assertTrue(
            FrontDeleteSafety(authTier = "password", appliesToFronts = false).needsCredentials,
        )
    }

    @Test fun `a grace period alone does not prompt`() {
        // Grace with tier none queues the deletion (202) without asking for
        // anything, which is a different path entirely.
        assertFalse(
            FrontDeleteSafety(
                authTier = "none",
                appliesToFronts = true,
                gracePeriodDays = 7,
            ).needsCredentials,
        )
    }
}
