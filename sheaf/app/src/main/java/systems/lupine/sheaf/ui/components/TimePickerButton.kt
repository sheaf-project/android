package systems.lupine.sheaf.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * A button showing a time of day that opens the Material time picker.
 *
 * Shared so that every place in the app asking for a time of day asks for it
 * the same way: front history entries, and the schedule on a server
 * announcement. Pair it with a `DatePickerDialog` seeded through
 * [datePickerMillis] to build a full timestamp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerButton(time: LocalTime, onTimeChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }

    if (open) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute)
        TimePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(LocalTime.of(state.hour, state.minute))
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            title = { Text("Select time") },
        ) { TimePicker(state = state) }
    }

    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Text(time.format(DateTimeFormatter.ofPattern("h:mm a")))
    }
}
