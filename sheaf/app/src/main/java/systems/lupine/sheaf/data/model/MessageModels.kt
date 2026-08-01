package systems.lupine.sheaf.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MessageCreate(
    @Json(name = "body") val body: String,
    @Json(name = "board_kind") val boardKind: String,  // "system" | "member"
    @Json(name = "board_member_id") val boardMemberId: String? = null,
    @Json(name = "author_member_id") val authorMemberId: String,
    @Json(name = "parent_message_id") val parentMessageId: String? = null,
)

@JsonClass(generateAdapter = true)
data class MessageUpdate(
    @Json(name = "body") val body: String,
)

@JsonClass(generateAdapter = true)
data class MessageRead(
    @Json(name = "id") val id: String,
    @Json(name = "system_id") val systemId: String,
    @Json(name = "board_kind") val boardKind: String,
    @Json(name = "board_member_id") val boardMemberId: String? = null,
    @Json(name = "author_member_id") val authorMemberId: String? = null,
    @Json(name = "author_member_name") val authorMemberName: String? = null,
    @Json(name = "parent_message_id") val parentMessageId: String? = null,
    @Json(name = "parent_preview") val parentPreview: String? = null,
    @Json(name = "parent_author_member_name") val parentAuthorMemberName: String? = null,
    @Json(name = "body") val body: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    // Set when a System Safety grace period has this queued for deletion.
    // Still returned and still usable until the window closes; the UI marks it.
    @Json(name = "pending_delete_at") val pendingDeleteAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class MessagesPage(
    @Json(name = "board_kind") val boardKind: String,
    @Json(name = "board_member_id") val boardMemberId: String? = null,
    @Json(name = "messages") val messages: List<MessageRead> = emptyList(),
    @Json(name = "caller_last_seen_at") val callerLastSeenAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class BoardSummary(
    @Json(name = "board_kind") val boardKind: String,
    @Json(name = "board_member_id") val boardMemberId: String? = null,
    @Json(name = "member_name") val memberName: String? = null,
    @Json(name = "last_message_at") val lastMessageAt: String? = null,
    @Json(name = "last_message_preview") val lastMessagePreview: String? = null,
    @Json(name = "message_count") val messageCount: Int = 0,
    @Json(name = "unread_count") val unreadCount: Int = 0,
)

@JsonClass(generateAdapter = true)
data class MarkSeenRequest(
    @Json(name = "member_id") val memberId: String,
    @Json(name = "board_kind") val boardKind: String,
    @Json(name = "board_member_id") val boardMemberId: String? = null,
)
