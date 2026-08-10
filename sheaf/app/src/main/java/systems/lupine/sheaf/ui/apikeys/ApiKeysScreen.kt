package systems.lupine.sheaf.ui.apikeys

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SectionHeader
import systems.lupine.sheaf.ui.components.SheafTopAppBar
import systems.lupine.sheaf.ui.theme.LocalWarningColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysScreen(
    onNavigateUp: () -> Unit,
    viewModel: ApiKeysViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showCreateSheet by remember { mutableStateOf(false) }
    var revokeId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("API Keys") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateSheet = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Create key")
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
                ErrorBanner(state.error!!, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.keys.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No API keys", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Create a key to access the Sheaf API programmatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { showCreateSheet = true }) { Text("Create Key") }
                    }
                }
            } else {
                state.keys.forEach { key ->
                    ListItem(
                        headlineContent = { Text(key.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column {
                                Text(
                                    key.scopes.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                key.expiresAt?.let {
                                    Text(
                                        "Expires: $it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { revokeId = key.id }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Revoke", tint = MaterialTheme.colorScheme.error)
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    // ── Create key sheet ──────────────────────────────────────────────────────

    if (showCreateSheet) {
        CreateApiKeySheet(
            isAdmin = state.user?.isAdmin == true,
            isCreating = state.isCreating,
            error = state.error,
            onDismiss = { showCreateSheet = false },
            onCreate = { name, scopes, expiresAt ->
                viewModel.createKey(name, scopes, expiresAt)
            },
        )
    }

    // ── Created key dialog (show raw key once) ────────────────────────────────

    state.createdKey?.let { created ->
        val clipboard = LocalClipboard.current
        val scope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { viewModel.clearCreatedKey() },
            title = { Text("Key Created") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Copy your API key now. It won't be shown again.")
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                created.key,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                                maxLines = 3,
                            )
                            IconButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", created.key))) } }) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearCreatedKey() }) { Text("Done") }
            },
        )
    }

    // ── Revoke confirmation ───────────────────────────────────────────────────

    revokeId?.let { id ->
        AlertDialog(
            onDismissRequest = { revokeId = null },
            title = { Text("Revoke key?") },
            text = { Text("This key will stop working immediately.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.revokeKey(id); revokeId = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Revoke") }
            },
            dismissButton = { TextButton(onClick = { revokeId = null }) { Text("Cancel") } },
        )
    }
}

// ── Create key sheet ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateApiKeySheet(
    isAdmin: Boolean,
    isCreating: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (name: String, scopes: List<String>, expiresAt: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var levels by remember {
        mutableStateOf<Map<String, ApiScopeLevel>>(emptyMap())
    }
    var adminLevel by remember { mutableStateOf(ApiScopeLevel.NONE) }
    val computedScopes = remember(levels, adminLevel, isAdmin) {
        scopesFromLevels(levels, isAdmin, adminLevel)
    }
    // The export endpoint hands back the whole account, so export:read is a
    // read of everything however narrow the rest of the picks are. That is
    // easy to miss when choosing scopes one resource at a time, so say it
    // inline, and confirm before creating.
    val exportSelected = (levels["export"] ?: ApiScopeLevel.NONE) != ApiScopeLevel.NONE
    // Only surprising when the key does not already read everything anyway.
    val grantsEveryRead = remember(levels) { grantsEveryRead(levels) }
    var confirmExport by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Create API Key", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. My Script") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (error != null) {
                ErrorBanner(error)
            }

            SectionHeader("Scopes")
            Text(
                "Write implies Read; Delete implies Read+Write.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SCOPE_GROUPS.forEach { (heading, resources) ->
                Text(
                    heading,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                resources.forEach { resource ->
                    ScopeLevelRow(
                        resource = resource,
                        level = levels[resource.key] ?: ApiScopeLevel.NONE,
                        onLevelChange = { newLevel ->
                            levels = levels + (resource.key to newLevel)
                        },
                    )
                }
            }

            if (isAdmin) {
                Text(
                    "Admin",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                ScopeLevelRow(
                    resource = ApiScopeResource("admin", "Admin endpoints"),
                    level = adminLevel,
                    onLevelChange = { adminLevel = it },
                )
            }

            Text(
                if (computedScopes.isEmpty()) "No scopes selected"
                else "Will grant: ${computedScopes.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )

            if (exportSelected) {
                val warning = LocalWarningColors.current
                Surface(
                    color = warning.container,
                    contentColor = warning.onContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Data export read lets this key download a full account " +
                            "export: everything in your account, not just the scopes " +
                            "selected above.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Button(
                onClick = {
                    if (exportSelected && !grantsEveryRead) confirmExport = true
                    else onCreate(name, computedScopes, null)
                },
                enabled = !isCreating && name.isNotBlank() && computedScopes.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (isCreating) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Text("Create Key")
            }
        }
    }

    if (confirmExport) {
        AlertDialog(
            onDismissRequest = { confirmExport = false },
            title = { Text("This key can read everything") },
            text = {
                Text(
                    "Data export read lets this key download a full account export: " +
                        "every member, journal, board message, poll, and setting in your " +
                        "account, not just the scopes you selected. Only hand it to " +
                        "something you would trust with all of it.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isCreating,
                    onClick = {
                        confirmExport = false
                        onCreate(name, computedScopes, null)
                    },
                ) { Text("Create key anyway") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExport = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeLevelRow(
    resource: ApiScopeResource,
    level: ApiScopeLevel,
    onLevelChange: (ApiScopeLevel) -> Unit,
) {
    // Build the available levels for this resource. read-only / write-only
    // resources expose a smaller option set.
    val options: List<Pair<ApiScopeLevel, String>> = when {
        resource.writeOnly -> listOf(ApiScopeLevel.NONE to "None", ApiScopeLevel.WRITE to "Write")
        resource.readOnly  -> listOf(ApiScopeLevel.NONE to "None", ApiScopeLevel.READ to "Read")
        resource.hasDelete -> listOf(
            ApiScopeLevel.NONE to "None",
            ApiScopeLevel.READ to "Read",
            ApiScopeLevel.WRITE to "Write",
            ApiScopeLevel.DELETE to "Delete",
        )
        else -> listOf(
            ApiScopeLevel.NONE to "None",
            ApiScopeLevel.READ to "Read",
            ApiScopeLevel.WRITE to "Write",
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(resource.label, style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (lvl, label) ->
                SegmentedButton(
                    selected = level == lvl,
                    onClick = { onLevelChange(lvl) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) { Text(label, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}
