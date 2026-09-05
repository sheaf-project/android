package systems.lupine.sheaf.data.api

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import systems.lupine.sheaf.data.model.GroupUpdate
import systems.lupine.sheaf.data.model.MemberUpdate
import systems.lupine.sheaf.data.model.SystemUpdate

/**
 * Hand-written Moshi adapters for the PATCH bodies whose fields clear on an
 * explicit null.
 *
 * These endpoints read the body with `exclude_unset`, so presence is the
 * contract:
 *   - omitted   -> leave the existing value alone
 *   - JSON null -> clear it
 *   - a value   -> set it
 *
 * Moshi omits null fields, which expresses the first two-thirds and leaves no
 * way to clear anything. Emptying a member's pronouns, a group's description or
 * a system's tag therefore did nothing: the field was dropped from the request
 * and the old value survived.
 *
 * The obvious shortcut, turning on null serialisation for the whole body, does
 * not work. Each of these bodies also carries fields backed by NOT NULL columns
 * that the server rejects an explicit null for (a member's name and privacy, a
 * system's date_format, and so on). Blanket nulls would start sending those and
 * fail the whole save. So each field is either clearable or omit-when-null, and
 * the split has to match the server's `_reject_explicit_null` lists.
 *
 * [PatchWriter.clears] and [PatchWriter.omitsWhenNull] name which is which, so
 * a field added later has to make the choice explicitly.
 */
internal class PatchWriter(
    private val writer: JsonWriter,
    private val anyAdapter: JsonAdapter<Any>,
) {
    /** Server clears the column on an explicit null, so always write it. */
    fun clears(name: String, value: Any?) {
        val previous = writer.serializeNulls
        writer.serializeNulls = true
        writer.name(name)
        if (value == null) writer.nullValue() else anyAdapter.toJson(writer, value)
        writer.serializeNulls = previous
    }

    /** Server rejects an explicit null here, so send nothing at all. */
    fun omitsWhenNull(name: String, value: Any?) {
        if (value == null) return
        writer.name(name)
        anyAdapter.toJson(writer, value)
    }
}

private fun requestOnly(name: String): Nothing =
    throw UnsupportedOperationException("$name is a request body; it is never parsed")

class MemberUpdateJsonAdapter(moshi: Moshi) : JsonAdapter<MemberUpdate>() {
    private val anyAdapter: JsonAdapter<Any> by lazy { moshi.adapter(Any::class.java) }

    override fun toJson(writer: JsonWriter, value: MemberUpdate?) {
        if (value == null) { writer.nullValue(); return }
        writer.beginObject()
        PatchWriter(writer, anyAdapter).apply {
            // NOT NULL server-side: name, privacy.
            omitsWhenNull("name", value.name)
            omitsWhenNull("privacy", value.privacy)
            clears("display_name", value.displayName)
            clears("description", value.description)
            clears("pronouns", value.pronouns)
            clears("avatar_url", value.avatarUrl)
            clears("banner_url", value.bannerUrl)
            clears("color", value.color)
            clears("birthday", value.birthday)
            clears("note", value.note)
            clears("emoji", value.emoji)
        }
        writer.endObject()
    }

    override fun fromJson(reader: JsonReader) = requestOnly("MemberUpdate")

    companion object {
        /** Needs the Moshi instance, so it registers as a factory. */
        val FACTORY = Factory { type, _, moshi ->
            if (Types.getRawType(type) == MemberUpdate::class.java) MemberUpdateJsonAdapter(moshi) else null
        }
    }
}

class GroupUpdateJsonAdapter(moshi: Moshi) : JsonAdapter<GroupUpdate>() {
    private val anyAdapter: JsonAdapter<Any> by lazy { moshi.adapter(Any::class.java) }

    override fun toJson(writer: JsonWriter, value: GroupUpdate?) {
        if (value == null) { writer.nullValue(); return }
        writer.beginObject()
        PatchWriter(writer, anyAdapter).apply {
            omitsWhenNull("name", value.name)
            clears("description", value.description)
            clears("color", value.color)
            // Clearing this is how a subgroup is promoted back to top level.
            clears("parent_id", value.parentId)
        }
        writer.endObject()
    }

    override fun fromJson(reader: JsonReader) = requestOnly("GroupUpdate")

    companion object {
        /** Needs the Moshi instance, so it registers as a factory. */
        val FACTORY = Factory { type, _, moshi ->
            if (Types.getRawType(type) == GroupUpdate::class.java) GroupUpdateJsonAdapter(moshi) else null
        }
    }
}

class SystemUpdateJsonAdapter(moshi: Moshi) : JsonAdapter<SystemUpdate>() {
    private val anyAdapter: JsonAdapter<Any> by lazy { moshi.adapter(Any::class.java) }

    override fun toJson(writer: JsonWriter, value: SystemUpdate?) {
        if (value == null) { writer.nullValue(); return }
        writer.beginObject()
        PatchWriter(writer, anyAdapter).apply {
            // NOT NULL server-side: name, privacy, show_member_created_date
            // (alongside date_format and the front defaults, which this client
            // does not send).
            omitsWhenNull("name", value.name)
            omitsWhenNull("privacy", value.privacy)
            omitsWhenNull("show_member_created_date", value.showMemberCreatedDate)
            clears("description", value.description)
            clears("tag", value.tag)
            clears("avatar_url", value.avatarUrl)
            clears("color", value.color)
            clears("note", value.note)
        }
        writer.endObject()
    }

    override fun fromJson(reader: JsonReader) = requestOnly("SystemUpdate")

    companion object {
        /** Needs the Moshi instance, so it registers as a factory. */
        val FACTORY = Factory { type, _, moshi ->
            if (Types.getRawType(type) == SystemUpdate::class.java) SystemUpdateJsonAdapter(moshi) else null
        }
    }
}
