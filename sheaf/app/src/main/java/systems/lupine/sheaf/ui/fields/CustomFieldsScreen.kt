@file:Suppress("ASSIGNED_VALUE_IS_NEVER_READ")

package systems.lupine.sheaf.ui.fields

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.CustomFieldRead
import systems.lupine.sheaf.ui.components.*
import systems.lupine.sheaf.ui.sharing.offersPublic
import androidx.compose.ui.draw.alpha

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun fieldTypeIcon(fieldType: String): ImageVector = when (fieldType) {
    "text"        -> Icons.Outlined.TextFields
    "number"      -> Icons.Outlined.Tag
    "date"        -> Icons.Outlined.CalendarMonth
    "boolean"     -> Icons.Outlined.ToggleOn
    "select"      -> Icons.AutoMirrored.Outlined.List
    "multiselect" -> Icons.Outlined.Checklist
    "slider"      -> Icons.Outlined.Tune
    else          -> Icons.AutoMirrored.Outlined.List
}

private val privacyOptions  = listOf("private", "friends", "public")
private val fieldTypeOptions =
    listOf("text", "number", "date", "boolean", "select", "multiselect", "slider")

/**
 * The type names as the web card labels them, so the two screens do not call
 * the same field two different things.
 */
private fun fieldTypeLabel(fieldType: String): String = when (fieldType) {
    "boolean"     -> "Yes/No"
    "select"      -> "Select (single)"
    "multiselect" -> "Multi-select"
    else          -> fieldType.replaceFirstChar { it.uppercase() }
}

private fun isChoiceType(fieldType: String?) =
    fieldType == "select" || fieldType == "multiselect"

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFieldsScreen(
    onNavigateUp: () -> Unit,
    viewModel: CustomFieldsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    var showAddSheet    by remember { mutableStateOf(false) }
    var editingField    by remember { mutableStateOf<CustomFieldRead?>(null) }
    var deletingField   by remember { mutableStateOf<CustomFieldRead?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Custom Fields") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add field")
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            // Only when there is nothing behind it: an error from an action
            // (a refused reorder) must not replace the list being worked in.
            state.error != null && state.fields.isEmpty() -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ErrorBanner(state.error!!)
                Button(onClick = { viewModel.clearError(); viewModel.load() }) { Text("Retry") }
            }

            state.fields.isEmpty() -> EmptyState(
                icon = Icons.AutoMirrored.Outlined.List,
                title = "No custom fields yet",
                subtitle = "Custom fields let you store extra info on each member.",
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            // The scaffold padding moves to the column so the banner sits
            // inside it too; the list keeps only its own spacing.
            else -> Column(Modifier.padding(padding)) {
                state.error?.let {
                    ErrorBanner(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = 8.dp,
                        bottom = 88.dp, // clear FAB
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(state.fields, key = { _, field -> field.id }) { index, field ->
                        FieldListItem(
                            field = field,
                            onClick = { editingField = field },
                            onDeleteClick = { deletingField = field },
                            // Null means the field has nowhere to go that way,
                            // or a reorder is already in flight; the arrow then
                            // greys out.
                            onMoveUp = { viewModel.moveField(index, -1) }
                                .takeIf { index > 0 && !state.isReordering },
                            onMoveDown = { viewModel.moveField(index, 1) }
                                .takeIf { index < state.fields.lastIndex && !state.isReordering },
                        )
                    }
                }
            }
        }
    }

    // ── Add field bottom sheet ─────────────────────────────────────────────────

    if (showAddSheet) {
        AddFieldSheet(
            isSaving = state.isSaving,
            // A new field has no stored level, so Public would be a raise from
            // nothing, which the server refuses on create too.
            offersPublic = state.raiseGate.offersPublic(null),
            onDismiss = { showAddSheet = false },
            onSave = { name, fieldType, privacy, choices, slider ->
                viewModel.createField(name, fieldType, privacy, choices, slider)
                showAddSheet = false
            },
        )
    }

    // ── Edit field dialog ──────────────────────────────────────────────────────

    editingField?.let { field ->
        EditFieldDialog(
            field = field,
            isSaving = state.isSaving,
            offersPublic = state.raiseGate.offersPublic(field.privacy),
            onDismiss = { editingField = null },
            onSave = { name, privacy, choices, slider ->
                viewModel.updateField(
                    id = field.id,
                    name = name,
                    privacy = privacy,
                    choices = choices,
                    fieldType = field.fieldType,
                    slider = slider,
                )
                editingField = null
            },
        )
    }

    // ── Delete confirmation dialog ─────────────────────────────────────────────

    deletingField?.let { field ->
        AlertDialog(
            onDismissRequest = { deletingField = null },
            title = { Text("Delete field?") },
            text = { Text("\"${field.name}\" and all its values will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteField(field.id); deletingField = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingField = null }) { Text("Cancel") }
            },
        )
    }

    if (state.stepUpEdit != null) {
        systems.lupine.sheaf.ui.sharing.StepUpSheet(
            authTier = state.raiseGate.authTier,
            totpEnabled = state.raiseGate.totpEnabled,
            isBusy = state.isSaving,
            errorMessage = state.stepUpError,
            onConfirm = { password, totp -> viewModel.confirmStepUp(password, totp) },
            onDismiss = { viewModel.dismissStepUp() },
        )
    }
}

// ── Field list item ───────────────────────────────────────────────────────────

@Composable
private fun FieldListItem(
    field: CustomFieldRead,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    // The name shares this row with three icon buttons, and ListItem gives the
    // trailing slot the width it asks for before the headline gets any. With
    // the privacy chip in there too the name was left a column one character
    // wide, spelling itself vertically down the screen. Reported from the
    // field. The chip moves down beside the type, where it has a full line to
    // sit on, and the name is capped at two lines so no future addition to the
    // trailing slot can collapse it again.
    ListItem(
        headlineContent = {
            Text(
                field.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(fieldTypeLabel(field.fieldType), style = MaterialTheme.typography.bodySmall)
                    // The same tag the member editor and the member profile
                    // show, so the three places a field's level appears cannot
                    // disagree about what it is or what it is called.
                    PrivacyTag(field.privacy)
                }
                StagedPrivacyNote(field.pendingPrivacy, field.privacyActivatesAt)
                PendingDeleteBadge(field.pendingDeleteAt)
            }
        },
        leadingContent = {
            Icon(
                imageVector = fieldTypeIcon(field.fieldType),
                contentDescription = fieldTypeLabel(field.fieldType),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                // This order is what a member's profile and any shared page
                // show their fields in, so it is worth being able to set.
                // See GroupCard: no explicit tint, so the button's own
                // disabled colour survives and an end-of-list arrow reads as
                // unavailable rather than identical to a live one.
                IconButton(onClick = { onMoveUp?.invoke() }, enabled = onMoveUp != null) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move ${field.name} up",
                    )
                }
                IconButton(onClick = { onMoveDown?.invoke() }, enabled = onMoveDown != null) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move ${field.name} down",
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete ${field.name}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .alpha(if (field.pendingDeleteAt != null) PENDING_DELETE_ALPHA else 1f),
    )
    HorizontalDivider()
}

/**
 * Per-choice list editor for SELECT / MULTISELECT custom fields.
 *
 * Each row is a small text field with a remove (×) icon. An "Add
 * choice" button at the bottom appends an empty row. An empty list
 * is treated by the viewmodel as "freeform tag mode" — backend
 * accepts any string for this field. So the editor sits comfortably
 * with both "give me preset choices" and "I just want a tag input"
 * intents.
 */
@Composable
private fun ChoicesEditor(
    choices: List<String>,
    onChange: (List<String>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Choices",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (choices.isEmpty())
                "Leave empty for a freeform tag input (any value accepted)."
            else
                "Pick from this list. Reorder by editing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        choices.forEachIndexed { i, value ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { v ->
                        onChange(choices.toMutableList().also { it[i] = v })
                    },
                    singleLine = true,
                    label = { Text("Choice ${i + 1}") },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    onChange(choices.toMutableList().also { it.removeAt(i) })
                }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove choice",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        TextButton(onClick = { onChange(choices + "") }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(if (choices.isEmpty()) "Add a choice" else "Add another")
        }
    }
}

// ── Slider options editor ─────────────────────────────────────────────────────

/**
 * Gradients offered by name, the same ids, labels and stops the web card
 * offers. The id is not stored anywhere (the options carry the colours
 * themselves), but keeping the lists identical means a gradient picked on one
 * client still shows as its name on the other instead of as "Custom colours".
 */
private val gradientPresets: List<Triple<String, String, List<String>>> = listOf(
    Triple("none", "No gradient", emptyList()),
    Triple("cool-warm", "Cool to warm", listOf("#5B92C9", "#E08D5E")),
    Triple("calm-intense", "Calm to intense", listOf("#7BAA7E", "#D6534D")),
    Triple("sad-happy", "Low to high", listOf("#5479AE", "#E4C44F")),
    Triple("soft-hard", "Soft to hard", listOf("#F4EBD8", "#A89B8C", "#3A2E4D")),
    Triple("day-night", "Day to night", listOf("#F4D86E", "#1E2444")),
    Triple("rose-dusk", "Rose dusk", listOf("#F4B89E", "#D17A8E", "#5B4378")),
    Triple("sage-meadow", "Sage meadow", listOf("#C5D9B5", "#4A6B47")),
    Triple("monochrome", "Monochrome", listOf("#E4DEEC", "#3A2E4D")),
    Triple("custom", "Custom colours", emptyList()),
)

private const val CUSTOM_GRADIENT_ID = "custom"
private val defaultCustomGradient = listOf("#5B92C9", "#E08D5E")
private const val MAX_GRADIENT_STOPS = 8

/**
 * What the server would refuse about this slider's bounds, said in the words
 * the person setting it up would use, or null when it is fine.
 *
 * Only the numeric kind can be wrong this way: a labeled slider's range is
 * fixed at 0 to 100 and is not the owner's to get wrong. Checked here so the
 * Save button can wait instead of sending a request whose 422 would surface as
 * a generic "Couldn't save this field".
 */
internal fun sliderProblem(spec: SliderSpec?): String? {
    if (spec == null || !spec.isNumeric) return null
    if (spec.max <= spec.min) return "Max has to be more than min."
    val step = spec.step ?: return null
    if (step <= 0) return "Step has to be more than zero."
    if (step > spec.max - spec.min) return "Step cannot be bigger than the range."
    return null
}

/** Whether a field is safe to save, as far as its slider bounds go. */
internal fun sliderBoundsOk(fieldType: String?, slider: SliderSpec?): Boolean =
    fieldType != "slider" || sliderProblem(slider) == null

/** Which preset a set of stops is, or "custom" when it matches none. */
internal fun gradientPresetId(gradient: List<String>?): String {
    if (gradient.isNullOrEmpty()) return "none"
    val normalised = gradient.map { it.uppercase() }
    return gradientPresets
        .firstOrNull { (id, _, stops) ->
            id != CUSTOM_GRADIENT_ID && stops.isNotEmpty() && stops == normalised
        }
        ?.first
        ?: CUSTOM_GRADIENT_ID
}

/** A gradient as a swatch, for the preset rows. */
@Composable
private fun GradientSwatch(stops: List<String>, modifier: Modifier = Modifier) {
    val colours = stops.mapNotNull { parseColor(it) }
    if (colours.size < 2) return
    Box(
        modifier = modifier
            .width(40.dp)
            .height(12.dp)
            .clip(CircleShape)
            .background(Brush.horizontalGradient(colours)),
    )
}

/**
 * Everything a slider field needs: kind, bounds, labels, track colours, and a
 * live preview of the result.
 *
 * Laid out down the screen rather than across it. The web card puts the three
 * label fields and the four numeric fields in grids, which a phone does not
 * have the width for: three text fields across a phone is how the field list
 * ended up spelling names vertically. Same controls, same order, one per line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderOptionsEditor(
    value: SliderSpec,
    onChange: (SliderSpec) -> Unit,
) {
    // "Custom colours" starts from whatever stops are showing, so without this
    // flag the picker would snap straight back to the preset those stops match
    // and the swatches would never appear.
    var customColours by remember { mutableStateOf(false) }
    var gradientMenuOpen by remember { mutableStateOf(false) }
    var editingColourIndex by remember { mutableStateOf<Int?>(null) }

    val preset = if (customColours && value.gradient != null) {
        CUSTOM_GRADIENT_ID
    } else {
        gradientPresetId(value.gradient)
    }
    val presetLabel = gradientPresets.firstOrNull { it.first == preset }?.second.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Kind",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Switching kind resets the bounds that belong to the other one,
            // the same way the web card does: a numeric slider that kept a
            // labeled one's 0..100 would claim an operator chose that range.
            KindRow(
                label = "Between two labels (0 to 100)",
                selected = !value.isNumeric,
                onClick = {
                    onChange(
                        value.copy(
                            mode = SLIDER_MODE_LABELED,
                            min = 0.0,
                            max = 100.0,
                            step = null,
                            unit = null,
                        ),
                    )
                },
            )
            KindRow(
                label = "A number with a range",
                selected = value.isNumeric,
                onClick = {
                    onChange(
                        value.copy(
                            mode = SLIDER_MODE_NUMERIC,
                            min = 0.0,
                            max = 10.0,
                            step = null,
                        ),
                    )
                },
            )
        }

        if (value.isNumeric) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberBox(
                    label = "Min",
                    value = value.min,
                    onChange = { onChange(value.copy(min = it ?: 0.0)) },
                    modifier = Modifier.weight(1f),
                )
                NumberBox(
                    label = "Max",
                    value = value.max,
                    onChange = { onChange(value.copy(max = it ?: 0.0)) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberBox(
                    label = "Step",
                    value = value.step,
                    placeholder = "any",
                    onChange = { onChange(value.copy(step = it)) },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = value.unit.orEmpty(),
                    onValueChange = {
                        onChange(value.copy(unit = it.ifBlank { null }))
                    },
                    label = { Text("Unit") },
                    placeholder = { Text("e.g. cm") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            sliderProblem(value)?.let { problem ->
                Text(
                    problem,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        listOf(
            Triple("Left label", value.labels.left, if (value.isNumeric) "optional" else "e.g. Calm"),
            Triple("Centre label", value.labels.center, "optional"),
            Triple("Right label", value.labels.right, if (value.isNumeric) "optional" else "e.g. Intense"),
        ).forEachIndexed { index, (label, current, hint) ->
            OutlinedTextField(
                value = current.orEmpty(),
                onValueChange = { text ->
                    val next = text.take(50).ifBlank { null }
                    onChange(
                        value.copy(
                            labels = when (index) {
                                0 -> value.labels.copy(left = next)
                                1 -> value.labels.copy(center = next)
                                else -> value.labels.copy(right = next)
                            },
                        ),
                    )
                },
                label = { Text(label) },
                placeholder = { Text(hint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Track colours",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ExposedDropdownMenuBox(
                expanded = gradientMenuOpen,
                onExpandedChange = { gradientMenuOpen = it },
            ) {
                OutlinedTextField(
                    value = presetLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = gradientMenuOpen)
                    },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = gradientMenuOpen,
                    onDismissRequest = { gradientMenuOpen = false },
                ) {
                    gradientPresets.forEach { (id, label, stops) ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    GradientSwatch(stops)
                                    Text(label)
                                }
                            },
                            onClick = {
                                customColours = id == CUSTOM_GRADIENT_ID
                                onChange(
                                    when (id) {
                                        "none" -> value.copy(gradient = null)
                                        CUSTOM_GRADIENT_ID -> value.copy(
                                            gradient = value.gradient ?: defaultCustomGradient,
                                        )
                                        else -> value.copy(gradient = stops)
                                    },
                                )
                                gradientMenuOpen = false
                            },
                        )
                    }
                }
            }

            if (preset == CUSTOM_GRADIENT_ID) {
                val stops = value.gradient ?: defaultCustomGradient
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    stops.forEachIndexed { index, colour ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Surface(
                                onClick = { editingColourIndex = index },
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                Box(Modifier.padding(2.dp)) {
                                    ColorSwatch(colour, size = 32.dp)
                                }
                            }
                            // Two is the floor the server enforces, so the
                            // remove button only exists above it.
                            if (stops.size > 2) {
                                IconButton(
                                    onClick = {
                                        onChange(
                                            value.copy(
                                                gradient = stops.filterIndexed { i, _ -> i != index },
                                            ),
                                        )
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove colour ${index + 1}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    if (stops.size < MAX_GRADIENT_STOPS) {
                        TextButton(onClick = { onChange(value.copy(gradient = stops + "#888888")) }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add colour")
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Preview",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The same 62% of the way along the web card previews at, so a
            // gradient looks the same on both while it is being picked.
            val previewSpec = sliderSpec(value.toOptions())
            SliderReadout(
                value = previewSpec.min + previewSpec.span * 0.62,
                spec = previewSpec,
            )
        }
    }

    editingColourIndex?.let { index ->
        val stops = value.gradient ?: defaultCustomGradient
        val current = stops.getOrNull(index) ?: return@let
        AlertDialog(
            onDismissRequest = { editingColourIndex = null },
            title = { Text("Colour ${index + 1}") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ColorPicker(
                        hex = current,
                        onColorChange = { hex ->
                            onChange(
                                value.copy(
                                    gradient = stops.toMutableList().also {
                                        it[index] = hex.uppercase()
                                    },
                                ),
                            )
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { editingColourIndex = null }) { Text("Done") }
            },
        )
    }
}

@Composable
private fun KindRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * A number the user types. Kept as text while editing so a half-typed "-" or
 * "1." is not thrown away mid-keystroke, and blank means null, which is how
 * the step field says "any value".
 */
@Composable
private fun NumberBox(
    label: String,
    value: Double?,
    onChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    var text by remember(value) {
        mutableStateOf(
            value?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
                .orEmpty(),
        )
    }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            onChange(raw.trim().toDoubleOrNull())
        },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

// ── Add field bottom sheet ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFieldSheet(
    isSaving: Boolean,
    offersPublic: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        fieldType: String,
        privacy: String,
        choices: List<String>?,
        slider: SliderSpec?,
    ) -> Unit,
) {
    var name      by remember { mutableStateOf("") }
    var fieldType by remember { mutableStateOf(fieldTypeOptions.first()) }
    var privacy   by remember { mutableStateOf(privacyOptions.first()) }
    var expanded  by remember { mutableStateOf(false) }
    // Choices for select / multiselect. Empty list = freeform tag mode
    // (server-side any string accepted). Hidden for non-choice types.
    var choices   by remember { mutableStateOf(listOf<String>()) }
    var slider    by remember { mutableStateOf(defaultSliderSpec()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Add Custom Field", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Field name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value = fieldTypeLabel(fieldType),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Field type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    fieldTypeOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(fieldTypeLabel(option)) },
                            onClick = { fieldType = option; expanded = false },
                        )
                    }
                }
            }

            if (isChoiceType(fieldType)) {
                ChoicesEditor(
                    choices = choices,
                    onChange = { choices = it },
                )
            }

            if (fieldType == "slider") {
                SliderOptionsEditor(value = slider, onChange = { slider = it })
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Privacy",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    privacyOptions.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = privacy == option,
                            enabled = offersPublic || option != "public",
                            onClick = { privacy = option },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = privacyOptions.size,
                            ),
                        ) {
                            Text(option.replaceFirstChar { it.uppercase() })
                        }
                    }
                }
                if (!offersPublic) {
                    Text(
                        systems.lupine.sheaf.ui.sharing.PUBLISHING_OFF_NOTE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = {
                    val sendChoices = if (isChoiceType(fieldType)) choices else null
                    val sendSlider = slider.takeIf { fieldType == "slider" }
                    onSave(name.trim(), fieldType, privacy, sendChoices, sendSlider)
                },
                // A numeric slider whose max is not above its min is the one
                // shape the server refuses outright, so the button waits
                // rather than sending a 422 back as "Couldn't save".
                enabled = !isSaving && name.isNotBlank() && sliderBoundsOk(fieldType, slider),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Add Field")
                }
            }
        }
    }
}

// ── Edit field dialog ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditFieldDialog(
    field: CustomFieldRead,
    isSaving: Boolean,
    offersPublic: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, privacy: String, choices: List<String>?, slider: SliderSpec?) -> Unit,
) {
    var name    by remember(field.id) { mutableStateOf(field.name) }
    var privacy by remember(field.id) { mutableStateOf(field.privacy) }
    var choices by remember(field.id) {
        mutableStateOf(field.options?.choices ?: emptyList())
    }
    // Seeded from what the server holds, so saving after changing only the
    // name sends the shape back unchanged rather than resetting it.
    var slider  by remember(field.id) {
        mutableStateOf(
            if (field.fieldType == "slider") sliderSpec(field.options) else defaultSliderSpec(),
        )
    }
    val isChoiceField = isChoiceType(field.fieldType)
    val isSliderField = field.fieldType == "slider"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Field") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Field name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    "Type: ${fieldTypeLabel(field.fieldType)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isChoiceField) {
                    ChoicesEditor(
                        choices = choices,
                        onChange = { choices = it },
                    )
                }

                if (isSliderField) {
                    SliderOptionsEditor(value = slider, onChange = { slider = it })
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Privacy",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        privacyOptions.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = privacy == option,
                                enabled = offersPublic || option != "public",
                                onClick = { privacy = option },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = privacyOptions.size,
                                ),
                            ) {
                                Text(option.replaceFirstChar { it.uppercase() })
                            }
                        }
                    }
                    if (!offersPublic) {
                        Text(
                            systems.lupine.sheaf.ui.sharing.PUBLISHING_OFF_NOTE,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val sendChoices = if (isChoiceField) choices else null
                    onSave(name.trim(), privacy, sendChoices, slider.takeIf { isSliderField })
                },
                enabled = !isSaving && name.isNotBlank() &&
                    sliderBoundsOk(field.fieldType, slider),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
