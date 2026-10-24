package systems.lupine.sheaf.data.api

import com.squareup.moshi.Moshi
import systems.lupine.sheaf.data.model.PasskeyOptions
import systems.lupine.sheaf.data.model.PasskeySignInComplete
import systems.lupine.sheaf.data.model.RawJson
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * WebAuthn options go from the server to Credential Manager, and the
 * credential comes back, without the app reading either. Decoding them as a
 * Map would turn `alg: -7` into `-7.0` and the authenticator would refuse the
 * options, so the bytes have to survive untouched.
 */
class RawJsonAdapterTest {

    private val moshi = Moshi.Builder().add(RawJson::class.java, RawJsonAdapter()).build()

    @Test fun `options keep integer numbers as integers`() {
        val options = """{"challenge":"abc","pubKeyCredParams":[{"type":"public-key","alg":-7}],"timeout":60000}"""
        val parsed = moshi.adapter(PasskeyOptions::class.java).fromJson("""{"options":$options}""")!!
        assertEquals(options, parsed.options.json)
    }

    @Test fun `a credential is embedded as an object, not a string`() {
        val credential = """{"id":"x","response":{"clientDataJSON":"y"},"type":"public-key"}"""
        val body = moshi.adapter(PasskeySignInComplete::class.java)
            .toJson(PasskeySignInComplete(RawJson(credential)))
        assertEquals("""{"credential":$credential}""", body)
    }
}
