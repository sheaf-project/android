package systems.lupine.sheaf.ui.settings

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import systems.lupine.sheaf.BuildConfig
import systems.lupine.sheaf.ui.components.SectionHeader
import systems.lupine.sheaf.ui.components.SheafTopAppBar

/**
 * Exactly which build this is, and exactly what it is talking to.
 *
 * The settings row it hangs off crams the same facts onto one ellipsised line,
 * which on a narrow screen truncated the commit before you could read it: the
 * only way to see the rest was to rotate the phone. Here every field gets its
 * own row, wraps rather than truncates, and copies to the clipboard on tap,
 * because the thing most often wanted from this screen is a commit hash pasted
 * into a bug report.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateUp: () -> Unit,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val serverVersion by viewModel.serverVersion.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("About") },
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
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("App", modifier = Modifier.padding(horizontal = 16.dp))
            DetailRow("Version", BuildConfig.VERSION_NAME)
            DetailRow("Version code", BuildConfig.VERSION_CODE.toString())
            DetailRow("Commit", BuildConfig.GIT_COMMIT_FULL)
            DetailRow("Built", BuildConfig.BUILD_TIME)
            DetailRow(
                "Distribution",
                buildString {
                    append(BuildConfig.FLAVOR)
                    append(if (BuildConfig.DEBUG) ", debug" else ", release")
                },
            )
            DetailRow("Package", BuildConfig.APPLICATION_ID)

            SectionHeader("Server", modifier = Modifier.padding(horizontal = 16.dp))
            DetailRow("Address", baseUrl.ifBlank { "Not configured" }, copyable = baseUrl.isNotBlank())
            val version = serverVersion
            if (version == null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "This server hasn't said what it's running.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        // Worth saying: an instance that predates the endpoint
                        // reads identically to one that is simply unreachable,
                        // and the difference matters when something is broken.
                        "Either it's older than the version endpoint, or it couldn't be reached.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { viewModel.refresh() }) { Text("Try again") }
                }
            } else {
                // Every field is optional: a server built outside CI reports a
                // version and little else, and blank rows would just be noise.
                version.version?.takeIf { it.isNotBlank() }?.let { DetailRow("Version", it) }
                version.gitTag?.takeIf { it.isNotBlank() }?.let { DetailRow("Tag", it) }
                version.gitCommit?.takeIf { it.isNotBlank() }?.let { DetailRow("Commit", it) }
                version.buildTime?.takeIf { it.isNotBlank() }?.let { DetailRow("Built", it) }
                version.mode?.takeIf { it.isNotBlank() }?.let { DetailRow("Mode", it) }
            }
        }
    }
}

/**
 * One fact, its label, and a copy button.
 *
 * The value is deliberately unconstrained in line count: a full commit hash on
 * a narrow screen is the case this screen exists for, and eliding it here would
 * reproduce the bug.
 */
@Composable
private fun DetailRow(label: String, value: String, copyable: Boolean = true) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        if (copyable) {
            IconButton(onClick = {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value)))
                }
            }) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    HorizontalDivider()
}
