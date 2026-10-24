package systems.lupine.sheaf.data.api

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import systems.lupine.sheaf.data.model.GroupUpdate
import systems.lupine.sheaf.data.model.MemberUpdate
import systems.lupine.sheaf.data.model.NotificationChannelUpdate
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
    /**
     * Whether a null in a clearable field means "clear it".
     *
     * False for a body that carries only part of the form, where a null means
     * the caller simply did not populate that field. Writing it anyway would
     * clear something the user never touched, which is how toggling a front
     * preference wiped the system's description, tag, colour, note, avatar and
     * banner: that screen sends one boolean and nothing else.
     */
    private val clearsUnsetFields: Boolean = true,
) {
    /**
     * Server clears the column on an explicit null.
     *
     * A set value is always written. A null is written only when this body is
     * the whole form, because only then does null mean the user emptied it.
     */
    fun clears(name: String, value: Any?) {
        if (value == null && !clearsUnsetFields) return
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
        PatchWriter(writer, anyAdapter, value.clearsUnsetFields).apply {
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
            omitsWhenNull("never_shareable", value.neverShareable)
            omitsWhenNull("fronting_private", value.frontingPrivate)
            omitsWhenNull("password", value.password)
            omitsWhenNull("totp_code", value.totpCode)
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
        PatchWriter(writer, anyAdapter, value.clearsUnsetFields).apply {
            omitsWhenNull("name", value.name)
            omitsWhenNull("privacy", value.privacy)
            clears("description", value.description)
            clears("color", value.color)
            // Clearing this is how a subgroup is promoted back to top level.
            clears("parent_id", value.parentId)
            omitsWhenNull("password", value.password)
            omitsWhenNull("totp_code", value.totpCode)
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

class NotificationChannelUpdateJsonAdapter(moshi: Moshi) : JsonAdapter<NotificationChannelUpdate>() {
    private val anyAdapter: JsonAdapter<Any> by lazy { moshi.adapter(Any::class.java) }

    override fun toJson(writer: JsonWriter, value: NotificationChannelUpdate?) {
        if (value == null) { writer.nullValue(); return }
        writer.beginObject()
        PatchWriter(writer, anyAdapter).apply {
            // quiet_hours is the one genuinely nullable field on a channel, and
            // the server keys on whether it was in the request rather than on
            // its value. Moshi dropped the null, so unticking "Quiet hours"
            // sent an empty body, the server left the column alone, and the
            // window came back on the next refresh.
            //
            // Written only when the user actually touched it. This body is a
            // partial patch, unlike the member and group ones, so an
            // unconditional null here would clear the window on every unrelated
            // channel save.
            if (value.quietHoursSet) clears("quiet_hours", value.quietHours)
            // webhook_secret, group_rules and member_rules are absent from the
            // server's _reject_explicit_null list, so a null would be accepted
            // rather than refused. That makes omitting them load-bearing: these
            // are sent only when the user edited them, and an explicit null
            // would wipe a secret or replace every rule with nothing.
            omitsWhenNull("webhook_secret", value.webhookSecret)
            omitsWhenNull("group_rules", value.groupRules)
            omitsWhenNull("member_rules", value.memberRules)
            omitsWhenNull("name", value.name)
            omitsWhenNull("destination_config", value.destinationConfig)
            omitsWhenNull("base_all_members", value.baseAllMembers)
            omitsWhenNull("base_include_private", value.baseIncludePrivate)
            omitsWhenNull("trigger_on_start", value.triggerOnStart)
            omitsWhenNull("trigger_on_stop", value.triggerOnStop)
            omitsWhenNull("trigger_on_cofront_change", value.triggerOnCofrontChange)
            omitsWhenNull("cofront_redaction", value.cofrontRedaction)
            omitsWhenNull("payload_sensitivity", value.payloadSensitivity)
            omitsWhenNull("debounce_seconds", value.debounceSeconds)
            omitsWhenNull("aggregation_window_seconds", value.aggregationWindowSeconds)
        }
        writer.endObject()
    }

    override fun fromJson(reader: JsonReader) = requestOnly("NotificationChannelUpdate")

    companion object {
        /** Needs the Moshi instance, so it registers as a factory. */
        val FACTORY = Factory { type, _, moshi ->
            if (Types.getRawType(type) == NotificationChannelUpdate::class.java) {
                NotificationChannelUpdateJsonAdapter(moshi)
            } else {
                null
            }
        }
    }
}

class SystemUpdateJsonAdapter(moshi: Moshi) : JsonAdapter<SystemUpdate>() {
    private val anyAdapter: JsonAdapter<Any> by lazy { moshi.adapter(Any::class.java) }

    override fun toJson(writer: JsonWriter, value: SystemUpdate?) {
        if (value == null) { writer.nullValue(); return }
        writer.beginObject()
        PatchWriter(writer, anyAdapter, value.clearsUnsetFields).apply {
            // NOT NULL server-side: name, privacy, show_member_created_date
            // and both front preferences (alongside date_format, which this
            // client does not send).
            omitsWhenNull("name", value.name)
            omitsWhenNull("privacy", value.privacy)
            omitsWhenNull("show_member_created_date", value.showMemberCreatedDate)
            omitsWhenNull("replace_fronts_default", value.replaceFrontsDefault)
            omitsWhenNull("coalesce_contiguous_fronts", value.coalesceContiguousFronts)
            clears("description", value.description)
            clears("tag", value.tag)
            clears("avatar_url", value.avatarUrl)
            clears("banner_url", value.bannerUrl)
            clears("color", value.color)
            clears("note", value.note)
            omitsWhenNull("password", value.password)
            omitsWhenNull("totp_code", value.totpCode)
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
