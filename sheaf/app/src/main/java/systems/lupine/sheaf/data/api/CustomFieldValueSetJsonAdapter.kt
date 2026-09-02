package systems.lupine.sheaf.data.api

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import systems.lupine.sheaf.data.model.CustomFieldValueSet

/**
 * Hand-written Moshi adapter for [CustomFieldValueSet].
 *
 * `PUT /v1/members/{id}/fields` upserts whatever `value` it is handed, and a
 * null is how a populated field gets cleared: the server stores the null rather
 * than deleting the row. There is no "omit to leave this one alone" mode, so
 * `value` is required on every entry the request carries.
 *
 * Moshi omits null fields by default, which turned a clear into
 * `{"field_id": "..."}` with no `value` at all. That is not a weaker request,
 * it is an invalid one, and clearing a field failed outright.
 *
 * This adapter always writes `value`, null included. Same trap the fronts PATCH
 * hit (see [FrontUpdateJsonAdapter]) from the other side: there the fix was to
 * allow an explicit null through, here it is to stop dropping one.
 */
class CustomFieldValueSetJsonAdapter(moshi: Moshi) : JsonAdapter<CustomFieldValueSet>() {

    // Values are type-erased on the wire (string, number, boolean, or a list
    // for multiselect), so delegate rather than guessing at the shape. Resolved
    // lazily: the factory runs while Moshi is still assembling itself.
    private val anyAdapter: JsonAdapter<Any> by lazy { moshi.adapter(Any::class.java) }

    override fun toJson(writer: JsonWriter, value: CustomFieldValueSet?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginObject()
        writer.name("field_id").value(value.fieldId)
        // Force the null through regardless of the writer's global setting,
        // which is what drops it otherwise.
        val previous = writer.serializeNulls
        writer.serializeNulls = true
        writer.name("value")
        if (value.value == null) writer.nullValue() else anyAdapter.toJson(writer, value.value)
        writer.serializeNulls = previous
        writer.endObject()
    }

    override fun fromJson(reader: JsonReader): CustomFieldValueSet {
        var fieldId: String? = null
        var parsed: Any? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "field_id" -> fieldId = reader.nextString()
                "value" -> parsed =
                    if (reader.peek() == JsonReader.Token.NULL) reader.nextNull<Any>()
                    else anyAdapter.fromJson(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return CustomFieldValueSet(
            fieldId = requireNotNull(fieldId) { "field_id missing" },
            value = parsed,
        )
    }

    companion object {
        /** Registered on the app's Moshi; needs the instance, hence a factory. */
        val FACTORY = Factory { type, _, moshi ->
            if (Types.getRawType(type) == CustomFieldValueSet::class.java) {
                CustomFieldValueSetJsonAdapter(moshi)
            } else {
                null
            }
        }
    }
}
