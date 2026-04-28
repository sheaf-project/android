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
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
)

@JsonClass(generateAdapter = true)
data class SystemUpdate(
    val name: String? = null,
    val description: String? = null,
    val tag: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    val color: String? = null,
    val privacy: String? = null,
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

// `changes` is an arbitrary JSON object — kept untyped, handled by KotlinJsonAdapterFactory.
data class SafetyChangeRequestRead(
    val id: String,
    @Json(name = "requested_at") val requestedAt: String,
    @Json(name = "requested_by_user_id") val requestedByUserId: String?,
    @Json(name = "finalize_after") val finalizeAfter: String,
    val changes: Map<String, Any?>,
    val status: String,
)

data class SystemSafetyResponse(
    val settings: SystemSafetySettings,
    @Json(name = "pending_actions") val pendingActions: List<PendingActionRead>,
    @Json(name = "pending_changes") val pendingChanges: List<SafetyChangeRequestRead>,
)

data class SystemSafetyUpdateResponse(
    val settings: SystemSafetySettings,
    val applied: List<String>,
    val deferred: List<String>,
    @Json(name = "pending_change") val pendingChange: SafetyChangeRequestRead?,
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
    val color: String?,
    val birthday: String?,
    val privacy: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
) {
    val displayNameOrName: String get() = displayName?.takeIf { it.isNotBlank() } ?: name
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
    val color: String? = null,
    val birthday: String? = null,
    val privacy: String = "private",
)

@JsonClass(generateAdapter = true)
data class MemberUpdate(
    val name: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
    val description: String? = null,
    val pronouns: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    val color: String? = null,
    val birthday: String? = null,
    val privacy: String? = null,
)

// ── Fronts ────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class FrontRead(
    val id: String,
    @Json(name = "system_id") val systemId: String,
    @Json(name = "started_at") val startedAt: String,
    @Json(name = "ended_at") val endedAt: String?,
    @Json(name = "member_ids") val memberIds: List<String>,
)

@JsonClass(generateAdapter = true)
data class FrontCreate(
    @Json(name = "member_ids") val memberIds: List<String>,
    @Json(name = "started_at") val startedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class FrontUpdate(
    @Json(name = "ended_at") val endedAt: String? = null,
    @Json(name = "member_ids") val memberIds: List<String>? = null,
    @Json(name = "started_at") val startedAt: String? = null,
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

// ── Custom Fields ─────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class CustomFieldRead(
    val id: String,
    @Json(name = "system_id") val systemId: String,
    val name: String,
    @Json(name = "field_type") val fieldType: String,
    val order: Int,
    val privacy: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
) {
    val fieldTypeDisplay: String get() = fieldType.replaceFirstChar { it.uppercase() }
    val privacyDisplay: String get() = privacy.replaceFirstChar { it.uppercase() }
}

@JsonClass(generateAdapter = true)
data class CustomFieldCreate(
    val name: String,
    @Json(name = "field_type") val fieldType: String,
    val order: Int? = null,
    val privacy: String = "private",
)

@JsonClass(generateAdapter = true)
data class CustomFieldUpdate(
    val name: String? = null,
    val privacy: String? = null,
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
)

@JsonClass(generateAdapter = true)
data class SheafImportResult(
    @Json(name = "members_imported") val membersImported: Int,
    @Json(name = "fronts_imported") val frontsImported: Int,
    @Json(name = "groups_imported") val groupsImported: Int,
    @Json(name = "tags_imported") val tagsImported: Int,
    @Json(name = "custom_fields_imported") val customFieldsImported: Int,
    val warnings: List<String>,
)

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
    @Json(name = "new_password") val newPassword: String? = null,
)

@JsonClass(generateAdapter = true)
data class AdminChangeEmailRequest(
    @Json(name = "new_email") val newEmail: String,
)
