package systems.lupine.sheaf.data.api

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import systems.lupine.sheaf.data.model.FrontUpdate

/**
 * Hand-written Moshi adapter for [FrontUpdate].
 *
 * The fronts PATCH endpoint distinguishes three states for a field on the
 * wire by *presence*:
 *   - omitted   → leave the existing value as-is
 *   - JSON null → clear the value
 *   - a value   → set the value
 *
 * Moshi's default behaviour omits null fields, which only expresses the
 * first two-thirds of that contract — there is no way to emit an explicit
 * null and therefore no way to *clear* a field. That broke "mark still
 * ongoing", which needs to clear `ended_at`.
 *
 * This adapter omits each field when it is null (preserving "leave as-is"
 * for member_ids / started_at / custom_status) but emits an explicit
 * `ended_at: null` when [FrontUpdate.clearEndedAt] is set.
 */
class FrontUpdateJsonAdapter : JsonAdapter<FrontUpdate>() {

    override fun toJson(writer: JsonWriter, value: FrontUpdate?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginObject()
        when {
            value.clearEndedAt -> {
                // Force an explicit null regardless of the writer's global
                // serializeNulls setting, so the server clears the field.
                val previous = writer.serializeNulls
                writer.serializeNulls = true
                writer.name("ended_at").nullValue()
                writer.serializeNulls = previous
            }
            value.endedAt != null -> writer.name("ended_at").value(value.endedAt)
        }
        value.memberIds?.let { ids ->
            writer.name("member_ids").beginArray()
            ids.forEach { writer.value(it) }
            writer.endArray()
        }
        value.startedAt?.let { writer.name("started_at").value(it) }
        value.customStatus?.let { writer.name("custom_status").value(it) }
        writer.endObject()
    }

    override fun fromJson(reader: JsonReader): FrontUpdate {
        var endedAt: String? = null
        var clearEndedAt = false
        var memberIds: List<String>? = null
        var startedAt: String? = null
        var customStatus: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "ended_at" ->
                    if (reader.peek() == JsonReader.Token.NULL) {
                        reader.nextNull<Unit>()
                        clearEndedAt = true
                    } else {
                        endedAt = reader.nextString()
                    }
                "member_ids" ->
                    if (reader.peek() == JsonReader.Token.NULL) {
                        reader.nextNull<Unit>()
                    } else {
                        val ids = mutableListOf<String>()
                        reader.beginArray()
                        while (reader.hasNext()) ids.add(reader.nextString())
                        reader.endArray()
                        memberIds = ids
                    }
                "started_at" ->
                    if (reader.peek() == JsonReader.Token.NULL) reader.nextNull<Unit>()
                    else startedAt = reader.nextString()
                "custom_status" ->
                    if (reader.peek() == JsonReader.Token.NULL) reader.nextNull<Unit>()
                    else customStatus = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return FrontUpdate(
            endedAt = endedAt,
            clearEndedAt = clearEndedAt,
            memberIds = memberIds,
            startedAt = startedAt,
            customStatus = customStatus,
        )
    }
}
