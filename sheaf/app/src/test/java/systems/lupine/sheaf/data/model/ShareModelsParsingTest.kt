package systems.lupine.sheaf.data.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Same rationale as SystemSafetyParsingTest: these adapters are codegen-backed,
 * and a dropped @Json name only shows up under R8 in a release build.
 *
 * The pending twins and the served/curated split are the parts worth pinning.
 * Both encode a distinction the UI has to keep straight, and both would parse
 * "successfully" as nulls and zeroes if a name regressed.
 */
class ShareModelsParsingTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Test
    fun `ShareViewRead keeps live flags and their staged twins apart`() {
        val json = """
            {
              "id": "view-1",
              "name": "Friends",
              "include_members": true,
              "include_bio": false,
              "include_fronting": false,
              "fronting_show_count": true,
              "include_relationships": false,
              "include_groups": false,
              "member_permalinks": false,
              "created_at": "2026-09-01T00:00:00Z",
              "is_shared": true,
              "pending_include_bio": true,
              "flags_activate_at": "2026-09-08T00:00:00Z",
              "members": [
                {
                  "id": "row-1",
                  "member_id": "mem-1",
                  "status": "active",
                  "served": false,
                  "not_served_reason": "archived",
                  "added_via_group_id": "grp-1"
                }
              ],
              "fields": [],
              "groups": []
            }
        """.trimIndent()

        val v = moshi.adapter(ShareViewRead::class.java).fromJson(json)
        assertNotNull(v)
        // The live flag is still the truth while a raise is staged.
        assertEquals(false, v.includeBio)
        assertEquals(true, v.pendingIncludeBio)
        assertTrue(v.hasPendingFlags)
        // A flag with nothing staged must read as null, not false.
        assertNull(v.pendingIncludeFronting)

        val row = v.members.single()
        assertEquals(false, row.served)
        assertEquals("archived", row.notServedReason)
        assertEquals("grp-1", row.addedViaGroupId)
    }

    @Test
    fun `a view with nothing staged has no pending twins`() {
        val json = """
            {
              "id": "view-2",
              "name": "Public",
              "include_members": true,
              "include_bio": true,
              "include_fronting": true,
              "fronting_show_count": false,
              "include_relationships": true,
              "include_groups": true,
              "member_permalinks": true,
              "created_at": "2026-09-01T00:00:00Z",
              "is_shared": false,
              "members": [],
              "fields": [],
              "groups": []
            }
        """.trimIndent()

        val v = moshi.adapter(ShareViewRead::class.java).fromJson(json)
        assertNotNull(v)
        assertNull(v.flagsActivateAt)
        assertTrue(!v.hasPendingFlags)
        // A member row defaults to served when the server omits the field.
        assertTrue(v.members.isEmpty())
    }

    @Test
    fun `audit keeps the curated count and the served count separate`() {
        val json = """
            {
              "entries": [
                {
                  "grant": {
                    "id": "g-1",
                    "view_id": "view-1",
                    "subject_type": "link",
                    "note": "for the group chat",
                    "status": "active",
                    "activates_at": null,
                    "expires_at": null,
                    "revoked_at": null,
                    "created_at": "2026-09-01T00:00:00Z"
                  },
                  "view_id": "view-1",
                  "view_name": "Friends",
                  "member_count": 5,
                  "served_member_count": 3,
                  "field_count": 2,
                  "include_members": true,
                  "include_bio": false,
                  "include_fronting": true,
                  "include_relationships": false,
                  "include_groups": false,
                  "member_permalinks": false,
                  "relationship_count": 0,
                  "group_count": 0
                }
              ],
              "profile_suppressed": "system_private"
            }
        """.trimIndent()

        val audit = moshi.adapter(ShareAudit::class.java).fromJson(json)
        assertNotNull(audit)
        val e = audit.entries.single()
        assertEquals(5, e.memberCount)
        assertEquals(3, e.servedMemberCount)
        assertEquals("system_private", audit.profileSuppressed)
    }

    @Test
    fun `a roster that is off makes the served count null rather than zero`() {
        val json = """
            {
              "entries": [
                {
                  "grant": {
                    "id": "g-2",
                    "view_id": "view-3",
                    "subject_type": "public",
                    "note": null,
                    "status": "active",
                    "activates_at": null,
                    "expires_at": null,
                    "revoked_at": null,
                    "created_at": "2026-09-01T00:00:00Z"
                  },
                  "view_id": "view-3",
                  "view_name": "Fronting only",
                  "member_count": 4,
                  "served_member_count": null,
                  "field_count": 0,
                  "include_members": false,
                  "include_bio": false,
                  "include_fronting": true,
                  "include_relationships": false,
                  "include_groups": false,
                  "member_permalinks": false,
                  "relationship_count": 0,
                  "group_count": 0
                }
              ]
            }
        """.trimIndent()

        val audit = moshi.adapter(ShareAudit::class.java).fromJson(json)
        assertNotNull(audit)
        val e = audit.entries.single()
        assertEquals(4, e.memberCount)
        assertNull(e.servedMemberCount)
        assertNull(audit.profileSuppressed)
    }

    @Test
    fun `a link token is carried on the create response only`() {
        val json = """
            {
              "grant": {
                "id": "g-3",
                "view_id": "view-1",
                "subject_type": "link",
                "note": null,
                "status": "pending",
                "activates_at": "2026-09-15T00:00:00Z",
                "expires_at": null,
                "revoked_at": null,
                "created_at": "2026-09-08T00:00:00Z"
              },
              "token": "shr_abc123"
            }
        """.trimIndent()

        val created = moshi.adapter(ShareGrantCreated::class.java).fromJson(json)
        assertNotNull(created)
        assertEquals("shr_abc123", created.token)
        assertEquals("pending", created.grant.status)
    }

    @Test
    fun `a preview distinguishes an unpublished section from an empty one`() {
        val json = """
            {
              "system": {"name": "Example", "member_permalinks": false},
              "members": [],
              "fronting": null
            }
        """.trimIndent()

        val p = moshi.adapter(SharePreview::class.java).fromJson(json)
        assertNotNull(p)
        // Served and empty.
        assertNotNull(p.members)
        assertTrue(p.members.isEmpty())
        // Unaddressable: the anonymous surface would 404 this section.
        assertNull(p.fronting)
        assertNull(p.relationships)
    }
}
