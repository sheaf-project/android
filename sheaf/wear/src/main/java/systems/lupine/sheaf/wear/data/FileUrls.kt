package systems.lupine.sheaf.wear.data

/**
 * Resolving the file URLs the API hands back.
 *
 * The server returns avatars in its canonical stored form, a server-relative
 * path: `/v1/files/avatars/<system-id>/<file-id>.png`. Clients are expected to
 * resolve it, and an instance may serve files from somewhere else entirely,
 * which `file_cdn_base` in the public auth config names.
 *
 * The phone has done this since the CDN work (`RelativeUrlInterceptor`, a Coil
 * interceptor). The watch never did, so it handed Coil and OkHttp a path with
 * no scheme or host: Coil failed silently and drew nothing, and
 * `Request.Builder().url("/v1/...")` threw inside a `runCatching` that
 * swallowed it, so a tile avatar came back null. A member with an image avatar
 * showed an empty slot on the watch while the same avatar was fine on the
 * phone.
 *
 * The rules are the phone's, deliberately, so the two clients cannot disagree
 * about where a file lives.
 */

/**
 * The absolute URL to fetch [raw] from, or null when there is nothing usable.
 *
 * - An absolute URL on the API's own host moves to the CDN when one is set.
 * - A leading-slash path resolves against the CDN if set, else the API base.
 * - A bare relative path is treated as server-relative and resolved the same.
 * - Any other absolute URL is left exactly as it is, because it belongs to
 *   somebody else and rewriting it would point it at the wrong host.
 *
 * Null means "cannot be fetched": a relative path with no base to resolve it
 * against. Callers draw their fallback rather than requesting a bad URL.
 */
fun resolveFileUrl(raw: String?, baseUrl: String?, cdnBase: String?): String? {
    val data = raw?.trim()?.ifBlank { null } ?: return null
    val cdn = cdnBase?.trim()?.trimEnd('/')?.ifBlank { null }
    val base = baseUrl?.trim()?.trimEnd('/')?.ifBlank { null }
    val preferred = cdn ?: base
    return when {
        cdn != null && base != null && data.startsWith("$base/") ->
            cdn + data.removePrefix(base)
        data.startsWith("/") -> preferred?.let { "$it$data" }
        !data.contains("://") -> preferred?.let { "$it/$data" }
        else -> data
    }
}
