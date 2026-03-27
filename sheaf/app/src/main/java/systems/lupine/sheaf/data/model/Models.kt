package systems.lupine.sheaf.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.OffsetDateTime

// ── Auth ─────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class UserRegister(
    val email: String,
    val password: String,
    @Json(name = "invite_code") val inviteCode: String? = null,
)

@JsonClass(generateAdapter = true)
data class UserLogin(
    val email: String,
    val password: String,
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
data class UserRead(
    val id: String,
    val email: String,
    @Json(name = "totp_enabled") val totpEnabled: Boolean,
    @Json(name = "is_admin") val isAdmin: Boolean = false,
    val tier: String,
    @Json(name = "account_status") val accountStatus: String = "active",
    @Json(name = "email_verified") val emailVerified: Boolean = true,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "last_login_at") val lastLoginAt: String?,
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

// ── Members ───────────────────────────────────────────────────────────────────

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

@JsonClass(generateAdapter = true)
data class FileUploadResponse(val url: String)

// ── API Keys ──────────────────────────────────────────────────────────────────

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
data class ApiKeyCreate(
    val name: String,
    val scopes: List<String>,
    @Json(name = "expires_at") val expiresAt: String? = null,
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

// ── Admin ─────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class AdminStats(
    @Json(name = "total_users") val totalUsers: Int,
    @Json(name = "total_members") val totalMembers: Int,
    @Json(name = "total_storage_bytes") val totalStorageBytes: Long,
    @Json(name = "users_by_tier") val usersByTier: Map<String, Int>,
)

@JsonClass(generateAdapter = true)
data class AdminAuthStatus(
    val level: String = "none",
    val verified: Boolean = true,
    @Json(name = "totp_enabled") val totpEnabled: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class AdminStepUpVerify(
    val password: String? = null,
    @Json(name = "totp_code") val totpCode: String? = null,
)

@JsonClass(generateAdapter = true)
data class AdminUserRead(
    val id: String,
    val email: String,
    val tier: String,
    @Json(name = "is_admin") val isAdmin: Boolean,
    @Json(name = "account_status") val accountStatus: String,
    @Json(name = "email_verified") val emailVerified: Boolean,
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

// ── Sheaf import ──────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class SheafPreviewSummary(
    @Json(name = "system_name") val systemName: String?,
    @Json(name = "member_count") val memberCount: Int,
    val members: List<SPPreviewMember>,
    @Json(name = "front_count") val frontCount: Int,
    @Json(name = "group_count") val groupCount: Int,
    @Json(name = "custom_field_count") val customFieldCount: Int,
    @Json(name = "tag_count") val tagCount: Int,
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
