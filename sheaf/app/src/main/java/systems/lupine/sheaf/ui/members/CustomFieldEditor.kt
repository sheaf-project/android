package systems.lupine.sheaf.ui.members

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import systems.lupine.sheaf.data.model.CustomFieldRead
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Edits a single custom field's value for one member. The value is
 * typed [Any?] on the wire so this dispatcher picks the right input
 * widget from [CustomFieldRead.fieldType] + [CustomFieldRead.options].
 *
 * Eight variants:
 *  - text       : single-line free text
 *  - number     : numeric keyboard, accepts the empty string for clear
 *  - date       : picker dialog, stores ISO yyyy-MM-dd
 *  - boolean    : Switch
 *  - select with choices    : ExposedDropdownMenu
 *  - select freeform        : single-line free text
 *  - multiselect with choices : FilterChip group
 *  - multiselect freeform   : chip input + add-by-text affordance
 *
 * Empty / blank string -> null. Server treats null as "clear this
 * field" so toggling a value out then saving removes it cleanly.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CustomFieldEditor(
    field: CustomFieldRead,
    value: Any?,
    onChange: (Any?) -> Unit,
) {
    val choices = field.options?.choices
    when (field.fieldType) {
        "text" -> TextEditor(label = field.name, value = value as? String, onChange = onChange)
        "number" -> NumberEditor(label = field.name, value = value, onChange = onChange)
        "date" -> DateEditor(label = field.name, value = value as? String, onChange = onChange)
        "boolean" -> BooleanEditor(label = field.name, value = value as? Boolean, onChange = onChange)
        "select" ->
            if (choices.isNullOrEmpty()) {
                TextEditor(label = field.name, value = value as? String, onChange = onChange)
            } else {
                SelectEditor(label = field.name, choices = choices, value = value as? String, onChange = onChange)
            }
        "multiselect" ->
            if (choices.isNullOrEmpty()) {
                MultiselectFreeformEditor(label = field.name, value = value.coerceToStringList(), onChange = onChange)
            } else {
                MultiselectChoicesEditor(label = field.name, choices = choices, value = value.coerceToStringList(), onChange = onChange)
            }
        else -> Text(
            "Unknown field type: ${field.fieldType}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** Coerce a multiselect server value into List<String>. Defensively
 *  handles the case where Moshi reflects a List<Any?> rather than the
 *  concrete List<String> — values inside are still strings, we just
 *  filter-and-cast. */
@Suppress("UNCHECKED_CAST")
private fun Any?.coerceToStringList(): List<String> = when (this) {
    null -> emptyList()
    is List<*> -> mapNotNull { it as? String }
    else -> emptyList()
}

@Composable
private fun TextEditor(label: String, value: String?, onChange: (Any?) -> Unit) {
    OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = { onChange(it.ifBlank { null }) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

/**
 * Number editor — keeps the raw user input as a string in viewmodel
 * state so half-typed numbers don't get lost, but emits a parsed
 * Double (or the string verbatim if it didn't parse) on change. Empty
 * string clears.
 *
 * Backend stores numbers as JSON numbers; sending a string-of-digits
 * would 422 on the per-type validator. So we parse before submit.
 */
@Composable
private fun NumberEditor(label: String, value: Any?, onChange: (Any?) -> Unit) {
    val display = when (value) {
        null -> ""
        is Number -> value.toString()
        is String -> value
        else -> value.toString()
    }
    OutlinedTextField(
        value = display,
        onValueChange = { raw ->
            val trimmed = raw.trim()
            when {
                trimmed.isEmpty() -> onChange(null)
                else -> {
                    // Accept both 5 and 5.0; bias toward Long when no
                    // decimal point to keep small integers as ints on
                    // the wire (server doesn't care, but reads nicer
                    // in audit history).
                    val parsed: Number? = trimmed.toLongOrNull() ?: trimmed.toDoubleOrNull()
                    onChange(parsed ?: trimmed)  // Keep raw string while user types invalid intermediates
                }
            }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateEditor(label: String, value: String?, onChange: (Any?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val display = value?.takeIf { it.isNotBlank() }?.let { iso ->
        runCatching {
            LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        }.getOrDefault(iso)
    }

    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { picking = true },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(display ?: "Pick a date")
            }
            if (value != null) {
                IconButton(onClick = { onChange(null) }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear date",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (picking) {
        val parsedInitial = value?.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = parsedInitial
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val iso = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .toString()
                        onChange(iso)
                    }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text("Cancel") }
            },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun BooleanEditor(label: String, value: Boolean?, onChange: (Any?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = value == true,
            onCheckedChange = { onChange(it) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectEditor(
    label: String,
    choices: List<String>,
    value: String?,
    onChange: (Any?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = value.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // Sentinel "(none)" row clears the value — matches the
            // explicit-clear convention we use on other field types.
            DropdownMenuItem(
                text = {
                    Text(
                        "(none)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                },
                onClick = { onChange(null); expanded = false },
            )
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice) },
                    onClick = { onChange(choice); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiselectChoicesEditor(
    label: String,
    choices: List<String>,
    value: List<String>,
    onChange: (Any?) -> Unit,
) {
    val selected = value.toSet()
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            choices.forEach { choice ->
                FilterChip(
                    selected = choice in selected,
                    onClick = {
                        val next = if (choice in selected) selected - choice else selected + choice
                        // Empty list -> null so the server clears the
                        // value rather than persisting [] (which would
                        // read as "explicitly no tags" but is harder
                        // for the UI to distinguish from "unset").
                        onChange(if (next.isEmpty()) null else next.toList())
                    },
                    label = { Text(choice) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiselectFreeformEditor(
    label: String,
    value: List<String>,
    onChange: (Any?) -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    fun add(text: String) {
        val tag = text.trim()
        if (tag.isEmpty()) return
        if (tag in value) {
            draft = ""
            return
        }
        onChange(value + tag)
        draft = ""
    }

    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        // Existing tags as removable chips.
        if (value.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                value.forEach { tag ->
                    FilterChip(
                        selected = true,
                        onClick = {
                            val next = value - tag
                            onChange(if (next.isEmpty()) null else next)
                        },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove $tag",
                                modifier = Modifier.height(16.dp),
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        // Inline text field + Add button. Pressing the keyboard Done
        // key also adds; users in tag-heavy fields don't have to keep
        // reaching for the on-screen button.
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Add tag") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { add(draft) },
                ),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = { add(draft) },
                enabled = draft.isNotBlank(),
            ) { Text("Add") }
        }
    }
}

/**
 * Read-only formatter for [MemberProfileScreen]. Mirrors the editor
 * dispatch but returns a display String rather than rendering an
 * editable widget. Null / blank values render as a long em-dash so the
 * profile doesn't show empty rows for unset fields. Pure (not
 * @Composable) so it can be called inline next to ListItem props.
 */
internal fun customFieldValueDisplay(field: CustomFieldRead, value: Any?): String {
    if (value == null) return "—"
    return when (field.fieldType) {
        "boolean" -> if (value as? Boolean == true) "Yes" else "No"
        "date" -> (value as? String)?.takeIf { it.isNotBlank() }?.let { iso ->
            runCatching {
                LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
            }.getOrDefault(iso)
        } ?: "—"
        "multiselect" -> value.coerceToStringList()
            .joinToString(", ")
            .ifEmpty { "—" }
        "number" -> (value as? Number)?.toString() ?: value.toString()
        else -> value.toString().ifBlank { "—" }
    }
}
