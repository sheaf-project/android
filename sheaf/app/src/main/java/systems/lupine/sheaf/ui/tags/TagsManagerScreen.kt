@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package systems.lupine.sheaf.ui.tags

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.TagRead
import systems.lupine.sheaf.ui.components.ColorPicker
import systems.lupine.sheaf.ui.components.ColorSwatch
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SheafTopAppBar

private const val DEFAULT_NEW_COLOR = "#10B981"

@Composable
fun TagsManagerScreen(
    onNavigateUp: () -> Unit,
    viewModel: TagsManagerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showCreateSheet by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Tags") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateSheet = true }) {
                        Icon(Icons.Default.Add, contentDescription = "New tag")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.error != null) {
                ErrorBanner(state.error!!, modifier = Modifier.padding(16.dp))
            }
            if (state.resultMessage != null) {
                Text(
                    state.resultMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                )
            }

            when {
                state.isLoading -> Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.tags.isEmpty() -> Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("No tags yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tags label members so you can group them by traits, role, or anything else useful to you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { showCreateSheet = true }) { Text("New Tag") }
                }

                else -> state.tags.forEach { tag ->
                    TagRow(
                        tag = tag,
                        onEdit = { viewModel.startEdit(tag.id) },
                        onDelete = { viewModel.openDelete(tag) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
            }
        }
    }

    if (showCreateSheet) {
        TagEditSheet(
            initial = null,
            isSaving = state.isCreating,
            errorMessage = state.createError,
            onDismiss = { showCreateSheet = false },
            onSave = { name, color ->
                viewModel.createTag(name, color)
                showCreateSheet = false
            },
        )
    }

    state.editingTagId?.let { id ->
        val tag = state.tags.firstOrNull { it.id == id }
        if (tag != null) {
            TagEditSheet(
                initial = tag,
                isSaving = state.isUpdating,
                errorMessage = null,
                onDismiss = { viewModel.cancelEdit() },
                onSave = { name, color -> viewModel.updateTag(tag.id, name, color) },
            )
        }
    }

    state.pendingDelete?.let { tag ->
        TagDeleteDialog(
            tag = tag,
            safety = state.safety,
            isDeleting = state.isDeleting,
            errorMessage = state.deleteError,
            onConfirm = { p, t -> viewModel.confirmDelete(p, t) },
            onDismiss = { viewModel.closeDelete() },
        )
    }
}

@Composable
private fun TagRow(
    tag: TagRead,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            ColorSwatch(hex = tag.color ?: "#10B981", size = 24.dp)
        },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

@Composable
private fun TagEditSheet(
    initial: TagRead?,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, color: String?) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var color by remember(initial?.id) { mutableStateOf(initial?.color ?: DEFAULT_NEW_COLOR) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (initial == null) "New Tag" else "Edit Tag",
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. caretaker") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Colour", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ColorSwatch(hex = color, size = 40.dp)
                Text(color, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ColorPicker(hex = color, onColorChange = { color = it })

            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = { onSave(name, color) },
                enabled = !isSaving && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Text(if (initial == null) "Create" else "Save")
            }
        }
    }
}

@Composable
private fun TagDeleteDialog(
    tag: TagRead,
    safety: TagDeleteSafety,
    isDeleting: Boolean,
    errorMessage: String?,
    onConfirm: (password: String?, totpCode: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val needsPassword = safety.authTier == "password" || safety.authTier == "both"
    val needsTotp = (safety.authTier == "totp" || safety.authTier == "both") && safety.totpEnabled
    val willQueue = safety.appliesToTags && safety.gracePeriodDays > 0

    var password by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
        title = { Text(if (willQueue) "Queue tag deletion?" else "Delete tag?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (willQueue) {
                        "Tag \"${tag.name}\" will be queued for deletion in ${safety.gracePeriodDays} " +
                            "${if (safety.gracePeriodDays == 1) "day" else "days"}. You can cancel from " +
                            "System Safety before then. Members tagged with it will be untagged on delete."
                    } else {
                        "Permanently delete tag \"${tag.name}\"? Members tagged with it will be untagged."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (needsPassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (needsTotp) {
                    OutlinedTextField(
                        value = totpCode,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) totpCode = it },
                        label = { Text("Authenticator code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (errorMessage != null) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        password.takeIf { needsPassword },
                        totpCode.takeIf { needsTotp },
                    )
                },
                enabled = !isDeleting &&
                    (!needsPassword || password.isNotBlank()) &&
                    (!needsTotp || totpCode.length == 6),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                if (isDeleting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(if (willQueue) "Queue" else "Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("Cancel") }
        },
    )
}
