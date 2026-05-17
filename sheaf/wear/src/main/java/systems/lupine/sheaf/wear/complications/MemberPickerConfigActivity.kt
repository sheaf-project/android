package systems.lupine.sheaf.wear.complications

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import systems.lupine.sheaf.wear.theme.SheafWearTheme

/**
 * Launched by the watchface picker when the user adds a
 * [MemberFrontingComplicationService] to a slot. Shows a list of members
 * (from the snapshot WearStore writes after each loadAll) and persists the
 * user's selection keyed by complication instance id.
 *
 * If the snapshot is empty (Sheaf has never been opened on this watch), we
 * tell the user to open the app and finish the activity with no result so
 * the picker re-prompts on next attempt.
 */
class MemberPickerConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The picker passes the instance id we'll save the member-id under.
        // INVALID_COMPLICATION_INSTANCE_ID means we were launched from
        // somewhere unexpected; bail.
        val instanceId = intent.getIntExtra(
            ComplicationDataSourceService.EXTRA_CONFIG_COMPLICATION_ID,
            -1,
        )
        Log.d(
            TAG,
            "config activity launched: instanceId=$instanceId extras=${intent.extras?.keySet()}",
        )
        if (instanceId == -1) {
            Log.w(TAG, "no instance id in launch intent — config aborted")
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val members = readMembersSnapshot(this).orEmpty()
        Log.d(TAG, "loaded ${members.size} members from snapshot for picker")

        setContent {
            SheafWearTheme {
                MemberPicker(
                    members = members,
                    onPick = { memberId ->
                        Log.d(TAG, "picked memberId=$memberId for instanceId=$instanceId")
                        saveMemberConfig(this, instanceId, memberId)
                        setResult(Activity.RESULT_OK)
                        finish()
                    },
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                )
            }
        }
    }

    private companion object {
        const val TAG = "SheafComplicationConfig"
    }
}

@Composable
private fun MemberPicker(
    members: List<MemberRow>,
    onPick: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = "Pick a member",
                    style = MaterialTheme.typography.title3,
                )
            }
            if (members.isEmpty()) {
                item {
                    Text(
                        text = "Open Sheaf on the watch first to load members.",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface,
                    )
                }
                item {
                    Chip(
                        label = { Text("Cancel") },
                        onClick = onCancel,
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                return@ScalingLazyColumn
            }

            items(members, key = { it.id }) { m ->
                // Hoist id and label into local vals so the onClick lambda
                // captures THIS iteration's values, not whatever the loop
                // happens to be on at click time. Belt-and-suspenders for
                // a Compose iteration-capture quirk that's been blamed for
                // "all clicks fire the last item" reports.
                val pickedId = m.id
                val emoji = m.emoji.takeIf { it.isNotBlank() }
                val labelText = if (emoji != null) "$emoji ${m.name}" else m.name
                Chip(
                    label = { Text(labelText) },
                    onClick = { onPick(pickedId) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Chip(
                    label = { Text("Cancel") },
                    onClick = onCancel,
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
