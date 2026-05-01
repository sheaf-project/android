package systems.lupine.sheaf.data.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression tests for the system-safety response models. These are codegen-backed
 * (`@JsonClass(generateAdapter = true)`); the adapters are emitted at build time
 * by Moshi's KSP processor. If a field is dropped or its `@Json(name=...)` mapping
 * regresses, parsing here will fail loudly instead of silently breaking the
 * release build (where R8 obfuscation makes the failure mode much more confusing).
 */
class SystemSafetyParsingTest {

    private val moshi = Moshi.Builder()
        // The reflection factory is what the production NetworkModule registers as
        // a fallback for any model lacking codegen. We include it here so a missing
        // @JsonClass annotation doesn't spuriously pass the test on reflection.
        .add(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun `SystemSafetyResponse parses a fully-populated payload`() {
        val json = """
            {
              "settings": {
                "grace_period_days": 7,
                "auth_tier": "totp",
                "applies_to_members": true,
                "applies_to_groups": false,
                "applies_to_tags": false,
                "applies_to_fields": true,
                "applies_to_fronts": true,
                "applies_to_journals": true,
                "applies_to_images": false
              },
              "pending_actions": [
                {
                  "id": "act-1",
                  "action_type": "member_delete",
                  "target_id": "mem-1",
                  "target_label": "Alex",
                  "requested_at": "2026-04-29T20:00:00Z",
                  "requested_by_user_id": "u-1",
                  "finalize_after": "2026-05-06T20:00:00Z",
                  "fronting_member_ids": ["mem-2"],
                  "fronting_member_names": ["Sam"],
                  "status": "pending"
                }
              ],
              "pending_changes": [
                {
                  "id": "chg-1",
                  "requested_at": "2026-04-29T20:01:00Z",
                  "requested_by_user_id": null,
                  "finalize_after": "2026-05-06T20:01:00Z",
                  "changes": {
                    "grace_period_days": 14,
                    "applies_to_journals": false
                  },
                  "status": "pending"
                }
              ]
            }
        """.trimIndent()

        val parsed = moshi.adapter(SystemSafetyResponse::class.java).fromJson(json)
        assertNotNull(parsed)

        assertEquals(7, parsed.settings.gracePeriodDays)
        assertEquals("totp", parsed.settings.authTier)
        assertTrue(parsed.settings.appliesToMembers)
        assertTrue(parsed.settings.appliesToJournals)

        assertEquals(1, parsed.pendingActions.size)
        val action = parsed.pendingActions.single()
        assertEquals("member_delete", action.actionType)
        assertEquals(listOf("mem-2"), action.frontingMemberIds)
        assertEquals(listOf("Sam"), action.frontingMemberNames)

        assertEquals(1, parsed.pendingChanges.size)
        val change = parsed.pendingChanges.single()
        assertNull(change.requestedByUserId)
        // `changes` is an arbitrary JSON object — Moshi decodes numbers to Double.
        assertEquals(14.0, change.changes["grace_period_days"])
        assertEquals(false, change.changes["applies_to_journals"])
    }

    @Test
    fun `SystemSafetyResponse parses an empty pending lists payload`() {
        val json = """
            {
              "settings": {
                "grace_period_days": 0,
                "auth_tier": "password",
                "applies_to_members": false,
                "applies_to_groups": false,
                "applies_to_tags": false,
                "applies_to_fields": false,
                "applies_to_fronts": false,
                "applies_to_journals": false,
                "applies_to_images": false
              },
              "pending_actions": [],
              "pending_changes": []
            }
        """.trimIndent()

        val parsed = moshi.adapter(SystemSafetyResponse::class.java).fromJson(json)
        assertNotNull(parsed)
        assertEquals(0, parsed.settings.gracePeriodDays)
        assertEquals("password", parsed.settings.authTier)
        assertTrue(parsed.pendingActions.isEmpty())
        assertTrue(parsed.pendingChanges.isEmpty())
    }

    @Test
    fun `SystemSafetyUpdateResponse parses with a pending change present`() {
        val json = """
            {
              "settings": {
                "grace_period_days": 7,
                "auth_tier": "totp",
                "applies_to_members": true,
                "applies_to_groups": true,
                "applies_to_tags": true,
                "applies_to_fields": true,
                "applies_to_fronts": true,
                "applies_to_journals": true,
                "applies_to_images": true
              },
              "applied": ["applies_to_groups"],
              "deferred": ["grace_period_days"],
              "pending_change": {
                "id": "chg-2",
                "requested_at": "2026-04-29T21:00:00Z",
                "requested_by_user_id": "u-1",
                "finalize_after": "2026-05-06T21:00:00Z",
                "changes": {"grace_period_days": 30},
                "status": "pending"
              }
            }
        """.trimIndent()

        val parsed = moshi.adapter(SystemSafetyUpdateResponse::class.java).fromJson(json)
        assertNotNull(parsed)
        assertEquals(listOf("applies_to_groups"), parsed.applied)
        assertEquals(listOf("grace_period_days"), parsed.deferred)
        assertNotNull(parsed.pendingChange)
        assertEquals("chg-2", parsed.pendingChange.id)
    }

    @Test
    fun `SystemSafetyUpdateResponse parses without a pending change`() {
        val json = """
            {
              "settings": {
                "grace_period_days": 7,
                "auth_tier": "totp",
                "applies_to_members": true,
                "applies_to_groups": true,
                "applies_to_tags": true,
                "applies_to_fields": true,
                "applies_to_fronts": true,
                "applies_to_journals": true,
                "applies_to_images": true
              },
              "applied": [],
              "deferred": [],
              "pending_change": null
            }
        """.trimIndent()

        val parsed = moshi.adapter(SystemSafetyUpdateResponse::class.java).fromJson(json)
        assertNotNull(parsed)
        assertNull(parsed.pendingChange)
    }
}
