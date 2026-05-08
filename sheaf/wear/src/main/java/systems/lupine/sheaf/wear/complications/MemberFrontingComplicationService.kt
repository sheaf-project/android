package systems.lupine.sheaf.wear.complications

import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/**
 * Per-member "is X fronting?" complication. Configurable: when the user
 * adds it to a watchface, [MemberPickerConfigActivity] runs and asks them
 * which member this slot represents. The selection is keyed by complication
 * instance id so two slots can track different members independently.
 *
 * Renders:
 * - SHORT_TEXT: "Alice ✓" / "Alice ✗" with title "front"
 * - LONG_TEXT: "Alice · 9h" / "Alice · off" with title "Member front"
 *
 * Tap action opens MainActivity (which routes to the member's profile if
 * we ever wire deep-linking that far; for now lands on home).
 */
class MemberFrontingComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, name = "Member", isFronting = true, durationLabel = "9h", emoji = "")

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val instanceId = request.complicationInstanceId
        // Pre-pick state: instance hasn't been configured yet (e.g. running
        // in the picker preview before the config activity has committed).
        val memberId = loadMemberConfig(this, instanceId)
        Log.d(
            TAG,
            "onComplicationRequest: instanceId=$instanceId memberId=$memberId type=${request.complicationType}",
        )
        if (memberId == null) return NoDataComplicationData()

        val members = readMembersSnapshot(this) ?: return NoDataComplicationData()
        val member = members.firstOrNull { it.id == memberId }
            ?: return NoDataComplicationData()

        val fronters = readFrontersSnapshot(this).orEmpty()
        val fronterRow = fronters.firstOrNull { it.id == memberId }
        val isFronting = fronterRow != null
        val durationLabel = fronterRow?.since?.takeIf { it.isNotBlank() }
            ?.let(::timeAgoOrNull) ?: ""

        return build(type = request.complicationType, name = member.name,
            isFronting = isFronting, durationLabel = durationLabel, emoji = member.emoji)
    }

    private fun build(
        type: ComplicationType,
        name: String,
        isFronting: Boolean,
        durationLabel: String,
        emoji: String,
    ): ComplicationData? {
        val tap = openAppPendingIntent(this, REQUEST_CODE_BASE)
        val description = PlainComplicationText.Builder(
            if (isFronting) "$name is fronting" else "$name is not fronting"
        ).build()

        // Pull the visible glyph: emoji if set, falls back to first letter
        // of the name. Glyph + state mark in 2-3 chars is the most we can
        // show on a SHORT_TEXT slot before the name eats the budget.
        val glyph = emoji.takeIf { it.isNotBlank() }
            ?: name.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        val mark = if (isFronting) "✓" else "✗"

        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                // 8-char budget. Fit "name mark" with name truncated as needed.
                val shortText = fitMemberShort(name, mark)
                ShortTextComplicationData.Builder(
                    PlainComplicationText.Builder(shortText).build(),
                    description,
                )
                    .setTitle(PlainComplicationText.Builder("front").build())
                    .setTapAction(tap)
                    .build()
            }

            ComplicationType.LONG_TEXT -> {
                val tail = when {
                    isFronting && durationLabel.isNotBlank() -> durationLabel
                    isFronting -> "fronting"
                    else -> "off"
                }
                val text = if (glyph.isNotBlank()) "$glyph $name · $tail" else "$name · $tail"
                LongTextComplicationData.Builder(
                    PlainComplicationText.Builder(text).build(),
                    description,
                )
                    .setTitle(PlainComplicationText.Builder("Member front").build())
                    .setTapAction(tap)
                    .build()
            }

            else -> null
        }
    }

    private companion object {
        const val REQUEST_CODE_BASE = 1007
        const val TAG = "SheafMemberFronting"
    }
}

/**
 * Format a member name + state mark for SHORT_TEXT so it fits the typical
 * 8-char slot. Trims the name as needed but always keeps the trailing mark
 * visible so the user can tell whether the member is fronting.
 */
internal fun fitMemberShort(name: String, mark: String): String {
    val budget = 8
    val tail = " $mark"
    val nameRoom = (budget - tail.length).coerceAtLeast(1)
    return name.take(nameRoom) + tail
}
