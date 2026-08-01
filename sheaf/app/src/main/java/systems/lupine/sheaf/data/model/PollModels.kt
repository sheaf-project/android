package systems.lupine.sheaf.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PollOptionCreate(
    @Json(name = "text") val text: String,
)

@JsonClass(generateAdapter = true)
data class PollOptionRead(
    @Json(name = "id") val id: String,
    @Json(name = "text") val text: String,
    @Json(name = "position") val position: Int,
)

@JsonClass(generateAdapter = true)
data class PollCreate(
    @Json(name = "question") val question: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "kind") val kind: String,  // "single_choice" | "multi_choice"
    @Json(name = "results_visibility") val resultsVisibility: String,  // "live" | "end_only"
    @Json(name = "closes_at") val closesAt: String,  // ISO 8601 with tz offset
    @Json(name = "retention_days") val retentionDays: Int? = null,
    @Json(name = "include_custom_fronts") val includeCustomFronts: Boolean = false,
    // When true, voting is gated on the voted-as member being part of
    // the current front at vote/withdraw time. False (default) lets
    // any system member vote regardless of fronting state, matching
    // the journals/messages model.
    @Json(name = "restrict_voting_to_fronters") val restrictVotingToFronters: Boolean = false,
    @Json(name = "options") val options: List<PollOptionCreate>,
)

@JsonClass(generateAdapter = true)
data class PollTallyEntry(
    @Json(name = "option_id") val optionId: String,
    @Json(name = "count") val count: Int,
)

@JsonClass(generateAdapter = true)
data class PollVoteRead(
    @Json(name = "voted_as_member_id") val votedAsMemberId: String,
    @Json(name = "option_ids") val optionIds: List<String>,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
)

@JsonClass(generateAdapter = true)
data class PollRead(
    @Json(name = "id") val id: String,
    @Json(name = "system_id") val systemId: String,
    @Json(name = "question") val question: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "kind") val kind: String,
    @Json(name = "results_visibility") val resultsVisibility: String,
    @Json(name = "closes_at") val closesAt: String,
    @Json(name = "retention_days") val retentionDays: Int = 30,
    @Json(name = "include_custom_fronts") val includeCustomFronts: Boolean = false,
    @Json(name = "restrict_voting_to_fronters") val restrictVotingToFronters: Boolean = false,
    @Json(name = "options") val options: List<PollOptionRead> = emptyList(),
    @Json(name = "is_closed") val isClosed: Boolean = false,
    @Json(name = "closed_since") val closedSince: String? = null,
    @Json(name = "purges_at") val purgesAt: String,
    @Json(name = "total_votes") val totalVotes: Int = 0,
    // Tally is null when results_visibility=end_only and poll is still open.
    @Json(name = "tally") val tally: List<PollTallyEntry>? = null,
    // Per-member votes. Only populated for the owner. Hidden under the same
    // visibility rule as tally.
    @Json(name = "votes") val votes: List<PollVoteRead>? = null,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    // Set when a System Safety grace period has this queued for deletion.
    // Still returned and still usable until the window closes; the UI marks it.
    @Json(name = "pending_delete_at") val pendingDeleteAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class VoteCast(
    @Json(name = "voted_as_member_id") val votedAsMemberId: String,
    @Json(name = "option_ids") val optionIds: List<String>,
)
