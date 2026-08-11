package systems.lupine.sheaf.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ── Auth ─────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class AuthConfig(
    @Json(name = "registration_mode") val registrationMode: String,
    @Json(name = "invite_codes_enabled") val inviteCodesEnabled: Boolean,
    @Json(name = "email_verification") val emailVerification: String,
    @Json(name = "email_enabled") val emailEnabled: Boolean,
    @Json(name = "base_url") val baseUrl: String?,
    @Json(name = "account_deletion_grace_days") val accountDeletionGraceDays: Int? = null,
    @Json(name = "file_cdn_base") val fileCdnBase: String? = null,
    @Json(name = "captcha_provider") val captchaProvider: String? = null,
    @Json(name = "captcha_on_login") val captchaOnLogin: Boolean = false,
    // Operator-configured contact + policy links, surfaced on the Support
    // screen. All optional; the operator may set none of them.
    @Json(name = "support_email") val supportEmail: String? = null,
    @Json(name = "support_url") val supportUrl: String? = null,
    @Json(name = "support_note") val supportNote: String? = null,
    // Operator-authored markdown shown at the top of the Support screen.
    // Server strips any raw HTML before sending, so it's safe to render.
    @Json(name = "support_custom_text") val supportCustomText: String? = null,
    @Json(name = "status_url") val statusUrl: String? = null,
    @Json(name = "terms_url") val termsUrl: String? = null,
    @Json(name = "privacy_url") val privacyUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class UserRegister(
    val email: String,
    val password: String,
    @Json(name = "invite_code") val inviteCode: String? = null,
    val captcha: String? = null,
)

@JsonClass(generateAdapter = true)
data class UserLogin(
    val email: String,
    val password: String,
    @Json(name = "totp_code") val totpCode: String? = null,
    val captcha: String? = null,
    @Json(name = "remember_device") val rememberDevice: Boolean = false,
)

// ── Altcha v2 captcha ────────────────────────────────────────────────────────
// Echoed back inside the submitted solution payload, so every parameter the
// server sends must round-trip unchanged — otherwise the HMAC signature check
// fails. Optional fields stay nullable so Moshi omits them when absent.

@JsonClass(generateAdapter = true)
data class CaptchaChallenge(
    val parameters: CaptchaChallengeParameters,
    val signature: String? = null,
)

@JsonClass(generateAdapter = true)
data class CaptchaChallengeParameters(
    val algorithm: String,
    val cost: Int,
    @Json(name = "keyLength") val keyLength: Int,
    @Json(name = "keyPrefix") val keyPrefix: String,
    val nonce: String,
    val salt: String,
    @Json(name = "keySignature") val keySignature: String? = null,
    @Json(name = "memoryCost") val memoryCost: Int? = null,
    val parallelism: Int? = null,
    @Json(name = "expiresAt") val expiresAt: Long? = null,
)

@JsonClass(generateAdapter = true)
data class CaptchaSolution(
    val counter: Int,
    @Json(name = "derivedKey") val derivedKey: String,
    val time: Long? = null,
)

@JsonClass(generateAdapter = true)
data class CaptchaPayload(
    val challenge: CaptchaChallenge,
    val solution: CaptchaSolution,
)

@JsonClass(generateAdapter = true)
data class TokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String,
    @Json(name = "token_type") val tokenType: String = "bearer",
)

@JsonClass(generateAdapter = true)
data class TokenRefresh(
    @Json(name = "refresh_token") val refreshToken: String,
)

/**
 * Returned by `POST /v1/auth/sessions/secondary`. Identical shape to
 * [TokenResponse] but with the child session id surfaced so the caller can
 * track the wearable's session for revocation/cascade purposes.
 */
@JsonClass(generateAdapter = true)
data class SecondarySessionResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String,
    @Json(name = "session_id") val sessionId: String,
)

@JsonClass(generateAdapter = true)
data class SecondarySessionRequest(
    @Json(name = "client_name") val clientName: String? = null,
)

@JsonClass(generateAdapter = true)
data class TOTPVerify(
    val code: String,
)

@JsonClass(generateAdapter = true)
data class TOTPDisable(
    val email: String,
    val password: String,
    @Json(name = "totp_code") val totpCode: String,
)

@JsonClass(generateAdapter = true)
data class TOTPSetupResponse(
    val secret: String,
    @Json(name = "provisioning_uri") val provisioningUri: String,
    @Json(name = "recovery_codes") val recoveryCodes: List<String>,
)

@JsonClass(generateAdapter = true)
data class TOTPRecoveryCodes(
    @Json(name = "recovery_codes") val recoveryCodes: List<String>,
)

@JsonClass(generateAdapter = true)
data class UserRead(
    val id: String,
    val email: String,
    @Json(name = "totp_enabled") val totpEnabled: Boolean,
    @Json(name = "is_admin") val isAdmin: Boolean,
    val tier: String,
    @Json(name = "account_status") val accountStatus: String,
    @Json(name = "email_verified") val emailVerified: Boolean,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "last_login_at") val lastLoginAt: String?,
    @Json(name = "deletion_requested_at") val deletionRequestedAt: String? = null,
    // Effective permission flags. Defaults match backend defaults; older cached
    // payloads without these fields will treat all paths as allowed.
    @Json(name = "uploads_allowed") val uploadsAllowed: Boolean = true,
    @Json(name = "bio_uploads_allowed") val bioUploadsAllowed: Boolean = true,
    @Json(name = "external_images_allowed") val externalImagesAllowed: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class PasswordResetRequest(
    val email: String,
)

@JsonClass(generateAdapter = true)
data class PasswordReset(
    val token: String,
    @Json(name = "new_password") val newPassword: String,
)

@JsonClass(generateAdapter = true)
data class DeleteAccountRequest(
    val password: String,
    @Json(name = "totp_code") val totpCode: String? = null,
)

@JsonClass(generateAdapter = true)
data class DeleteConfirmationUpdate(
    val level: String,
    val password: String,
    @Json(name = "totp_code") val totpCode: String? = null,
)

// ── API Keys ──────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class ApiKeyCreate(
    val name: String,
    val scopes: List<String>,
    @Json(name = "expires_at") val expiresAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class ApiKeyRead(
    val id: String,
    val name: String,
    val scopes: List<String>,
    @Json(name = "last_used_at") val lastUsedAt: String?,
    @Json(name = "expires_at") val expiresAt: String?,
    @Json(name = "created_at") val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class ApiKeyCreated(
    val id: String,
    val name: String,
    val scopes: List<String>,
    @Json(name = "last_used_at") val lastUsedAt: String?,
    @Json(name = "expires_at") val expiresAt: String?,
    @Json(name = "created_at") val createdAt: String,
    val key: String,
)

// ── Sessions ──────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class SessionRead(
    val id: String,
    val nickname: String?,
    @Json(name = "client_name") val clientName: String,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "created_ip") val createdIp: String?,
    @Json(name = "last_active_at") val lastActiveAt: String?,
    @Json(name = "last_active_ip") val lastActiveIp: String?,
    @Json(name = "is_current") val isCurrent: Boolean,
)

@JsonClass(generateAdapter = true)
data class SessionUpdate(
    val nickname: String,
)

// ── System ────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class SystemRead(
    val id: String,
    val name: String,
    val description: String?,
    val tag: String?,
    @Json(name = "avatar_url") val avatarUrl: String?,
    val color: String?,
    val privacy: String,
    @Json(name = "delete_confirmation") val deleteConfirmation: String?,
    // Default to true if the field is missing (older cached payloads); matches
    // the backend default for replace_fronts_default and web's `?? true` fallback.
    @Json(name = "replace_fronts_default") val replaceFrontsDefault: Boolean = true,
    // Free-form scratchpad. Lightweight counterpart to journals: no versioning,
    // no destructive-auth on edits. Max 5000 chars server-side.
    val note: String? = null,
    // Account-wide display timezone: an IANA zone name, or null = "automatic"
    // (each device renders in its own local clock). Synced across the account's
    // devices; a per-device override can shadow it locally. See [resolveDisplayZone].
    val timezone: String? = null,
    // Display preference: show each member's created date on their profile.
    // Opt-in per system (default false, matching the backend) - some systems
    // want it, others find it noise. Purely a display gate; MemberRead.createdAt
    // is always present regardless.
    @Json(name = "show_member_created_date") val showMemberCreatedDate: Boolean = false,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
)

// Dedicated single-field body for updating the account timezone. Separate from
// SystemUpdate because "automatic" is an explicit null that must be *sent*
// (the backend uses exclude_unset: omitted = unchanged, null = auto), and the
// shared Moshi codegen omits null fields. Serialized with an adapter built via
// .serializeNulls() so the null actually reaches the wire.
@JsonClass(generateAdapter = true)
data class SystemTimezoneBody(
    val timezone: String?,
)

@JsonClass(generateAdapter = true)
data class SystemUpdate(
    val name: String? = null,
    val description: String? = null,
    val tag: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    val color: String? = null,
    val privacy: String? = null,
    val note: String? = null,
    @Json(name = "show_member_created_date") val showMemberCreatedDate: Boolean? = null,
)

// ── System Safety ─────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class SystemSafetySettings(
    @Json(name = "grace_period_days") val gracePeriodDays: Int,
    @Json(name = "auth_tier") val authTier: String,
    @Json(name = "applies_to_members") val appliesToMembers: Boolean,
    @Json(name = "applies_to_groups") val appliesToGroups: Boolean,
    @Json(name = "applies_to_tags") val appliesToTags: Boolean,
    @Json(name = "applies_to_fields") val appliesToFields: Boolean,
    @Json(name = "applies_to_fronts") val appliesToFronts: Boolean,
    @Json(name = "applies_to_journals") val appliesToJournals: Boolean,
    @Json(name = "applies_to_images") val appliesToImages: Boolean,
    @Json(name = "applies_to_revisions") val appliesToRevisions: Boolean = false,
    @Json(name = "auto_pin_first_revision") val autoPinFirstRevision: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class SystemSafetyUpdate(
    @Json(name = "grace_period_days") val gracePeriodDays: Int? = null,
    @Json(name = "auth_tier") val authTier: String? = null,
    @Json(name = "applies_to_members") val appliesToMembers: Boolean? = null,
    @Json(name = "applies_to_groups") val appliesToGroups: Boolean? = null,
    @Json(name = "applies_to_tags") val appliesToTags: Boolean? = null,
    @Json(name = "applies_to_fields") val appliesToFields: Boolean? = null,
    @Json(name = "applies_to_fronts") val appliesToFronts: Boolean? = null,
    @Json(name = "applies_to_journals") val appliesToJournals: Boolean? = null,
    @Json(name = "applies_to_images") val appliesToImages: Boolean? = null,
    @Json(name = "applies_to_revisions") val appliesToRevisions: Boolean? = null,
    @Json(name = "auto_pin_first_revision") val autoPinFirstRevision: Boolean? = null,
    val password: String? = null,
    @Json(name = "totp_code") val totpCode: String? = null,
)

@JsonClass(generateAdapter = true)
data class PendingActionRead(
    val id: String,
    @Json(name = "action_type") val actionType: String,
    @Json(name = "target_id") val targetId: String,
    @Json(name = "target_label") val targetLabel: String,
    @Json(name = "requested_at") val requestedAt: String,
    @Json(name = "requested_by_user_id") val requestedByUserId: String?,
    @Json(name = "finalize_after") val finalizeAfter: String,
    @Json(name = "fronting_member_ids") val frontingMemberIds: List<String>,
    @Json(name = "fronting_member_names") val frontingMemberNames: List<String>,
    val status: String,
)

// `changes` is an arbitrary JSON object whose values come back as Moshi's
// built-in Object adapter handles (Boolean / Double / String / List / Map).
@JsonClass(generateAdapter = true)
data class SafetyChangeRequestRead(
    val id: String,
    @Json(name = "requested_at") val requestedAt: String,
    @Json(name = "requested_by_user_id") val requestedByUserId: String?,
    @Json(name = "finalize_after") val finalizeAfter: String,
    val changes: Map<String, Any?>,
    val status: String,
)

@JsonClass(generateAdapter = true)
data class SystemSafetyResponse(
    val settings: SystemSafetySettings,
    @Json(name = "pending_actions") val pendingActions: List<PendingActionRead>,
    @Json(name = "pending_changes") val pendingChanges: List<SafetyChangeRequestRead>,
)

@JsonClass(generateAdapter = true)
data class SystemSafetyUpdateResponse(
    val settings: SystemSafetySettings,
    val applied: List<String>,
    val deferred: List<String>,
    @Json(name = "pending_change") val pendingChange: SafetyChangeRequestRead?,
)

// ── Revision retention ────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class RetentionTrimNoticeRead(
    val id: String,
    @Json(name = "requested_at") val requestedAt: String,
    @Json(name = "effective_at") val effectiveAt: String,
    @Json(name = "from_tier") val fromTier: String,
    @Json(name = "to_tier") val toTier: String,
    val reason: String,
    val status: String,
)

// Caps semantics: 0 means "unlimited" (selfhosted tier default). override_*
// is null when no override is set and the tier max applies directly.
@JsonClass(generateAdapter = true)
data class RetentionResponse(
    @Json(name = "effective_max_revisions") val effectiveMaxRevisions: Int,
    @Json(name = "effective_max_days") val effectiveMaxDays: Int,
    @Json(name = "tier_max_revisions") val tierMaxRevisions: Int,
    @Json(name = "tier_max_days") val tierMaxDays: Int,
    @Json(name = "override_revisions") val overrideRevisions: Int? = null,
    @Json(name = "override_days") val overrideDays: Int? = null,
    @Json(name = "trim_notice") val trimNotice: RetentionTrimNoticeRead? = null,
)

// Lowering a cap (keeping fewer revisions) is the loosening path: it routes
// through the safety grace period and needs re-auth via password / totp_code,
// because dropping retention is destructive (existing revisions over the new
// cap get pruned). Raising a cap or clearing the override (null) applies
// immediately. 0 means "unlimited".
@JsonClass(generateAdapter = true)
data class RetentionUpdate(
    @Json(name = "max_revisions") val maxRevisions: Int? = null,
    @Json(name = "max_revision_days") val maxRevisionDays: Int? = null,
    val password: String? = null,
    @Json(name = "totp_code") val totpCode: String? = null,
)

// ── Members ───────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class MemberDeleteConfirm(
    val password: String? = null,
    @Json(name = "totp_code") val totpCode: String? = null,
)

@JsonClass(generateAdapter = true)
data class MemberDeletePending(
    @Json(name = "pending_action_id") val pendingActionId: String,
    @Json(name = "finalize_after") val finalizeAfter: String,
)

@JsonClass(generateAdapter = true)
data class MemberRead(
    val id: String,
    @Json(name = "system_id") val systemId: String,
    val name: String,
    @Json(name = "display_name") val displayName: String?,
    val description: String?,
    val pronouns: String?,
    @Json(name = "avatar_url") val avatarUrl: String?,
    // Wide header image for the member profile. Same storage/trust model as
    // avatar_url; differs only in aspect ratio (3:1) at the display layer.
    @Json(name = "banner_url") val bannerUrl: String? = null,
    val color: String?,
    val birthday: String?,
    val privacy: String,
    // Free-form scratchpad. Lightweight, no versioning, no destructive-auth.
    // Use the description for the "bio" with revision history; this is for
    // running notes like trigger lists, fav drinks, current meds. Max 5000.
    val note: String? = null,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    val emoji: String? = null,
    // Set when the member is archived: a reversible soft-hide. The list
    // endpoint still returns archived members, so the client filters them
    // out of the main roster and surfaces them separately.
    @Json(name = "archived_at") val archivedAt: String? = null,
    // Set when a System Safety grace period has this queued for deletion.
    // Still returned and still usable until the window closes; the UI marks it.
    @Json(name = "pending_delete_at") val pendingDeleteAt: String? = null,
) {
    val displayNameOrName: String get() = displayName?.takeIf { it.isNotBlank() } ?: name
    val isArchived: Boolean get() = archivedAt != null
    val initials: String get() = displayNameOrName
        .split("\\s+".toRegex())
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }
}

@JsonClass(generateAdapter = true)
data class MemberCreate(
    val name: String,
    @Json(name = "display_name") val displayName: String? = null,
    val description: String? = null,
    val pronouns: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "banner_url") val bannerUrl: String? = null,
    val color: String? = null,
    val birthday: String? = null,
    val privacy: String = "private",
    val note: String? = null,
)

/** Optional step-up credentials for archiving a member. Only consulted when
 *  the system's "archive" safety category is enabled; an empty body is fine
 *  otherwise. */
@JsonClass(generateAdapter = true)
data class MemberArchiveBody(
    val password: String? = null,
    @Json(name = "totp_code") val totpCode: String? = null,
)

@JsonClass(generateAdapter = true)
data class MemberUpdate(
    val name: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
    val description: String? = null,
    val pronouns: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "banner_url") val bannerUrl: String? = null,
    val color: String? = null,
    val birthday: String? = null,
    val privacy: String? = null,
    val note: String? = null,
)

// ── Fronts ────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class FrontRead(
    val id: String,
    @Json(name = "system_id") val systemId: String,
    @Json(name = "started_at") val startedAt: String,
    @Json(name = "ended_at") val endedAt: String?,
    @Json(name = "member_ids") val memberIds: List<String>,
    // Optional per-entry "what was going on" note. Server-side it's
    // called custom_status; UI labels match web / iOS which both use
    // "Custom status". Surfaces on cards, in history, and in
    // create / edit forms.
    @Json(name = "custom_status") val customStatus: String? = null,
    // Per-member effective fronting-since (chain-aware) when the system has
    // coalesce_contiguous_fronts enabled. Keys are member ids; values are
    // ISO timestamps of the earliest started_at in each member's contiguous
    // chain. Falls back to this.startedAt when absent.
    @Json(name = "member_since") val memberSince: Map<String, String> = emptyMap(),
    // Member ids whose member_since hit the server-side walk-back depth cap.
    // Their timestamp is a lower bound; UI should prefix with "> ".
    @Json(name = "member_since_capped") val memberSinceCapped: List<String> = emptyList(),
    // Set when a System Safety grace period has this queued for deletion.
    // Still returned and still usable until the window closes; the UI marks it.
    @Json(name = "pending_delete_at") val pendingDeleteAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class FrontCreate(
    @Json(name = "member_ids") val memberIds: List<String>,
    @Json(name = "started_at") val startedAt: String? = null,
    // null = let the server fall back to system.replace_fronts_default.
    @Json(name = "replace_fronts") val replaceFronts: Boolean? = null,
    @Json(name = "custom_status") val customStatus: String? = null,
)

/**
 * Body for `POST /v1/fronts/{id}/replace`: end one specific open front and
 * open a replacement in its place, atomically, leaving every other open front
 * untouched.
 *
 * This is the correct way to change who is in a co-front. Editing the member
 * list in place loses each member's stint as its own history entry, and
 * ending-then-creating emits two notifications for one change; this does both
 * halves in one transaction, so it reads as a single aggregated change.
 *
 * `memberIds` must be non-empty (to end a front entirely, end it instead).
 * Omitting `customStatus` carries the replaced front's status over.
 */
@JsonClass(generateAdapter = true)
data class FrontReplace(
    @Json(name = "member_ids") val memberIds: List<String>,
    @Json(name = "started_at") val startedAt: String? = null,
    @Json(name = "custom_status") val customStatus: String? = null,
)

// Serialized by the hand-written FrontUpdateJsonAdapter rather than codegen,
// because the wire contract is tristate-by-presence (omit = leave as-is,
// JSON null = clear, value = set) and Moshi cannot emit an explicit null on
// its own. Field names are mapped in that adapter.
data class FrontUpdate(
    val endedAt: String? = null,
    // When true, emit an explicit `ended_at: null` to CLEAR the end time
    // (i.e. mark the front still ongoing). This is not itself a wire field;
    // it is consumed by FrontUpdateJsonAdapter and is mutually exclusive
    // with a non-null endedAt. Leaving both unset omits ended_at entirely.
    val clearEndedAt: Boolean = false,
    val memberIds: List<String>? = null,
    val startedAt: String? = null,
    // null still omits the field (leave as-is). Clearing a custom status via
    // edit remains a follow-up to be wired up alongside audit edit.
    val customStatus: String? = null,
)

// ── Groups ────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class GroupRead(
    val id: String,
    @Json(name = "system_id") val systemId: String,
    val name: String,
    val description: String?,
    val color: String?,
    @Json(name = "parent_id") val parentId: String?,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    // Set when a System Safety grace period has this queued for deletion.
    // Still returned and still usable until the window closes; the UI marks it.
    @Json(name = "pending_delete_at") val pendingDeleteAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class GroupCreate(
    val name: String,
    val description: String? = null,
    val color: String? = null,
    @Json(name = "parent_id") val parentId: String? = null,
)

@JsonClass(generateAdapter = true)
data class GroupUpdate(
    val name: String? = null,
    val description: String? = null,
    val color: String? = null,
    @Json(name = "parent_id") val parentId: String? = null,
)

@JsonClass(generateAdapter = true)
data class GroupMemberUpdate(
    @Json(name = "member_ids") val memberIds: List<String>,
)

// ── Tags ──────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class TagRead(
    val id: String,
    @Json(name = "system_id") val systemId: String,
    val name: String,
    val color: String?,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    // Set when a System Safety grace period has this queued for deletion.
    // Still returned and still usable until the window closes; the UI marks it.
    @Json(name = "pending_delete_at") val pendingDeleteAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class TagCreate(
    val name: String,
    val color: String? = null,
)

@JsonClass(generateAdapter = true)
data class TagUpdate(
    val name: String? = null,
    val color: String? = null,
)

// Two response shapes for DELETE /v1/tags/{id}: 204 immediate, 202 pending.
// Both fields nullable so a single adapter handles either; caller decides
// which path was taken from the HTTP status code.
@JsonClass(generateAdapter = true)
data class TagDeletePending(
    @Json(name = "pending_action_id") val pendingActionId: String? = null,
    @Json(name = "finalize_after") val finalizeAfter: String? = null,
)

// ── Custom Fields ─────────────────────────────────────────────────────────────

/**
 * Per-field-type options. For SELECT / MULTISELECT, [choices] carries
 * the predefined values the user can pick from; null = freeform tag
 * mode (any string accepted server-side). Other field types don't
 * carry options today.
 */
@JsonClass(generateAdapter = true)
data class CustomFieldOptions(
    val choices: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class CustomFieldRead(
    val id: String,
    @Json(name = "system_id") val systemId: String,
    val name: String,
    @Json(name = "field_type") val fieldType: String,
    val options: CustomFieldOptions? = null,
    val order: Int,
    val privacy: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    // Set when a System Safety grace period has this queued for deletion.
    // Still returned and still usable until the window closes; the UI marks it.
    @Json(name = "pending_delete_at") val pendingDeleteAt: String? = null,
) {
    val fieldTypeDisplay: String get() = fieldType.replaceFirstChar { it.uppercase() }
    val privacyDisplay: String get() = privacy.replaceFirstChar { it.uppercase() }
}

@JsonClass(generateAdapter = true)
data class CustomFieldCreate(
    val name: String,
    @Json(name = "field_type") val fieldType: String,
    val options: CustomFieldOptions? = null,
    val order: Int? = null,
    val privacy: String = "private",
)

@JsonClass(generateAdapter = true)
data class CustomFieldUpdate(
    val name: String? = null,
    val options: CustomFieldOptions? = null,
    val privacy: String? = null,
)

// Per-member custom field values. The value column on the wire is
// type-erased (Any?) because the same endpoint carries booleans,
// strings, numbers, lists, and dates depending on the field's type.
// Moshi's KotlinJsonAdapterFactory (reflection adapter wired up in
// NetworkModule) handles Any? round-trips without per-class codegen.
// Read side: server returns the decrypted plaintext value. Write side:
// server validates against the field's type/choices and encrypts at
// rest. Field types map to value shapes:
//   text/number(string-of-digits)/date(iso string) -> String
//   boolean -> Boolean
//   select  -> String (must be in choices when choices are set)
//   multiselect -> List<String> (each must be in choices when set)
//
// Members the viewer isn't permitted to see fields for return an
// empty list; the per-field privacy gate is enforced server-side.

data class CustomFieldValueRead(
    @Json(name = "field_id") val fieldId: String,
    @Json(name = "member_id") val memberId: String,
    val value: Any?,
)

data class CustomFieldValueSet(
    @Json(name = "field_id") val fieldId: String,
    val value: Any?,
)

// ── Files ─────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class FileUploadResponse(
    val url: String,
    val key: String,
    val size: Long,
)

@JsonClass(generateAdapter = true)
data class FileUsage(
    @Json(name = "used_bytes") val usedBytes: Long,
    @Json(name = "quota_bytes") val quotaBytes: Long,
    @Json(name = "file_count") val fileCount: Int,
)

@JsonClass(generateAdapter = true)
data class FileRead(
    val id: String,
    val key: String,
    val url: String,
    val purpose: String,
    @Json(name = "content_type") val contentType: String,
    @Json(name = "size_bytes") val sizeBytes: Long,
    @Json(name = "created_at") val createdAt: String,
)

// Both response shapes for DELETE /v1/files/{id}: 200 returns
// {deleted, key, freed_bytes} on immediate delete, 202 returns
// {pending_action_id, finalize_after} when image-safeguarded. All fields
// nullable so a single adapter handles either shape; the caller decides
// which path was taken from the HTTP status code.
@JsonClass(generateAdapter = true)
data class FileDeletePending(
    @Json(name = "pending_action_id") val pendingActionId: String? = null,
    @Json(name = "finalize_after") val finalizeAfter: String? = null,
)

// ── Client Settings ───────────────────────────────────────────────────────────
// No codegen — settings is arbitrary JSON (Map<String, Any>), handled by KotlinJsonAdapterFactory.

data class ClientSettingsBody(
    val settings: Map<String, Any>,
)

data class ClientSettingsResponse(
    val clientId: String,
    val settings: Map<String, Any>,
)

// ── Simply Plural import ──────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class SPPreviewMember(
    val id: String,
    val name: String,
)

@JsonClass(generateAdapter = true)
data class SPPreviewSummary(
    @Json(name = "system_name") val systemName: String?,
    @Json(name = "member_count") val memberCount: Int,
    val members: List<SPPreviewMember>,
    @Json(name = "custom_front_count") val customFrontCount: Int,
    @Json(name = "custom_fronts") val customFronts: List<SPPreviewMember>,
    @Json(name = "front_history_count") val frontHistoryCount: Int,
    @Json(name = "group_count") val groupCount: Int,
    @Json(name = "custom_field_count") val customFieldCount: Int,
    @Json(name = "note_count") val noteCount: Int,
)

@JsonClass(generateAdapter = true)
data class SPImportResult(
    @Json(name = "members_imported") val membersImported: Int,
    @Json(name = "custom_fronts_imported") val customFrontsImported: Int,
    @Json(name = "fronts_imported") val frontsImported: Int,
    @Json(name = "groups_imported") val groupsImported: Int,
    @Json(name = "custom_fields_imported") val customFieldsImported: Int,
    @Json(name = "notes_skipped") val notesSkipped: Int,
    val warnings: List<String>,
)

// ── Sheaf import ──────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class SheafPreviewMember(
    val id: String,
    val name: String,
)

@JsonClass(generateAdapter = true)
data class SheafPreviewSummary(
    @Json(name = "system_name") val systemName: String?,
    @Json(name = "member_count") val memberCount: Int,
    val members: List<SheafPreviewMember>,
    @Json(name = "front_count") val frontCount: Int,
    @Json(name = "group_count") val groupCount: Int,
    @Json(name = "tag_count") val tagCount: Int,
    @Json(name = "custom_field_count") val customFieldCount: Int,
    // True when the previewed file is a complete-backup zip (export.json +
    // images/); imageCount is how many images it carries. Default to the
    // plain-JSON shape so older backends that don't return these still parse.
    val archive: Boolean = false,
    @Json(name = "image_count") val imageCount: Int = 0,
    // OpenPlural previews also report how many prior exports the file has
    // passed through (its lineage). Absent (0) for native Sheaf previews.
    @Json(name = "lineage_length") val lineageLength: Int = 0,
)

@JsonClass(generateAdapter = true)
data class SheafImportResult(
    @Json(name = "members_imported") val membersImported: Int,
    @Json(name = "fronts_imported") val frontsImported: Int,
    @Json(name = "groups_imported") val groupsImported: Int,
    @Json(name = "tags_imported") val tagsImported: Int,
    @Json(name = "custom_fields_imported") val customFieldsImported: Int,
    @Json(name = "images_imported") val imagesImported: Int = 0,
    val warnings: List<String>,
)

// ── PluralKit import ─────────────────────────────────────────────────────────
//
// Same canonical PK shape for both ingestion paths (file upload + live API
// pull); the preview/result schemas don't care which path produced them.

@JsonClass(generateAdapter = true)
data class PKPreviewMember(
    val id: String,  // PK HID
    val name: String,
)

@JsonClass(generateAdapter = true)
data class PKPreviewSummary(
    @Json(name = "system_name") val systemName: String?,
    @Json(name = "member_count") val memberCount: Int,
    val members: List<PKPreviewMember>,
    @Json(name = "group_count") val groupCount: Int,
    @Json(name = "switch_count") val switchCount: Int,
    @Json(name = "earliest_switch") val earliestSwitch: String? = null,
    @Json(name = "latest_switch") val latestSwitch: String? = null,
)

/** Result decoded from an ImportJobRead.counts dict at terminal status. */
@JsonClass(generateAdapter = true)
data class PKImportResult(
    @Json(name = "members_imported") val membersImported: Int,
    @Json(name = "groups_imported") val groupsImported: Int,
    @Json(name = "fronts_imported") val frontsImported: Int,
    val warnings: List<String> = emptyList(),
)

/** Body for the PK API preview endpoint — token only, request-scoped. */
@JsonClass(generateAdapter = true)
data class PKApiPreviewBody(
    val token: String,
)

// ── Tupperbox import ─────────────────────────────────────────────────────────
//
// Tupperbox is a Discord proxy bot. Its export is a flat tupper list plus
// groups; no system metadata, no fronting history, no custom fields. So the
// preview surface is correspondingly smaller than PK / SP / Sheaf.

@JsonClass(generateAdapter = true)
data class TBPreviewMember(
    val id: String,  // Tupperbox numeric id, stringified for transport
    val name: String,
)

@JsonClass(generateAdapter = true)
data class TBPreviewSummary(
    @Json(name = "member_count") val memberCount: Int,
    val members: List<TBPreviewMember>,
    @Json(name = "group_count") val groupCount: Int,
)

@JsonClass(generateAdapter = true)
data class TBImportResult(
    @Json(name = "members_imported") val membersImported: Int,
    @Json(name = "groups_imported") val groupsImported: Int,
    val warnings: List<String> = emptyList(),
)

// ── PluralSpace import ────────────────────────────────────────────────────────
//
// PluralSpace exports a zip (members + custom fronts + groups + custom fields +
// front history + journal + chat + polls + media). The preview opens the zip
// server-side and reports counts; submit goes through the unified file runner
// under source = pluralspace_file. No credential — the zip isn't encrypted.

@JsonClass(generateAdapter = true)
data class PluralSpacePreviewMember(
    val id: String,
    val name: String,
    @Json(name = "is_custom_front") val isCustomFront: Boolean = false,
    @Json(name = "is_archived") val isArchived: Boolean = false,
    @Json(name = "has_avatar") val hasAvatar: Boolean = false,
    val roles: List<String> = emptyList(),
    val groups: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PluralSpacePreviewSummary(
    @Json(name = "system_name") val systemName: String? = null,
    @Json(name = "format_version") val formatVersion: String? = null,
    @Json(name = "export_date") val exportDate: String? = null,
    @Json(name = "member_count") val memberCount: Int = 0,
    @Json(name = "custom_front_count") val customFrontCount: Int = 0,
    val members: List<PluralSpacePreviewMember> = emptyList(),
    @Json(name = "group_count") val groupCount: Int = 0,
    @Json(name = "custom_field_count") val customFieldCount: Int = 0,
    @Json(name = "front_count") val frontCount: Int = 0,
    @Json(name = "journal_entry_count") val journalEntryCount: Int = 0,
    @Json(name = "chat_channel_count") val chatChannelCount: Int = 0,
    @Json(name = "chat_message_count") val chatMessageCount: Int = 0,
    @Json(name = "poll_count") val pollCount: Int = 0,
    @Json(name = "thought_count") val thoughtCount: Int = 0,
    @Json(name = "media_file_count") val mediaFileCount: Int = 0,
)

// ── Prism import ──────────────────────────────────────────────────────────────
//
// Prism exports a passphrase-encrypted .prism file (PRISM1 envelope). The
// preview decrypts it server-side using the supplied passphrase; submit goes
// through the unified file runner under source = prism_file with the passphrase
// passed as the `credential` form field (encrypted at rest until the job runs).

@JsonClass(generateAdapter = true)
data class PrismPreviewMember(
    val id: String,
    val name: String,
    @Json(name = "is_archived") val isArchived: Boolean = false,
    @Json(name = "has_avatar") val hasAvatar: Boolean = false,
    @Json(name = "pluralkit_id") val pluralkitId: String? = null,
)

@JsonClass(generateAdapter = true)
data class PrismPreviewSummary(
    @Json(name = "system_name") val systemName: String? = null,
    @Json(name = "format_version") val formatVersion: String? = null,
    @Json(name = "export_date") val exportDate: String? = null,
    @Json(name = "app_name") val appName: String? = null,
    @Json(name = "member_count") val memberCount: Int = 0,
    val members: List<PrismPreviewMember> = emptyList(),
    @Json(name = "group_count") val groupCount: Int = 0,
    @Json(name = "custom_field_count") val customFieldCount: Int = 0,
    @Json(name = "front_session_count") val frontSessionCount: Int = 0,
    @Json(name = "sleep_session_count") val sleepSessionCount: Int = 0,
    @Json(name = "conversation_count") val conversationCount: Int = 0,
    @Json(name = "message_count") val messageCount: Int = 0,
    @Json(name = "poll_count") val pollCount: Int = 0,
    @Json(name = "poll_option_count") val pollOptionCount: Int = 0,
    @Json(name = "note_count") val noteCount: Int = 0,
    @Json(name = "reminder_count") val reminderCount: Int = 0,
    @Json(name = "habit_count") val habitCount: Int = 0,
    @Json(name = "member_board_post_count") val memberBoardPostCount: Int = 0,
    @Json(name = "media_attachment_count") val mediaAttachmentCount: Int = 0,
    @Json(name = "media_blob_count") val mediaBlobCount: Int = 0,
)

// ── Async import job runner ──────────────────────────────────────────────────
//
// Backend wrapped every importer (SP / Sheaf / PK / TB) in a shared async
// job model. Submit returns a job row with status=pending; the worker runs
// the import out-of-band; clients poll the job until it lands on a terminal
// status. The legacy synchronous endpoints under `/v1/import/<source>` (no
// trailing `s`) were retired except for the preview shims, which the
// existing previewSimplyPluralImport / previewSheafImport calls still use.

/**
 * One row from `/v1/imports`. The shape is source-agnostic; the
 * source-specific counts (e.g. `members_imported`, `fronts_imported`)
 * land inside [counts] under their canonical key names, so callers can
 * decode straight into the source's result data class.
 */
@JsonClass(generateAdapter = true)
data class ImportJobRead(
    val id: String,
    val source: String,
    val status: String,
    val counts: Map<String, Int> = emptyMap(),
    val events: List<ImportJobEvent> = emptyList(),
    @Json(name = "started_at") val startedAt: String? = null,
    @Json(name = "finished_at") val finishedAt: String? = null,
    @Json(name = "last_error") val lastError: String? = null,
    @Json(name = "archived_at") val archivedAt: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
)

/**
 * Per-record event captured during the import — warnings get surfaced
 * to the user, errors get folded into the failure message.
 *
 * `recordRef` is source-specific (PK HID, SP member id, Sheaf UUID, etc.)
 * and null for parse-level / general events that don't tie to one record.
 */
@JsonClass(generateAdapter = true)
data class ImportJobEvent(
    val level: String,
    val stage: String,
    val message: String,
    @Json(name = "record_ref") val recordRef: String? = null,
)

/** String constants for ImportJobRead.status — pre-baked so callers don't
 *  hand-roll string comparisons against backend enum values. */
object ImportJobStatus {
    const val PENDING = "pending"
    const val RUNNING = "running"
    const val COMPLETE = "complete"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"
    val terminal = setOf(COMPLETE, FAILED, CANCELLED)
}

/** String constants for ImportJobRead.source / submit form field. */
object ImportJobSource {
    const val PLURALKIT_FILE = "pluralkit_file"
    const val PLURALKIT_API = "pluralkit_api"
    const val TUPPERBOX_FILE = "tupperbox_file"
    const val SIMPLYPLURAL_FILE = "simplyplural_file"
    const val SHEAF_FILE = "sheaf_file"
    // Complete Sheaf backup zip (export.json + images/). The preview endpoint
    // sniffs the zip magic and reports archive=true; submit under this source.
    const val SHEAF_ARCHIVE = "sheaf_archive"
    const val PLURALSPACE_FILE = "pluralspace_file"
    // Passphrase-encrypted .prism export; the passphrase rides as `credential`.
    const val PRISM_FILE = "prism_file"
    // OpenPlural v0.1 interchange file. One source for both the bare .json
    // and the .openplural.zip bundle; the runner sniffs the zip magic and
    // unpacks images when present (no separate archive source like Sheaf).
    const val OPENPLURAL_FILE = "openplural_file"
    const val AMPERSAND_FILE = "ampersand_file"
}

// ── Ampersand import ──────────────────────────────────────────────────────────
//
// Ampersand exports a plain .json file. The preview reports category counts
// only (no per-member list), so selective member import isn't offered on
// Android; every real member is imported. `groups` imports Ampersand systems as
// Sheaf groups; `board_messages` also gates polls.
@JsonClass(generateAdapter = true)
data class AmpersandPreviewSummary(
    @Json(name = "system_count") val systemCount: Int = 0,
    @Json(name = "member_count") val memberCount: Int = 0,
    @Json(name = "custom_front_count") val customFrontCount: Int = 0,
    @Json(name = "front_history_count") val frontHistoryCount: Int = 0,
    @Json(name = "tag_count") val tagCount: Int = 0,
    @Json(name = "custom_field_count") val customFieldCount: Int = 0,
    @Json(name = "journal_count") val journalCount: Int = 0,
    @Json(name = "note_count") val noteCount: Int = 0,
    @Json(name = "board_message_count") val boardMessageCount: Int = 0,
    @Json(name = "poll_count") val pollCount: Int = 0,
    @Json(name = "reminder_count") val reminderCount: Int = 0,
    @Json(name = "asset_count") val assetCount: Int = 0,
    @Json(name = "limit_warnings") val limitWarnings: List<String> = emptyList(),
)

// ── Export ──────────────────────────────────────────────────────────────────

/** Async full-backup (with images) request. Step-up: password always, plus
 *  totpCode when the account has 2FA. format is "sheaf_native" or
 *  "openplural" (note: the synchronous JSON export uses "sheaf"/"openplural"). */
@JsonClass(generateAdapter = true)
data class ExportJobRequest(
    @Json(name = "include_images") val includeImages: Boolean = true,
    val format: String,
    val password: String,
    @Json(name = "totp_code") val totpCode: String? = null,
)

@JsonClass(generateAdapter = true)
data class ExportJobRead(
    val id: String,
    @Json(name = "include_images") val includeImages: Boolean,
    val format: String,
    // pending | running | done | failed | expired
    val status: String,
    @Json(name = "requested_at") val requestedAt: String,
    @Json(name = "started_at") val startedAt: String? = null,
    @Json(name = "completed_at") val completedAt: String? = null,
    @Json(name = "expires_at") val expiresAt: String? = null,
    @Json(name = "file_size_bytes") val fileSizeBytes: Long? = null,
    val error: String? = null,
) {
    val isTerminal: Boolean get() = status == "done" || status == "failed" || status == "expired"
    val isDownloadable: Boolean get() = status == "done"
}

/**
 * Lighter shape of [ImportJobRead] for the history list. Drops the
 * events array which can run 10k entries on a large failing import;
 * detail screen fetches the full ImportJobRead on tap.
 */
@JsonClass(generateAdapter = true)
data class ImportJobSummary(
    val id: String,
    val source: String,
    val status: String,
    val counts: Map<String, Int> = emptyMap(),
    @Json(name = "started_at") val startedAt: String? = null,
    @Json(name = "finished_at") val finishedAt: String? = null,
    @Json(name = "archived_at") val archivedAt: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
)

/** Cursor-paginated response from /v1/imports. */
@JsonClass(generateAdapter = true)
data class ImportJobList(
    val items: List<ImportJobSummary>,
    @Json(name = "next_cursor") val nextCursor: String? = null,
)

// Credential-based submit (`/v1/imports/api`, used by PluralKit API) takes
// a JSON body with `source`, `idempotency_key`, `pk_token`, `options`. The
// options dict is mixed-typed (bools + an optional string list), which
// Moshi codegen doesn't model cleanly, so the viewmodel hand-rolls the
// body as a JSON string and the API method takes a RequestBody directly.

// ── Invite codes ─────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class InviteCodeRead(
    val id: String,
    val code: String,
    @Json(name = "max_uses") val maxUses: Int,
    @Json(name = "use_count") val useCount: Int,
    val note: String?,
    @Json(name = "expires_at") val expiresAt: String?,
    @Json(name = "created_at") val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class InviteCodeCreate(
    @Json(name = "max_uses") val maxUses: Int = 0,
    val note: String? = null,
    @Json(name = "expires_at") val expiresAt: String? = null,
)

// ── Announcements ─────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class AnnouncementPublic(
    val id: String,
    val title: String,
    val body: String,
    val severity: String,
    val dismissible: Boolean,
    @Json(name = "starts_at") val startsAt: String?,
    @Json(name = "expires_at") val expiresAt: String?,
    @Json(name = "created_at") val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class AnnouncementRead(
    val id: String,
    val title: String,
    val body: String,
    val severity: String,
    val dismissible: Boolean,
    val active: Boolean,
    @Json(name = "starts_at") val startsAt: String?,
    @Json(name = "expires_at") val expiresAt: String?,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    @Json(name = "created_by") val createdBy: String?,
)

@JsonClass(generateAdapter = true)
data class AnnouncementCreate(
    val title: String,
    val body: String,
    val severity: String = "info",
    val dismissible: Boolean = true,
    val active: Boolean = true,
    @Json(name = "starts_at") val startsAt: String? = null,
    @Json(name = "expires_at") val expiresAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class AnnouncementUpdate(
    val title: String? = null,
    val body: String? = null,
    val severity: String? = null,
    val dismissible: Boolean? = null,
    val active: Boolean? = null,
    @Json(name = "starts_at") val startsAt: String? = null,
    @Json(name = "expires_at") val expiresAt: String? = null,
    @Json(name = "clear_starts_at") val clearStartsAt: Boolean = false,
    @Json(name = "clear_expires_at") val clearExpiresAt: Boolean = false,
)

// ── Admin ─────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class AdminAuthStatus(
    val level: String,
    val verified: Boolean,
    @Json(name = "totp_enabled") val totpEnabled: Boolean,
)

@JsonClass(generateAdapter = true)
data class AdminStepUpVerify(
    val password: String? = null,
    @Json(name = "totp_code") val totpCode: String? = null,
)

@JsonClass(generateAdapter = true)
data class AdminStats(
    @Json(name = "total_users") val totalUsers: Int,
    @Json(name = "total_members") val totalMembers: Int,
    @Json(name = "total_storage_bytes") val totalStorageBytes: Long,
    @Json(name = "users_by_tier") val usersByTier: Map<String, Int>,
)

@JsonClass(generateAdapter = true)
data class AdminUserRead(
    val id: String,
    val email: String,
    val tier: String,
    @Json(name = "is_admin") val isAdmin: Boolean,
    @Json(name = "account_status") val accountStatus: String,
    @Json(name = "email_verified") val emailVerified: Boolean,
    @Json(name = "totp_enabled") val totpEnabled: Boolean,
    @Json(name = "signup_ip") val signupIp: String?,
    @Json(name = "member_limit") val memberLimit: Int?,
    @Json(name = "storage_used_bytes") val storageUsedBytes: Long,
    @Json(name = "member_count") val memberCount: Int,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "last_login_at") val lastLoginAt: String?,
    @Json(name = "suspended_until") val suspendedUntil: String? = null,
    @Json(name = "suspended_reason") val suspendedReason: String? = null,
)

@JsonClass(generateAdapter = true)
data class AdminUserUpdate(
    val tier: String? = null,
    @Json(name = "is_admin") val isAdmin: Boolean? = null,
    @Json(name = "member_limit") val memberLimit: Int? = null,
    @Json(name = "clear_member_limit") val clearMemberLimit: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class PendingUserRead(
    val id: String,
    val email: String,
    @Json(name = "email_verified") val emailVerified: Boolean,
    @Json(name = "signup_ip") val signupIp: String?,
    @Json(name = "created_at") val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class AdminResetPasswordRequest(
    // Backend requires a reason (1-500 chars) for the audit trail.
    val reason: String,
    @Json(name = "new_password") val newPassword: String? = null,
)

@JsonClass(generateAdapter = true)
data class AdminChangeEmailRequest(
    val reason: String,
    @Json(name = "new_email") val newEmail: String,
)

/**
 * Body shared by admin actions that require only an audited reason:
 * disable-totp, verify-email, cancel-deletion, unsuspend, ban, unban.
 */
@JsonClass(generateAdapter = true)
data class AdminReasonBody(
    val reason: String,
)

@JsonClass(generateAdapter = true)
data class AdminSuspendRequest(
    val reason: String,
    // Omitted = indefinite. Backend bounds it to 1-1825 days.
    @Json(name = "duration_days") val durationDays: Int? = null,
)

// ── Admin user diagnostics (explain / sessions / keys) ────────────────────────

@JsonClass(generateAdapter = true)
data class AdminExplainSystem(
    val id: String,
    val name: String,
    @Json(name = "member_count") val memberCount: Int,
    @Json(name = "delete_confirmation") val deleteConfirmation: String,
    @Json(name = "grace_period_days") val gracePeriodDays: Int,
)

@JsonClass(generateAdapter = true)
data class AdminExplainAuditRow(
    val id: String,
    val action: String,
    @Json(name = "target_type") val targetType: String,
    val reason: String? = null,
    @Json(name = "created_at") val createdAt: String,
)

/** One-shot triage dossier from GET /v1/admin/users/{id}/explain. */
@JsonClass(generateAdapter = true)
data class AdminExplainResponse(
    @Json(name = "user_id") val userId: String,
    val email: String,
    val tier: String,
    @Json(name = "is_admin") val isAdmin: Boolean,
    @Json(name = "account_status") val accountStatus: String,
    @Json(name = "email_verified") val emailVerified: Boolean,
    @Json(name = "totp_enabled") val totpEnabled: Boolean,
    @Json(name = "signup_ip") val signupIp: String? = null,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "last_login_at") val lastLoginAt: String? = null,
    @Json(name = "active_session_count") val activeSessionCount: Int = 0,
    @Json(name = "api_key_count") val apiKeyCount: Int = 0,
    val system: AdminExplainSystem? = null,
    @Json(name = "recent_admin_audit") val recentAdminAudit: List<AdminExplainAuditRow> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AdminSessionRow(
    // Opaque handle (not the raw session id); used for the terminate call.
    val id: String,
    @Json(name = "user_agent") val userAgent: String? = null,
    val ip: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "last_seen_at") val lastSeenAt: String? = null,
    val nickname: String? = null,
)

@JsonClass(generateAdapter = true)
data class AdminRotateAllResponse(
    @Json(name = "revoked_count") val revokedCount: Int = 0,
)

// ── Admin maintenance jobs ────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class AdminJobLastRun(
    @Json(name = "started_at") val startedAt: String,
    @Json(name = "finished_at") val finishedAt: String? = null,
    val status: String,
    @Json(name = "items_processed") val itemsProcessed: Int = 0,
    @Json(name = "duration_ms") val durationMs: Long? = null,
    @Json(name = "error_message") val errorMessage: String? = null,
)

@JsonClass(generateAdapter = true)
data class AdminJobRead(
    val name: String,
    val description: String,
    val enabled: Boolean = true,
    @Json(name = "interval_seconds") val intervalSeconds: Int = 0,
    @Json(name = "last_run") val lastRun: AdminJobLastRun? = null,
)

@JsonClass(generateAdapter = true)
data class AdminJobRunResponse(
    @Json(name = "job_name") val jobName: String? = null,
    val status: String,
    @Json(name = "items_processed") val itemsProcessed: Int = 0,
    @Json(name = "duration_ms") val durationMs: Long? = null,
    @Json(name = "error_message") val errorMessage: String? = null,
)

@JsonClass(generateAdapter = true)
data class AdminPushoverUsage(
    val month: String,
    val count: Int,
    val cap: Int,
    val enforced: Boolean,
)

// ── Admin bulk approve ────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class BulkApproveRequest(
    @Json(name = "user_ids") val userIds: List<String>,
)

@JsonClass(generateAdapter = true)
data class BulkApproveResult(
    @Json(name = "user_id") val userId: String,
    val approved: Boolean,
    val reason: String? = null,
)

@JsonClass(generateAdapter = true)
data class BulkApproveResponse(
    @Json(name = "approved_count") val approvedCount: Int = 0,
    val results: List<BulkApproveResult> = emptyList(),
)

// ── Admin emergency ops ───────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class AdminResetSafetyResponse(
    val reset: Boolean = false,
    @Json(name = "changed_fields") val changedFields: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AdminBypassPendingResponse(
    @Json(name = "finalized_count") val finalizedCount: Int = 0,
    @Json(name = "by_type") val byType: Map<String, Int> = emptyMap(),
)

// ── Admin import-job inspection ───────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class AdminImportJobSummary(
    val id: String,
    val source: String,
    val status: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "started_at") val startedAt: String? = null,
    @Json(name = "finished_at") val finishedAt: String? = null,
    val counts: Map<String, Int> = emptyMap(),
    @Json(name = "last_error") val lastError: String? = null,
)

/**
 * Full import-job view including the event log; from POST
 * /v1/admin/import-jobs/{id}. Reading it is privacy-sensitive (the events
 * can quote a member's data), so the endpoint requires a reason and writes
 * an audit row. Reuses [ImportJobEvent] for the events.
 */
@JsonClass(generateAdapter = true)
data class AdminImportJobDetail(
    val id: String,
    val source: String,
    val status: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "started_at") val startedAt: String? = null,
    @Json(name = "finished_at") val finishedAt: String? = null,
    val counts: Map<String, Int> = emptyMap(),
    @Json(name = "last_error") val lastError: String? = null,
    val events: List<ImportJobEvent> = emptyList(),
)

// ── Admin audit log ─────────────────────────────────────────────────────────
//
// before_json / after_json are arbitrary state snapshots, so they're typed as
// Any?-valued maps and (like CustomFieldValueRead) left as plain reflection-
// adapter data classes rather than @JsonClass codegen, which doesn't handle
// Any?. The model package is kept wholesale by proguard, so reflection
// survives R8.

data class AdminAuditEventRead(
    val id: String,
    @Json(name = "admin_user_id") val adminUserId: String? = null,
    @Json(name = "admin_email") val adminEmail: String? = null,
    val action: String,
    @Json(name = "target_type") val targetType: String,
    @Json(name = "target_id") val targetId: String? = null,
    @Json(name = "target_user_id") val targetUserId: String? = null,
    val reason: String? = null,
    @Json(name = "before_json") val beforeJson: Map<String, Any?>? = null,
    @Json(name = "after_json") val afterJson: Map<String, Any?>? = null,
    @Json(name = "created_at") val createdAt: String,
)

/**
 * The caller-facing slice of an audit event from GET /v1/auth/admin-activity:
 * admin actions taken against the authenticated user's own account. Omits the
 * acting admin's id and the (always-self) target_user_id that the admin view
 * carries.
 */
data class UserAdminActivityRead(
    val id: String,
    @Json(name = "admin_email") val adminEmail: String? = null,
    val action: String,
    @Json(name = "target_type") val targetType: String,
    @Json(name = "target_id") val targetId: String? = null,
    val reason: String? = null,
    @Json(name = "before_json") val beforeJson: Map<String, Any?>? = null,
    @Json(name = "after_json") val afterJson: Map<String, Any?>? = null,
    @Json(name = "created_at") val createdAt: String,
)

// ── Journals ──────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class JournalEntryRead(
    val id: String,
    @Json(name = "system_id") val systemId: String,
    @Json(name = "member_id") val memberId: String?,
    val title: String?,
    val body: String,
    val visibility: String,
    @Json(name = "author_user_id") val authorUserId: String?,
    @Json(name = "author_member_ids") val authorMemberIds: List<String> = emptyList(),
    @Json(name = "author_member_names") val authorMemberNames: List<String> = emptyList(),
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    // Set when a System Safety grace period has this queued for deletion.
    // Still returned and still usable until the window closes; the UI marks it.
    @Json(name = "pending_delete_at") val pendingDeleteAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class JournalEntryReadWithCount(
    val id: String,
    @Json(name = "system_id") val systemId: String,
    @Json(name = "member_id") val memberId: String?,
    val title: String?,
    val body: String,
    val visibility: String,
    @Json(name = "author_user_id") val authorUserId: String?,
    @Json(name = "author_member_ids") val authorMemberIds: List<String> = emptyList(),
    @Json(name = "author_member_names") val authorMemberNames: List<String> = emptyList(),
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    @Json(name = "revision_count") val revisionCount: Int = 0,
    // Mirrors JournalEntryRead: the detail screen reads this variant, so the
    // field has to exist on both or the entry looks safe once you open it.
    @Json(name = "pending_delete_at") val pendingDeleteAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class JournalEntryCreate(
    val body: String,
    val title: String? = null,
    @Json(name = "member_id") val memberId: String? = null,
    val visibility: String = "system",
    @Json(name = "author_member_ids") val authorMemberIds: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class JournalEntryUpdate(
    val title: String? = null,
    val body: String? = null,
    val visibility: String? = null,
    @Json(name = "author_member_ids") val authorMemberIds: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class JournalListResponse(
    val items: List<JournalEntryRead>,
    @Json(name = "next_cursor") val nextCursor: String? = null,
)

@JsonClass(generateAdapter = true)
data class JournalEntryDeleteConfirm(
    val password: String? = null,
    @Json(name = "totp_code") val totpCode: String? = null,
)

@JsonClass(generateAdapter = true)
data class JournalEntryDeletePending(
    @Json(name = "pending_action_id") val pendingActionId: String,
    @Json(name = "finalize_after") val finalizeAfter: String,
)

@JsonClass(generateAdapter = true)
data class ContentRevisionRead(
    val id: String,
    @Json(name = "target_type") val targetType: String,
    @Json(name = "target_id") val targetId: String,
    @Json(name = "user_id") val userId: String?,
    @Json(name = "editor_member_ids") val editorMemberIds: List<String> = emptyList(),
    @Json(name = "editor_member_names") val editorMemberNames: List<String> = emptyList(),
    val title: String?,
    val body: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "pinned_at") val pinnedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class RestoreRevisionRequest(
    @Json(name = "revision_id") val revisionId: String,
)

@JsonClass(generateAdapter = true)
data class PinRevisionRequest(
    @Json(name = "revision_id") val revisionId: String,
)

@JsonClass(generateAdapter = true)
data class UnpinRevisionRequest(
    @Json(name = "revision_id") val revisionId: String,
    val password: String? = null,
    @Json(name = "totp_code") val totpCode: String? = null,
)

/**
 * Backend returns either an immediate result (`revision` populated) or a queued
 * action (`pendingActionId` + `finalizeAfter`) when revision-safety is enabled
 * with a positive grace period.
 */
@JsonClass(generateAdapter = true)
data class UnpinRevisionResponse(
    val revision: ContentRevisionRead? = null,
    @Json(name = "pending_action_id") val pendingActionId: String? = null,
    @Json(name = "finalize_after") val finalizeAfter: String? = null,
)
