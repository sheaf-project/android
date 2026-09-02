package systems.lupine.sheaf.data.model

import com.squareup.moshi.Moshi
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Wire-contract details that a refactor can quietly break without any compiler
 * or runtime complaint, because the server's response to a wrong body is
 * "fine, nothing changed".
 */
class ModelContractsTest {

    private val moshi = Moshi.Builder().build()

    @Test fun `an explicit null timezone reaches the wire`() {
        // "Automatic" is an explicit null, and the backend patches with
        // exclude_unset: an omitted field means "leave unchanged". Without
        // serializeNulls, Moshi drops the null and choosing Automatic becomes a
        // no-op that silently keeps the old zone.
        val json = moshi.adapter(SystemTimezoneBody::class.java)
            .serializeNulls()
            .toJson(SystemTimezoneBody(null))
        assertEquals("""{"timezone":null}""", json)
    }

    @Test fun `a chosen timezone serialises normally`() {
        val json = moshi.adapter(SystemTimezoneBody::class.java)
            .serializeNulls()
            .toJson(SystemTimezoneBody("Europe/London"))
        assertEquals("""{"timezone":"Europe/London"}""", json)
    }

    @Test fun `without serializeNulls the null would vanish`() {
        // Pinning the trap itself, so the next person to touch this sees why
        // the .serializeNulls() call at the call site is load-bearing.
        val json = moshi.adapter(SystemTimezoneBody::class.java)
            .toJson(SystemTimezoneBody(null))
        assertEquals("{}", json)
    }

    // ── MemberRead derived display fields ───────────────────────────────────

    private fun member(name: String, displayName: String? = null) = MemberRead(
        id = "m1",
        systemId = "s1",
        name = name,
        displayName = displayName,
        description = null,
        pronouns = null,
        avatarUrl = null,
        color = null,
        birthday = null,
        privacy = "private",
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
    )

    @Test fun `display name wins over name when set`() {
        assertEquals("Ash", member(name = "ashley", displayName = "Ash").displayNameOrName)
    }

    @Test fun `a blank display name falls back to the name`() {
        assertEquals("ashley", member(name = "ashley", displayName = "   ").displayNameOrName)
    }

    @Test fun `initials take the first two words`() {
        assertEquals("AB", member("Alex Bell").initials)
        assertEquals("AB", member("Alex Bell Carter").initials)
        assertEquals("A", member("Alex").initials)
    }

    @Test fun `initials come from the display name`() {
        assertEquals("SR", member(name = "ashley", displayName = "Sam Rivers").initials)
    }

    @Test fun `initials never come out empty`() {
        // Rendered into every avatar placeholder on phone and widgets, so an
        // empty string here is a blank circle rather than a crash.
        assertEquals("?", member(" ").initials)
        assertEquals("?", member("").initials)
    }

    @Test fun `initials uppercase`() {
        assertEquals("AB", member("alex bell").initials)
    }

    @Test fun `the display name carries the emoji in front`() {
        assertEquals("\uD83E\uDD8A Alex", member("Alex").copy(emoji = "\uD83E\uDD8A").displayNameWithEmoji)
    }

    @Test fun `no emoji leaves the name untouched`() {
        assertEquals("Alex", member("Alex").displayNameWithEmoji)
        assertEquals("Alex", member("Alex").copy(emoji = "").displayNameWithEmoji)
        assertEquals("Alex", member("Alex").copy(emoji = "   ").displayNameWithEmoji)
    }

    @Test fun `the emoji form respects the display name`() {
        val m = member(name = "ashley", displayName = "Sam Rivers").copy(emoji = "\u2728")
        assertEquals("\u2728 Sam Rivers", m.displayNameWithEmoji)
    }

    @Test fun `the plain name stays free of the emoji`() {
        // Sorting, searching and content descriptions use displayNameOrName.
        // An emoji leaking in would file the roster under one character and
        // stop a name query matching.
        val m = member("Alex").copy(emoji = "\uD83E\uDD8A")
        assertEquals("Alex", m.displayNameOrName)
        assertEquals("A", m.initials)
    }

    @Test fun `an emoji reaches the wire on create and update`() {
        val create = moshi.adapter(MemberCreate::class.java)
            .toJson(MemberCreate(name = "Alex", emoji = "\u2728"))
        assertEquals(true, "\"emoji\"" in create)
    }

    @Test fun `clearing an emoji sends an empty string, not an omission`() {
        // The member PATCH is omit-means-unchanged, and Moshi drops nulls, so
        // a null emoji would silently leave the old one in place. The empty
        // string is what actually clears it.
        val json = moshi.adapter(MemberUpdate::class.java)
            .toJson(MemberUpdate(name = "Alex", emoji = ""))
        assertEquals(true, "\"emoji\":\"\"" in json)
    }

    @Test fun `archived is derived from the timestamp`() {
        val active = member("Alex")
        assertEquals(false, active.isArchived)
        assertEquals(true, active.copy(archivedAt = "2026-01-02T00:00:00Z").isArchived)
    }

    @Test fun `show_member_created_date round-trips under its wire name`() {
        // A camelCase slip here wouldn't fail anything loudly: the toggle would
        // just never stick, because the server ignores unknown keys on PATCH and
        // reports its unchanged value back.
        val adapter = moshi.adapter(SystemUpdate::class.java)
        assertEquals(
            """{"show_member_created_date":true}""",
            adapter.toJson(SystemUpdate(showMemberCreatedDate = true)),
        )
        val read = moshi.adapter(SystemRead::class.java).fromJson(
            """
            {"id":"s1","name":"Sys","description":null,"tag":null,"avatar_url":null,
             "color":null,"privacy":"private","delete_confirmation":null,
             "show_member_created_date":true,
             "created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T00:00:00Z"}
            """.trimIndent(),
        )
        assertEquals(true, read?.showMemberCreatedDate)
    }

    @Test fun `an omitted show_member_created_date reads as off`() {
        // Older cached payloads and older servers won't carry the field; the
        // display has to default to off rather than blow up or leak the date.
        val read = moshi.adapter(SystemRead::class.java).fromJson(
            """
            {"id":"s1","name":"Sys","description":null,"tag":null,"avatar_url":null,
             "color":null,"privacy":"private","delete_confirmation":null,
             "created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T00:00:00Z"}
            """.trimIndent(),
        )
        assertEquals(false, read?.showMemberCreatedDate)
    }

    @Test fun `a false toggle is still sent rather than omitted`() {
        // Turning the setting back off has to reach the wire. Moshi drops nulls,
        // so the form's Boolean must be non-null false, not null.
        assertEquals(
            """{"show_member_created_date":false}""",
            moshi.adapter(SystemUpdate::class.java).toJson(SystemUpdate(showMemberCreatedDate = false)),
        )
    }
}
