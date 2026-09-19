package systems.lupine.sheaf.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Owner-side share views and grants. The public viewer surface is deliberately
// absent: a shared link renders in any mobile browser, so the app ports the
// controls only.
//
// `token_hash` never appears in any response. A link token exists in plaintext
// exactly once, in ShareGrantCreated.token, and cannot be read back afterwards.

@JsonClass(generateAdapter = true)
data class ShareViewMemberRead(
    val id: String,
    @Json(name = "member_id") val memberId: String,
    val status: String,
    @Json(name = "activates_at") val activatesAt: String? = null,
    // Whether the projection actually serves this person right now. Computed
    // server-side out of the projection's own filter, so it already accounts
    // for archived and deletion-queued members, not just privacy.
    val served: Boolean = true,
    // One of NotServedReason when served is false. Null while served, and also
    // when the projection excluded them for a reason it cannot name.
    @Json(name = "not_served_reason") val notServedReason: String? = null,
    // Which group expansion put them here, null for a hand-picked row. This is
    // what detaching that group takes with it.
    @Json(name = "added_via_group_id") val addedViaGroupId: String? = null,
)

@JsonClass(generateAdapter = true)
data class ShareViewFieldRead(
    val id: String,
    @Json(name = "field_id") val fieldId: String,
    val status: String,
    @Json(name = "activates_at") val activatesAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class ShareViewGroupRead(
    val id: String,
    @Json(name = "group_id") val groupId: String,
    @Json(name = "synced_at") val syncedAt: String,
)

@JsonClass(generateAdapter = true)
data class ShareViewRead(
    val id: String,
    val name: String,
    @Json(name = "include_members") val includeMembers: Boolean,
    @Json(name = "include_bio") val includeBio: Boolean,
    @Json(name = "include_fronting") val includeFronting: Boolean,
    @Json(name = "fronting_show_count") val frontingShowCount: Boolean,
    @Json(name = "include_relationships") val includeRelationships: Boolean,
    @Json(name = "include_groups") val includeGroups: Boolean,
    // Instant in both directions and never staged: it exposes nothing new, only
    // a stable address for members the roster already shows.
    @Json(name = "member_permalinks") val memberPermalinks: Boolean,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "is_shared") val isShared: Boolean,
    // A flag flip that exposes more on an already-shared view is staged. The
    // live flag above is still the truth; these say what it becomes at
    // flagsActivateAt. Null = nothing staged.
    @Json(name = "pending_include_bio") val pendingIncludeBio: Boolean? = null,
    @Json(name = "pending_include_fronting") val pendingIncludeFronting: Boolean? = null,
    @Json(name = "pending_fronting_show_count") val pendingFrontingShowCount: Boolean? = null,
    @Json(name = "pending_include_relationships") val pendingIncludeRelationships: Boolean? = null,
    @Json(name = "pending_include_members") val pendingIncludeMembers: Boolean? = null,
    @Json(name = "pending_include_groups") val pendingIncludeGroups: Boolean? = null,
    @Json(name = "flags_activate_at") val flagsActivateAt: String? = null,
    val members: List<ShareViewMemberRead> = emptyList(),
    val fields: List<ShareViewFieldRead> = emptyList(),
    val groups: List<ShareViewGroupRead> = emptyList(),
) {
    val hasPendingFlags: Boolean get() = flagsActivateAt != null
}

@JsonClass(generateAdapter = true)
data class ShareViewCreate(
    val name: String,
    @Json(name = "include_members") val includeMembers: Boolean = true,
    @Json(name = "include_bio") val includeBio: Boolean = false,
    @Json(name = "include_fronting") val includeFronting: Boolean = false,
    @Json(name = "fronting_show_count") val frontingShowCount: Boolean = true,
    @Json(name = "include_relationships") val includeRelationships: Boolean = false,
    @Json(name = "include_groups") val includeGroups: Boolean = false,
    @Json(name = "member_permalinks") val memberPermalinks: Boolean = false,
)

// Password and TOTP are only consulted when the change is actually deferred.
// Sending them on a change that turns out not to need them is harmless.
@JsonClass(generateAdapter = true)
data class ShareViewUpdate(
    val name: String? = null,
    @Json(name = "include_members") val includeMembers: Boolean? = null,
    @Json(name = "include_bio") val includeBio: Boolean? = null,
    @Json(name = "include_fronting") val includeFronting: Boolean? = null,
    @Json(name = "fronting_show_count") val frontingShowCount: Boolean? = null,
    @Json(name = "include_relationships") val includeRelationships: Boolean? = null,
    @Json(name = "include_groups") val includeGroups: Boolean? = null,
    @Json(name = "member_permalinks") val memberPermalinks: Boolean? = null,
    val password: String? = null,
    @Json(name = "totp_code") val totpCode: String? = null,
)

@JsonClass(generateAdapter = true)
data class ShareViewMemberAdd(
    @Json(name = "member_id") val memberId: String,
    val password: String? = null,
    @Json(name = "totp_code") val totpCode: String? = null,
)

@JsonClass(generateAdapter = true)
data class ShareViewFieldAdd(
    @Json(name = "field_id") val fieldId: String,
    val password: String? = null,
    @Json(name = "totp_code") val totpCode: String? = null,
)

@JsonClass(generateAdapter = true)
data class ShareViewGroupAdd(
    @Json(name = "group_id") val groupId: String,
    val password: String? = null,
    @Json(name = "totp_code") val totpCode: String? = null,
)

// Skip counts are surfaced rather than swallowed so the user is told plainly
// that part of the group did not go in.
@JsonClass(generateAdapter = true)
data class ShareViewGroupAddResult(
    val added: Int,
    @Json(name = "skipped_never_shareable") val skippedNeverShareable: Int,
    @Json(name = "skipped_not_public") val skippedNotPublic: Int,
)

@JsonClass(generateAdapter = true)
data class ShareGrantCreate(
    @Json(name = "view_id") val viewId: String,
    @Json(name = "subject_type") val subjectType: String,
    val note: String? = null,
    @Json(name = "expires_at") val expiresAt: String? = null,
    val password: String? = null,
    @Json(name = "totp_code") val totpCode: String? = null,
)

@JsonClass(generateAdapter = true)
data class ShareGrantRead(
    val id: String,
    @Json(name = "view_id") val viewId: String,
    @Json(name = "subject_type") val subjectType: String,
    val note: String?,
    val status: String,
    @Json(name = "activates_at") val activatesAt: String?,
    @Json(name = "expires_at") val expiresAt: String?,
    @Json(name = "revoked_at") val revokedAt: String?,
    @Json(name = "created_at") val createdAt: String,
)

// `token` is populated for link grants only, and only here. Show it to the user
// immediately; nothing can retrieve it again.
@JsonClass(generateAdapter = true)
data class ShareGrantCreated(
    val grant: ShareGrantRead,
    val token: String? = null,
)

@JsonClass(generateAdapter = true)
data class ShareAuditEntry(
    val grant: ShareGrantRead,
    @Json(name = "view_id") val viewId: String,
    @Json(name = "view_name") val viewName: String,
    // Curated: how many members the owner put in. Describes their curation,
    // not what a visitor gets.
    @Json(name = "member_count") val memberCount: Int,
    // Served: how many of those the projection would actually show. Null when
    // the roster is off entirely, because a roster the view refuses to serve
    // must not be countable.
    @Json(name = "served_member_count") val servedMemberCount: Int? = null,
    @Json(name = "field_count") val fieldCount: Int,
    @Json(name = "include_members") val includeMembers: Boolean,
    @Json(name = "include_bio") val includeBio: Boolean,
    @Json(name = "include_fronting") val includeFronting: Boolean,
    @Json(name = "include_relationships") val includeRelationships: Boolean,
    @Json(name = "include_groups") val includeGroups: Boolean,
    @Json(name = "member_permalinks") val memberPermalinks: Boolean,
    @Json(name = "relationship_count") val relationshipCount: Int,
    @Json(name = "group_count") val groupCount: Int,
)

@JsonClass(generateAdapter = true)
data class ShareAudit(
    val entries: List<ShareAuditEntry> = emptyList(),
    // Why nothing above is being served, or null when the entries describe live
    // exposure. Account-level, so it suppresses every grant at once.
    @Json(name = "profile_suppressed") val profileSuppressed: String? = null,
)

// Preview of one view as a visitor receives it, from the same projection the
// anonymous router uses. A null section is this bundle's spelling of the
// anonymous surface's 404: the section is unaddressable, which is a different
// thing from an empty list.
@JsonClass(generateAdapter = true)
data class SharePreview(
    val system: PublicSystemView,
    val members: List<PublicMemberView>? = null,
    val fronting: PublicFrontingView? = null,
    val relationships: PublicRelationshipsView? = null,
    val groups: PublicGroupsView? = null,
)

@JsonClass(generateAdapter = true)
data class PublicSystemView(
    val id: String? = null,
    val name: String,
    val description: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    val color: String? = null,
    val tag: String? = null,
    @Json(name = "member_count") val memberCount: Int? = null,
    @Json(name = "member_permalinks") val memberPermalinks: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class PublicMemberField(
    val name: String,
    val value: String,
)

@JsonClass(generateAdapter = true)
data class PublicMemberView(
    val id: String,
    val name: String,
    val pronouns: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "banner_url") val bannerUrl: String? = null,
    val color: String? = null,
    val bio: String? = null,
    // A list rather than a name-keyed map so two fields sharing a name both
    // reach the card.
    val fields: List<PublicMemberField> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PublicFrontingMember(
    val id: String,
    val name: String,
    val pronouns: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    val color: String? = null,
    val since: String? = null,
)

@JsonClass(generateAdapter = true)
data class PublicFrontingView(
    val members: List<PublicFrontingMember> = emptyList(),
    @Json(name = "hidden_count") val hiddenCount: Int = 0,
)

@JsonClass(generateAdapter = true)
data class PublicRelationshipEndpoint(
    val id: String,
    val name: String,
)

@JsonClass(generateAdapter = true)
data class PublicRelationship(
    val id: String,
    @Json(name = "type_name") val typeName: String,
    @Json(name = "type_color") val typeColor: String? = null,
    val source: PublicRelationshipEndpoint,
    val target: PublicRelationshipEndpoint,
    @Json(name = "source_label") val sourceLabel: String,
    @Json(name = "target_label") val targetLabel: String,
    val mutual: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class PublicRelationshipsView(
    val relationships: List<PublicRelationship> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PublicGroupMember(
    val id: String,
    val name: String,
)

@JsonClass(generateAdapter = true)
data class PublicGroupView(
    val id: String,
    val name: String,
    val description: String? = null,
    val color: String? = null,
    val members: List<PublicGroupMember> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PublicGroupsView(
    val groups: List<PublicGroupView> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AdultAttestationRead(
    @Json(name = "adult_attested_at") val adultAttestedAt: String?,
)
