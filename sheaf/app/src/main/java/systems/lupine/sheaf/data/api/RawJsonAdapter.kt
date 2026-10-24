package systems.lupine.sheaf.data.api

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import systems.lupine.sheaf.data.model.RawJson

/**
 * Copies a JSON value byte for byte in both directions.
 *
 * Moshi's generic Map decoding turns every number into a Double, so a WebAuthn
 * `alg: -7` would come back out as `-7.0` and the authenticator would reject
 * the options. Streaming the source untouched avoids that.
 */
class RawJsonAdapter : JsonAdapter<RawJson>() {

    override fun fromJson(reader: JsonReader): RawJson =
        reader.nextSource().use { RawJson(it.readUtf8()) }

    override fun toJson(writer: JsonWriter, value: RawJson?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.valueSink().use { it.writeUtf8(value.json) }
    }
}
