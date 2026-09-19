package systems.lupine.sheaf.ui.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import systems.lupine.sheaf.data.model.SystemSafetySettings

/**
 * The re-auth prompt is decided client-side from the diff, so a category that
 * can be disarmed has to be in `isLoosening` or the save silently skips the
 * dialog and eats a 400 from the server instead.
 */
class SystemSafetyLooseningTest {

    private val armed = SystemSafetySettings(
        gracePeriodDays = 7,
        authTier = "password",
        appliesToMembers = true,
        appliesToGroups = true,
        appliesToTags = true,
        appliesToFields = true,
        appliesToFronts = true,
        appliesToJournals = true,
        appliesToImages = true,
        appliesToRevisions = true,
        appliesToNotifications = true,
        appliesToReminders = true,
        appliesToPolls = true,
        appliesToMessages = true,
        appliesToRelationships = true,
        appliesToArchive = true,
        appliesToProfileVisibility = true,
        autoPinFirstRevision = true,
    )

    @Test
    fun `disarming profile visibility is a loosening`() {
        val draft = armed.copy(appliesToProfileVisibility = false)
        assertTrue(SystemSafetyViewModel.isLoosening(armed, draft))
    }

    @Test
    fun `disarming any other category is a loosening too`() {
        val cases = listOf<Pair<String, SystemSafetySettings>>(
            "notifications" to armed.copy(appliesToNotifications = false),
            "reminders" to armed.copy(appliesToReminders = false),
            "polls" to armed.copy(appliesToPolls = false),
            "messages" to armed.copy(appliesToMessages = false),
            "relationships" to armed.copy(appliesToRelationships = false),
            "archive" to armed.copy(appliesToArchive = false),
        )
        cases.forEach { (name, draft) ->
            assertTrue(
                SystemSafetyViewModel.isLoosening(armed, draft),
                "disarming $name should require re-auth",
            )
        }
    }

    @Test
    fun `arming profile visibility is not a loosening`() {
        val current = armed.copy(appliesToProfileVisibility = false)
        assertFalse(SystemSafetyViewModel.isLoosening(current, armed))
    }
}
