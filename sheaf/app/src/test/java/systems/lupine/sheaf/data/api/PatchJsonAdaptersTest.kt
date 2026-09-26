package systems.lupine.sheaf.data.api

import systems.lupine.sheaf.data.model.GroupUpdate
import systems.lupine.sheaf.data.model.MemberUpdate
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

    // ── Member ────────────────────────────────────────────────────────────────

    @Test fun `emptying a member's optional fields clears them`() {
        val json = member(MemberUpdate(name = "Alex"))
        listOf("display_name", "description", "pronouns", "color", "birthday", "note", "emoji")
            .forEach { assertTrue("\"$it\":null" in json, "$it should clear, got: $json") }
    }

    @Test fun `removing a member's emoji removes it`() {
        // This one needed an empty string before the adapter existed, because a
        // dropped null read as "leave it alone" and the old emoji stayed put.
        assertTrue("\"emoji\":null" in member(MemberUpdate(name = "Alex", emoji = null)))
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
        assertTrue("\"avatar_url\":null" in member(MemberUpdate(name = "Alex")))
        assertTrue("\"banner_url\":null" in member(MemberUpdate(name = "Alex")))
    }

    // ── Group ─────────────────────────────────────────────────────────────────

    @Test fun `a group's description and colour clear`() {
        val json = group(GroupUpdate(name = "G"))
        assertTrue("\"description\":null" in json, json)
        assertTrue("\"color\":null" in json, json)
    }

    @Test fun `clearing a group's parent promotes it to top level`() {
        assertTrue("\"parent_id\":null" in group(GroupUpdate(name = "G")))
    }

    @Test fun `a group's name is omitted when null`() {
        assertFalse("\"name\"" in group(GroupUpdate(description = "d")))
    }

    // ── System ────────────────────────────────────────────────────────────────

    @Test fun `a system's tag, colour and description clear`() {
        val json = system(SystemUpdate(name = "S"))
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
        listOf(member(MemberUpdate(name = "A")), group(GroupUpdate(name = "G")), system(SystemUpdate(name = "S")))
            .forEach { json ->
                listOf("never_shareable", "fronting_private", "privacy", "password", "totp_code")
                    .forEach { assertFalse("\"$it\"" in json, "$it should be omitted: $json") }
            }
    }

    @Test fun `the bodies stay valid JSON objects`() {
        listOf(member(MemberUpdate(name = "A")), group(GroupUpdate(name = "G")), system(SystemUpdate(name = "S")))
            .forEach {
                assertTrue(it.startsWith("{") && it.endsWith("}"), it)
                assertEquals(it.count { c -> c == '{' }, it.count { c -> c == '}' })
            }
    }
}
