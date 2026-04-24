package systems.lupine.sheaf.data.api

import android.util.Base64
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import systems.lupine.sheaf.data.model.CaptchaChallenge
import systems.lupine.sheaf.data.model.CaptchaPayload
import systems.lupine.sheaf.data.model.CaptchaSolution
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Solves an Altcha v2 proof-of-work challenge and encodes the solution
 * payload as the server expects it (base64-encoded JSON).
 *
 * Only `PBKDF2/SHA-256` is supported — that's what the Sheaf backend issues.
 * The java.security PBKDF2 SPI takes `char[]` passwords (UTF-8 encoded), which
 * wouldn't round-trip the raw `nonce || counter` bytes Altcha requires, so we
 * implement PBKDF2 directly on top of HmacSHA256.
 */
@Singleton
class AltchaSolver @Inject constructor(moshi: Moshi) {

    private val payloadAdapter = moshi.adapter(CaptchaPayload::class.java)

    /**
     * Solves [challenge] and returns the `captcha` field value to send with
     * the login/register request. Runs on [Dispatchers.Default] since PBKDF2
     * at the server's default cost (~50k iterations) is CPU-bound for a few
     * hundred counters on average.
     */
    suspend fun solve(challenge: CaptchaChallenge): String = withContext(Dispatchers.Default) {
        val params = challenge.parameters
        require(params.algorithm.equals("PBKDF2/SHA-256", ignoreCase = true)) {
            "Unsupported altcha algorithm: ${params.algorithm}"
        }

        val nonceBytes = params.nonce.hexToBytes()
        val saltBytes = params.salt.hexToBytes()
        val passwordBuffer = ByteArray(nonceBytes.size + 4)
        System.arraycopy(nonceBytes, 0, passwordBuffer, 0, nonceBytes.size)

        val mac = Mac.getInstance("HmacSHA256")
        val started = System.currentTimeMillis()
        var counter = 0
        while (counter < MAX_COUNTER) {
            // Cooperatively cancel every so often without paying the check cost per iteration.
            if (counter and 0x3F == 0) coroutineContext.ensureActive()

            passwordBuffer[nonceBytes.size] = (counter ushr 24).toByte()
            passwordBuffer[nonceBytes.size + 1] = (counter ushr 16).toByte()
            passwordBuffer[nonceBytes.size + 2] = (counter ushr 8).toByte()
            passwordBuffer[nonceBytes.size + 3] = counter.toByte()

            val derivedKey = pbkdf2HmacSha256(mac, passwordBuffer, saltBytes, params.cost, params.keyLength)
            val derivedHex = derivedKey.toHex()
            if (derivedHex.startsWith(params.keyPrefix, ignoreCase = true)) {
                val payload = CaptchaPayload(
                    challenge = challenge,
                    solution = CaptchaSolution(
                        counter = counter,
                        derivedKey = derivedHex,
                        time = System.currentTimeMillis() - started,
                    ),
                )
                val json = payloadAdapter.toJson(payload)
                return@withContext Base64.encodeToString(
                    json.toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP,
                )
            }
            counter++
        }
        error("Altcha challenge unsolvable within $MAX_COUNTER counters")
    }

    companion object {
        private const val MAX_COUNTER = 1_000_000
    }
}

private fun pbkdf2HmacSha256(
    mac: Mac,
    password: ByteArray,
    salt: ByteArray,
    iterations: Int,
    keyLength: Int,
): ByteArray {
    mac.init(SecretKeySpec(password, "HmacSHA256"))
    val hLen = 32
    val blocks = (keyLength + hLen - 1) / hLen
    val out = ByteArray(keyLength)
    for (i in 1..blocks) {
        mac.update(salt)
        mac.update(byteArrayOf(
            (i ushr 24).toByte(),
            (i ushr 16).toByte(),
            (i ushr 8).toByte(),
            i.toByte(),
        ))
        var u = mac.doFinal()
        val t = u.copyOf()
        for (j in 2..iterations) {
            u = mac.doFinal(u)
            for (k in 0 until hLen) t[k] = (t[k].toInt() xor u[k].toInt()).toByte()
        }
        val offset = (i - 1) * hLen
        val copy = minOf(hLen, keyLength - offset)
        System.arraycopy(t, 0, out, offset, copy)
    }
    return out
}

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Hex string has odd length" }
    val out = ByteArray(length / 2)
    var i = 0
    while (i < length) {
        val hi = Character.digit(this[i], 16)
        val lo = Character.digit(this[i + 1], 16)
        require(hi >= 0 && lo >= 0) { "Invalid hex character at $i" }
        out[i / 2] = ((hi shl 4) or lo).toByte()
        i += 2
    }
    return out
}

private fun ByteArray.toHex(): String {
    val hex = CharArray(size * 2)
    for (i in indices) {
        val v = this[i].toInt() and 0xFF
        hex[i * 2] = HEX_CHARS[v ushr 4]
        hex[i * 2 + 1] = HEX_CHARS[v and 0x0F]
    }
    return String(hex)
}

private val HEX_CHARS = "0123456789abcdef".toCharArray()
