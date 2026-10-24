package systems.lupine.sheaf.data.api

import systems.lupine.sheaf.data.model.GroupUpdate
import systems.lupine.sheaf.data.model.MemberUpdate
import systems.lupine.sheaf.data.model.NotificationChannelUpdate
import systems.lupine.sheaf.data.model.QuietHoursSpec
import systems.lupine.sheaf.data.model.SystemUpdate
import systems.lupine.sheaf.di.NetworkModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * These PATCH bodies are read with `exclude_unset`, so an omitted field means
 * "leave it alone" and a null means "clear it". Moshi drops nulls, which left
 * no way to clear anything: emptying a member's pronouns silently kept the old
 * value.
 *
 * The split matters in both directions. A field that should clear but is
 * omitted fails quietly, which is the bug. A field the server rejects an
 * explicit null for fails the entire save if a null is sent, which would be a
 * louder bug introduced by an over-broad fix.
 *
 * Built through the app's own Moshi, so a missing registration fails here
 * rather than only in the field.
 */
class PatchJsonAdaptersTest {

    private val moshi = NetworkModule.provideMoshi()

    private fun member(u: MemberUpdate) = moshi.adapter(MemberUpdate::class.java).toJson(u)
    private fun group(u: GroupUpdate) = moshi.adapter(GroupUpdate::class.java).toJson(u)
    private fun system(u: SystemUpdate) = moshi.adapter(SystemUpdate::class.java).toJson(u)
    private fun channel(u: NotificationChannelUpdate) =
        moshi.adapter(NotificationChannelUpdate::class.java).toJson(u)

    // ── Member ────────────────────────────────────────────────────────────────

    @Test fun `emptying a member's optional fields clears them`() {
        val json = member(MemberUpdate(name = "Alex", clearsUnsetFields = true))
        listOf("display_name", "description", "pronouns", "color", "birthday", "note", "emoji")
            .forEach { assertTrue("\"$it\":null" in json, "$it should clear, got: $json") }
    }

    @Test fun `removing a member's emoji removes it`() {
        // This one needed an empty string before the adapter existed, because a
        // dropped null read as "leave it alone" and the old emoji stayed put.
        assertTrue("\"emoji\":null" in member(MemberUpdate(name = "Alex", emoji = null, clearsUnsetFields = true)))
        val wolf = "\uD83D\uDC3A"
        assertTrue("\"emoji\":\"$wolf\"" in member(MemberUpdate(emoji = wolf)))
    }

    @Test fun `a member's NOT NULL fields are omitted rather than nulled`() {
        // Sending these as null fails the whole save server-side.
        val json = member(MemberUpdate(displayName = "D"))
        assertFalse("\"name\"" in json, json)
        assertFalse("\"privacy\"" in json, json)
    }

    @Test fun `a member's set values still serialise`() {
        val json = member(MemberUpdate(name = "Alex", pronouns = "they/them"))
        assertTrue("\"name\":\"Alex\"" in json, json)
        assertTrue("\"pronouns\":\"they/them\"" in json, json)
    }

    @Test fun `clearing an avatar reaches the wire`() {
        // Removing a picture is a clear, not an omission.
        assertTrue("\"avatar_url\":null" in member(MemberUpdate(name = "Alex", clearsUnsetFields = true)))
        assertTrue("\"banner_url\":null" in member(MemberUpdate(name = "Alex", clearsUnsetFields = true)))
    }

    // ── Group ─────────────────────────────────────────────────────────────────

    @Test fun `a group's description and colour clear`() {
        val json = group(GroupUpdate(name = "G", clearsUnsetFields = true))
        assertTrue("\"description\":null" in json, json)
        assertTrue("\"color\":null" in json, json)
    }

    @Test fun `clearing a group's parent promotes it to top level`() {
        assertTrue("\"parent_id\":null" in group(GroupUpdate(name = "G", clearsUnsetFields = true)))
    }

    @Test fun `a group's name is omitted when null`() {
        assertFalse("\"name\"" in group(GroupUpdate(description = "d")))
    }

    // ── System ────────────────────────────────────────────────────────────────

    @Test fun `a system's tag, colour and description clear`() {
        val json = system(SystemUpdate(name = "S", clearsUnsetFields = true))
        listOf("description", "tag", "color", "note")
            .forEach { assertTrue("\"$it\":null" in json, "$it should clear, got: $json") }
    }

    @Test fun `a system's NOT NULL fields are omitted rather than nulled`() {
        val json = system(SystemUpdate(tag = "t"))
        assertFalse("\"name\"" in json, json)
        assertFalse("\"privacy\"" in json, json)
        // Absent from the body, not sent as null: the column is NOT NULL, and
        // the toggle only ever ships an actual value.
        assertFalse("\"show_member_created_date\"" in json, json)
    }

    @Test fun `a system's created-date toggle serialises both ways when set`() {
        assertTrue("\"show_member_created_date\":true" in system(SystemUpdate(showMemberCreatedDate = true)))
        assertTrue("\"show_member_created_date\":false" in system(SystemUpdate(showMemberCreatedDate = false)))
    }

    // ── Every field reaches the wire ──────────────────────────────────────────

    // A hand-written adapter only writes what it lists, so a field left off the
    // list is dropped without a sound. That is how the ceilings and the step-up
    // credentials went missing: re-auth never reached the server and the guard
    // switches never saved.

    private fun assertAllKeys(json: String, keys: List<String>) =
        keys.forEach { assertTrue("\"$it\":" in json, "$it missing from: $json") }

    @Test fun `every member field is written when set`() {
        val json = member(
            MemberUpdate(
                name = "A", displayName = "D", description = "d", pronouns = "p",
                avatarUrl = "a", bannerUrl = "b", color = "#fff", birthday = "2000-01-01",
                privacy = "public", note = "n", emoji = "x",
                neverShareable = false, frontingPrivate = false,
                password = "pw", totpCode = "123456",
            ),
        )
        assertAllKeys(
            json,
            listOf(
                "name", "display_name", "description", "pronouns", "avatar_url", "banner_url",
                "color", "birthday", "privacy", "note", "emoji",
                "never_shareable", "fronting_private", "password", "totp_code",
            ),
        )
        assertTrue("\"never_shareable\":false" in json, json)
    }

    @Test fun `every group field is written when set`() {
        val json = group(
            GroupUpdate(
                name = "G", description = "d", color = "#fff", parentId = "p",
                privacy = "public", password = "pw", totpCode = "123456",
            ),
        )
        assertAllKeys(
            json,
            listOf("name", "description", "color", "parent_id", "privacy", "password", "totp_code"),
        )
    }

    @Test fun `every system field is written when set`() {
        val json = system(
            SystemUpdate(
                name = "S", description = "d", tag = "t", avatarUrl = "a", color = "#fff",
                privacy = "public", note = "n", showMemberCreatedDate = true,
                password = "pw", totpCode = "123456",
            ),
        )
        assertAllKeys(
            json,
            listOf(
                "name", "description", "tag", "avatar_url", "color", "privacy", "note",
                "show_member_created_date", "password", "totp_code",
            ),
        )
    }

    @Test fun `unset ceilings, credentials and privacy are left out`() {
        // All NOT NULL or step-up only: a null would fail the save or be noise.
        listOf(member(MemberUpdate(name = "A")), group(GroupUpdate(name = "G", clearsUnsetFields = true)), system(SystemUpdate(name = "S", clearsUnsetFields = true)))
            .forEach { json ->
                listOf("never_shareable", "fronting_private", "privacy", "password", "totp_code")
                    .forEach { assertFalse("\"$it\"" in json, "$it should be omitted: $json") }
            }
    }

    @Test fun `the bodies stay valid JSON objects`() {
        listOf(member(MemberUpdate(name = "A")), group(GroupUpdate(name = "G", clearsUnsetFields = true)), system(SystemUpdate(name = "S", clearsUnsetFields = true)))
            .forEach {
                assertTrue(it.startsWith("{") && it.endsWith("}"), it)
                assertEquals(it.count { c -> c == '{' }, it.count { c -> c == '}' })
            }
    }

    @Test fun `the front preferences go out under their wire names`() {
        // Both are NOT NULL server-side, so they omit rather than clear, and
        // both are snake_case: without the mapping the server sees an unknown
        // key, keeps its value, and the switch appears to do nothing.
        val on = system(SystemUpdate(replaceFrontsDefault = true))
        assertTrue(""""replace_fronts_default":true""" in on, on)
        val off = system(SystemUpdate(coalesceContiguousFronts = false))
        assertTrue(""""coalesce_contiguous_fronts":false""" in off, off)
    }

    @Test fun `turning a front preference off is sent, not dropped`() {
        // False is a value, not an absence. Toggling one off has to reach the
        // wire or it silently stays on.
        val json = system(SystemUpdate(replaceFrontsDefault = false))
        assertTrue(""""replace_fronts_default":false""" in json, json)
    }

    @Test fun `a front preference left alone is omitted entirely`() {
        // Saving one must not restate the other: an explicit null on either
        // is rejected server-side, and restating a stale value would undo a
        // change made from another client between load and save.
        val json = system(SystemUpdate(coalesceContiguousFronts = true))
        assertFalse("replace_fronts_default" in json, json)
    }

    // ── A partial body must not clear what it did not send ────────────────────

    // The other half of the contract, and the one that went wrong in the field.
    // `clears` writes a null whether or not the caller populated the field, so a
    // screen that sends one setting was destroying the rest of the record.

    @Test fun `toggling a front preference sends only that preference`() {
        // Settings, Fronting sends one boolean. It used to arrive alongside six
        // nulls, wiping the system's description, tag, colour, note, avatar and
        // banner every time either switch was flipped.
        val json = system(SystemUpdate(replaceFrontsDefault = true))
        assertEquals("""{"replace_fronts_default":true}""", json)
    }

    @Test fun `no clearable field escapes a partial system body`() {
        val json = system(SystemUpdate(coalesceContiguousFronts = false))
        listOf("description", "tag", "avatar_url", "banner_url", "color", "note")
            .forEach { assertFalse(it in json, "$it must not be sent, got: $json") }
    }

    @Test fun `a partial member or group body clears nothing either`() {
        // Neither has a partial caller today. Both would be one rename away
        // from losing an avatar, a birthday or a parent group.
        val member = member(MemberUpdate(name = "Alex"))
        assertEquals("""{"name":"Alex"}""", member)
        val group = group(GroupUpdate(name = "G"))
        assertEquals("""{"name":"G"}""", group)
    }

    @Test fun `a partial body still sends the values it does carry`() {
        val json = member(MemberUpdate(name = "Alex", pronouns = "they/them"))
        assertTrue(""""pronouns":"they/them"""" in json, json)
        assertFalse("avatar_url" in json, json)
    }

    // ── Notification channel ──────────────────────────────────────────────────

    @Test fun `turning quiet hours off clears them`() {
        // The bug this adapter exists for: unticking the box reported success
        // and left the window in place, because the null was dropped and the
        // server reads presence rather than value.
        val json = channel(
            NotificationChannelUpdate(quietHoursSet = true, quietHours = null),
        )
        assertEquals("""{"quiet_hours":null}""", json)
    }

    @Test fun `setting quiet hours sends the window`() {
        val json = channel(
            NotificationChannelUpdate(
                quietHoursSet = true,
                quietHours = QuietHoursSpec("22:00", "07:00", "UTC"),
            ),
        )
        assertTrue(""""start":"22:00"""" in json, json)
        assertTrue(""""tz":"UTC"""" in json, json)
    }

    @Test fun `editing something else does not touch quiet hours`() {
        // Every channel save would otherwise clear the window, since the
        // clearing field is written whether or not it was edited.
        val json = channel(NotificationChannelUpdate(name = "Phone"))
        assertFalse("quiet_hours" in json, json)
    }

    @Test fun `a secret or a rule set is never cleared by omission`() {
        // These three are absent from the server's _reject_explicit_null list,
        // so a null would be accepted rather than refused: sending one would
        // wipe a webhook secret or replace every rule with nothing.
        val json = channel(NotificationChannelUpdate(quietHoursSet = true, quietHours = null))
        listOf("webhook_secret", "group_rules", "member_rules")
            .forEach { assertFalse(it in json, "$it must not be sent, got: $json") }
    }

    @Test fun `a channel toggle turned off is sent, not dropped`() {
        val json = channel(NotificationChannelUpdate(triggerOnStart = false))
        assertTrue(""""trigger_on_start":false""" in json, json)
    }

    @Test fun `a zero debounce is a value, not an absence`() {
        // Zero means "deliver each change on its own", which is a real choice
        // and the one somebody picks to turn batching off.
        val json = channel(NotificationChannelUpdate(debounceSeconds = 0))
        assertTrue(""""debounce_seconds":0""" in json, json)
    }
}
