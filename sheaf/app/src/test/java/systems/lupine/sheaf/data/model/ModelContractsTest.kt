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

    @Test fun `archived is derived from the timestamp`() {
        val active = member("Alex")
        assertEquals(false, active.isArchived)
        assertEquals(true, active.copy(archivedAt = "2026-01-02T00:00:00Z").isArchived)
    }
}
