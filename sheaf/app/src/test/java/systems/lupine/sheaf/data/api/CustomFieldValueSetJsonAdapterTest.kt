package systems.lupine.sheaf.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import systems.lupine.sheaf.data.model.CustomFieldValueSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Clearing a populated custom field is expressed as `value: null`, and the
 * server requires the key to be present: an entry without it is rejected
 * outright, not treated as a smaller change. Moshi drops null fields by
 * default, so this is the difference between clearing working and failing.
 */
class CustomFieldValueSetJsonAdapterTest {

    private val moshi = Moshi.Builder()
        .add(CustomFieldValueSetJsonAdapter.FACTORY)
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val listType =
        Types.newParameterizedType(List::class.java, CustomFieldValueSet::class.java)
    private val adapter = moshi.adapter<List<CustomFieldValueSet>>(listType)

    private fun json(vararg items: CustomFieldValueSet) = adapter.toJson(items.toList())

    @Test fun `clearing a field sends an explicit null`() {
        assertEquals(
            """[{"field_id":"f1","value":null}]""",
            json(CustomFieldValueSet("f1", null)),
        )
    }

    @Test fun `the value key is never dropped`() {
        // The regression this exists for: without the adapter this serialised
        // to {"field_id":"f1"} and the server rejected the whole request.
        assertTrue("\"value\"" in json(CustomFieldValueSet("f1", null)))
    }

    @Test fun `a set value still serialises normally`() {
        assertEquals(
            """[{"field_id":"f1","value":"hello"}]""",
            json(CustomFieldValueSet("f1", "hello")),
        )
    }

    @Test fun `non-string value types survive`() {
        // Fields are type-erased on the wire; numbers, booleans and the list
        // a multiselect carries all go through the same path.
        assertTrue("\"value\":true" in json(CustomFieldValueSet("f1", true)))
        assertTrue("\"value\":[" in json(CustomFieldValueSet("f1", listOf("a", "b"))))
    }

    @Test fun `a mixed batch clears one field while setting another`() {
        // What a real save looks like when someone empties one field and edits
        // another in the same edit.
        assertEquals(
            """[{"field_id":"f1","value":null},{"field_id":"f2","value":"kept"}]""",
            json(CustomFieldValueSet("f1", null), CustomFieldValueSet("f2", "kept")),
        )
    }

    @Test fun `the app's own Moshi has the adapter registered`() {
        // The tests above prove the adapter; this proves it is actually wired
        // into the instance the app uses. Forgetting the registration would
        // leave every other test here passing while clearing stayed broken.
        val appMoshi = systems.lupine.sheaf.di.NetworkModule.provideMoshi()
        val appAdapter = appMoshi.adapter<List<CustomFieldValueSet>>(listType)
        assertEquals(
            """[{"field_id":"f1","value":null}]""",
            appAdapter.toJson(listOf(CustomFieldValueSet("f1", null))),
        )
    }

    @Test fun `round-trips back through fromJson`() {
        val parsed = adapter.fromJson("""[{"field_id":"f1","value":null}]""")
        assertEquals(1, parsed?.size)
        assertEquals("f1", parsed?.first()?.fieldId)
        assertEquals(null, parsed?.first()?.value)
    }
}
