package systems.lupine.sheaf.ui.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.ExportJobRead
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SectionHeader
import systems.lupine.sheaf.ui.components.SheafTopAppBar
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDataScreen(
    onNavigateUp: () -> Unit,
    viewModel: ExportDataViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val jsonSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportJsonTo(it) } }

    // Saveable: the document picker is a separate activity, so this one can be
    // recreated (rotation, low memory) while it is up. With a plain remember the
    // job id came back null, the result was dropped, and the user got nothing
    // after choosing where to save.
    var pendingDownloadJobId by rememberSaveable { mutableStateOf<String?>(null) }
    val zipSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val jobId = pendingDownloadJobId
        pendingDownloadJobId = null
        if (uri != null && jobId != null) viewModel.downloadJobTo(jobId, uri)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Export data") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.error != null) ErrorBanner(state.error!!)
            if (state.message != null) {
                Text(state.message!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            SectionHeader("Format")
            ExportFormat.entries.forEach { fmt ->
                FormatRow(
                    format = fmt,
                    selected = state.format == fmt,
                    onSelect = { viewModel.setFormat(fmt) },
                )
            }

            HorizontalDivider()
            SectionHeader("Download")

            Button(
                onClick = {
                    val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
                    jsonSaveLauncher.launch(viewModel.jsonFileName(ts))
                },
                enabled = !state.isExportingJson,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isExportingJson) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Export JSON only")
            }
            Text(
                "A metadata-only JSON file. Fast, but does not include image files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = { viewModel.openStepUp() },
                enabled = !state.isSubmittingJob,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Build full backup (with images)") }
            Text(
                "Builds a zip with your images in the background, then appears below to download. Requires your password.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.jobs.isNotEmpty() || state.isLoadingJobs) {
                HorizontalDivider()
                SectionHeader("Recent backups")
                if (state.isLoadingJobs && state.jobs.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.jobs.forEach { job ->
                    BackupRow(
                        job = job,
                        isDownloading = state.downloadingJobId == job.id,
                        onDownload = {
                            pendingDownloadJobId = job.id
                            zipSaveLauncher.launch(viewModel.fileNameForJob(job))
                        },
                    )
                }
            }
        }
    }

    if (state.showStepUp) {
        StepUpDialog(
            totpEnabled = state.totpEnabled,
            isSubmitting = state.isSubmittingJob,
            error = state.stepUpError,
            onConfirm = { password, totp -> viewModel.requestFullBackup(password, totp) },
            onDismiss = { viewModel.dismissStepUp() },
        )
    }
}

@Composable
private fun FormatRow(format: ExportFormat, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.padding(start = 4.dp, top = 10.dp)) {
            Text(format.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                format.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BackupRow(job: ExportJobRead, isDownloading: Boolean, onDownload: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    statusLabel(job),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = when (job.status) {
                        "failed", "expired" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                val sub = buildList {
                    add(if (job.format == "openplural") "OpenPlural" else "Sheaf")
                    formatDate(job.requestedAt)?.let { add(it) }
                    job.fileSizeBytes?.let { add(formatSize(it)) }
                }.joinToString(" · ")
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (job.status == "failed" && job.error != null) {
                    Text(job.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            when {
                job.status == "pending" || job.status == "running" ->
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                job.isDownloadable -> {
                    if (isDownloading) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onDownload) {
                            Icon(Icons.Outlined.CloudDownload, contentDescription = "Download backup")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepUpDialog(
    totpEnabled: Boolean,
    isSubmitting: Boolean,
    error: String?,
    onConfirm: (password: String, totp: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var totp by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Confirm it's you") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Building a full backup exports everything you have, including images. Enter your password to continue.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (totpEnabled) {
                    OutlinedTextField(
                        value = totp,
                        onValueChange = { totp = it },
                        label = { Text("Authenticator code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password, totp.takeIf { totpEnabled }) },
                enabled = !isSubmitting && password.isNotBlank() && (!totpEnabled || totp.isNotBlank()),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Build backup")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") }
        },
    )
}

private fun statusLabel(job: ExportJobRead): String = when (job.status) {
    "pending" -> "Queued"
    "running" -> "Building…"
    "done" -> "Ready to download"
    "failed" -> "Failed"
    "expired" -> "Expired"
    else -> job.status
}

private fun formatDate(iso: String): String? = runCatching {
    OffsetDateTime.parse(iso).toLocalDateTime().format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
}.getOrNull()

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
