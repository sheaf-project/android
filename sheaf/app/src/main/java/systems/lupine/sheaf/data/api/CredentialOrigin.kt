package systems.lupine.sheaf.data.api

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Origin matching for deciding where credentials may be sent.
 *
 * The API auth stack (bearer token, Cloudflare Access secrets, the
 * trusted-device cookie) used to be attached host-blind, and the Coil image
 * client is cloned from the API client, so a member avatar or a bio-embedded
 * image hosted on an external server received the user's live credentials. The
 * interceptors now gate on origin: credentials go only to the instance's own
 * hosts, never to whatever host an image URL happens to point at.
 */

private fun normalizedOrigin(configured: String?): HttpUrl? {
    val raw = configured?.trim()?.trimEnd('/')?.ifBlank { null } ?: return null
    // The CDN base can arrive scheme-less from the instance config; assume https
    // so a bare host still parses to a comparable origin.
    val withScheme = if (raw.contains("://")) raw else "https://$raw"
    return withScheme.toHttpUrlOrNull()
}

/** True when [url] has the same scheme, host and port as [configured]. Path is ignored. */
internal fun originMatches(url: HttpUrl, configured: String?): Boolean {
    val base = normalizedOrigin(configured) ?: return false
    return url.scheme == base.scheme &&
        url.host.equals(base.host, ignoreCase = true) &&
        url.port == base.port
}

/**
 * Credentials may be sent to the API base origin and to the instance's
 * configured file CDN origin (both are infrastructure the user configured or
 * received from the instance's own config), and to nothing else.
 */
internal fun isTrustedCredentialOrigin(url: HttpUrl, baseUrl: String?, fileCdnBase: String?): Boolean =
    originMatches(url, baseUrl) || originMatches(url, fileCdnBase)

/**
 * True when two configured base URLs point at the same origin. Used to decide
 * whether changing the server URL is actually switching instances (and so must
 * drop the old session) or just a cosmetic edit of the same one. Two blank/unset
 * values count as the same.
 */
internal fun sameConfiguredOrigin(a: String?, b: String?): Boolean {
    val ao = normalizedOrigin(a)
    val bo = normalizedOrigin(b)
    if (ao == null || bo == null) return ao == null && bo == null
    return ao.scheme == bo.scheme &&
        ao.host.equals(bo.host, ignoreCase = true) &&
        ao.port == bo.port
}
