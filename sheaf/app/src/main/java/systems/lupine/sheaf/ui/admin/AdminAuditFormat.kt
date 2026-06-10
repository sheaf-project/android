package systems.lupine.sheaf.ui.admin

/**
 * Shared formatting for the admin audit log, used by both the admin-facing
 * viewer and the user-facing "actions on your account" screen.
 */

/** Friendly label for an AdminAuditAction enum value. */
internal fun adminActionLabel(action: String): String = when (action.uppercase()) {
    "USER_UPDATE" -> "Account updated"
    "USER_APPROVE" -> "Registration approved"
    "USER_REJECT" -> "Registration rejected"
    "USER_MEMBER_LIMIT_SET" -> "Member limit changed"
    "USER_SAFETY_RESET" -> "System safety reset"
    "USER_PENDING_BYPASS" -> "Pending actions finalized"
    "IMPORT_LOG_VIEW" -> "Import log viewed"
    "USER_SESSION_REVOKE" -> "Session revoked"
    "USER_API_KEYS_ROTATE_ALL" -> "All API keys revoked"
    "USER_SUSPEND" -> "Account suspended"
    "USER_UNSUSPEND" -> "Suspension lifted"
    "USER_DOSSIER_EXPORT" -> "Account data exported"
    "USER_BAN" -> "Account banned"
    "USER_UNBAN" -> "Ban lifted"
    "USER_PASSWORD_RESET" -> "Password reset"
    "USER_EMAIL_CHANGE" -> "Email changed"
    "USER_TOTP_DISABLE" -> "Two-factor disabled"
    "USER_EMAIL_VERIFY" -> "Email force-verified"
    "USER_DELETION_CANCEL" -> "Account deletion cancelled"
    "INVITE_CREATE" -> "Invite created"
    "INVITE_DELETE" -> "Invite deleted"
    "JOB_TRIGGER" -> "Maintenance job run"
    // Unknown / future action: degrade gracefully to a title-cased form of
    // the raw enum rather than showing a bare SCREAMING_SNAKE token.
    else -> action.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/** "USER", "SYSTEM", ... -> human-readable target noun. */
internal fun adminTargetLabel(targetType: String): String = when (targetType.uppercase()) {
    "USER" -> "user"
    "SYSTEM" -> "system"
    "PENDING_ACTION" -> "pending action"
    "IMPORT_JOB" -> "import job"
    "INVITE" -> "invite"
    "JOB" -> "job"
    else -> targetType.lowercase().replace('_', ' ')
}

/**
 * Render an ISO-8601 timestamp as "YYYY-MM-DD HH:MM". Audit timestamps come
 * from the server already in UTC ISO form; we keep it lexical rather than
 * pulling in timezone conversion for a log line. Falls back to the first 10
 * chars if the shape is unexpected.
 */
internal fun formatAuditTimestamp(iso: String): String = runCatching {
    if (iso.length >= 16 && iso[10] == 'T') {
        iso.substring(0, 10) + " " + iso.substring(11, 16)
    } else {
        iso.take(10)
    }
}.getOrDefault(iso.take(10))

/**
 * Flatten a before/after snapshot map into "key: value" display lines. Nested
 * structures are stringified; this is a log read-out, not an editor.
 */
internal fun auditFieldLines(snapshot: Map<String, Any?>?): List<String> {
    if (snapshot.isNullOrEmpty()) return emptyList()
    return snapshot.entries.map { (k, v) -> "$k: ${renderAuditValue(v)}" }
}

private fun renderAuditValue(v: Any?): String = when (v) {
    null -> "null"
    is Map<*, *> -> v.entries.joinToString(", ", "{", "}") { (k, x) -> "$k: ${renderAuditValue(x)}" }
    is List<*> -> v.joinToString(", ", "[", "]") { renderAuditValue(it) }
    is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
    else -> v.toString()
}
