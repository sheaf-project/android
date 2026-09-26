package systems.lupine.sheaf.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Hour / minute / AM-PM, as a row of fields.
 *
 * Shared so that every place in the app asking for a time of day asks for it
 * the same way: front history entries, and the schedule on a server
 * announcement. Pair it with a `DatePickerDialog` seeded through
 * [datePickerMillis] to build a full timestamp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeInputRow(time: LocalTime, onTimeChange: (LocalTime) -> Unit) {
    // These must NOT be keyed on `time`: every accepted keystroke calls
    // onTimeChange, which updates the parent `time`, which would re-key the
    // remember, replace this text state, and snap the cursor back to the
    // start, making the field impossible to type into. The fields are the
    // source of truth while editing and `time` only ever changes via them.
    var hourText by remember { mutableStateOf(time.format(DateTimeFormatter.ofPattern("h"))) }
    var minuteText by remember { mutableStateOf(time.format(DateTimeFormatter.ofPattern("mm"))) }
    var isPm by remember { mutableStateOf(time.hour >= 12) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = hourText,
            onValueChange = { v ->
                if (v.length <= 2 && v.all { it.isDigit() }) {
                    hourText = v
                    val h = v.toIntOrNull() ?: return@OutlinedTextField
                    if (h in 1..12) {
                        val hour24 = if (isPm) { if (h == 12) 12 else h + 12 } else { if (h == 12) 0 else h }
                        onTimeChange(time.withHour(hour24).withMinute(minuteText.toIntOrNull() ?: time.minute))
                    }
                }
            },
            label = { Text("Hour") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        Text(":", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = minuteText,
            onValueChange = { v ->
                if (v.length <= 2 && v.all { it.isDigit() }) {
                    minuteText = v
                    val m = v.toIntOrNull() ?: return@OutlinedTextField
                    if (m in 0..59) onTimeChange(time.withMinute(m))
                }
            },
            label = { Text("Min") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            SegmentedButton(
                selected = !isPm,
                onClick = {
                    isPm = false
                    val h = if (time.hour >= 12) time.hour - 12 else time.hour
                    onTimeChange(time.withHour(if (h == 0) 0 else h))
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("AM") }
            SegmentedButton(
                selected = isPm,
                onClick = {
                    isPm = true
                    val h = if (time.hour < 12) time.hour + 12 else time.hour
                    onTimeChange(time.withHour(h))
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("PM") }
        }
    }
}
