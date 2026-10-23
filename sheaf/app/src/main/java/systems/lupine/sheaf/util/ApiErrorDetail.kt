package systems.lupine.sheaf.util

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import retrofit2.HttpException

/**
 * Reading the server's own explanation out of a failed response.
 *
 * The API puts it in `detail`, and every surface that wants it has to read the
 * same body. That is the catch: an error body is a one-shot stream, and
 * `ResponseBody.string()` closes the source, so the second reader gets nothing.
 * The sharing flows already hit this and worked around it locally, with a
 * comment about not letting `toUserMessage` "race us for it". Once the
 * technical-errors toggle exists, two readers on one exception stops being
 * hypothetical: the detail is wanted for the message AND for classification on
 * the same failure.
 *
 * So this peeks rather than consumes. Retrofit buffers the error body before
 * building the exception, so `peek()` hands back an independent view and the
 * original stays readable for whoever asks next. Call it as many times as you
 * like, in any order.
 */

private val errorBodyAdapter by lazy {
    Moshi.Builder().build().adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
    )
}

/** The raw error body, without consuming it. Empty string when there is none. */
fun HttpException.errorBodyText(): String = runCatching {
    response()?.errorBody()?.source()?.peek()?.readUtf8().orEmpty()
}.getOrDefault("")

/**
 * The server's `detail` string, or null when the body has none.
 *
 * A validation failure (422) answers with a list of field errors rather than a
 * string, which is not something to put in front of somebody as prose, so it
 * reads as "no detail" here and the caller falls back to its summary.
 */
fun HttpException.errorDetail(): String? = runCatching {
    val body = errorBodyText()
    if (body.isBlank()) return@runCatching null
    (errorBodyAdapter.fromJson(body)?.get("detail") as? String)?.takeIf { it.isNotBlank() }
}.getOrNull()
