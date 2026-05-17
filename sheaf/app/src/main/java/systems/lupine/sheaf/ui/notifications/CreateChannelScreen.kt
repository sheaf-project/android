package systems.lupine.sheaf.ui.notifications

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SheafTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChannelScreen(
    onNavigateUp: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateChannelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var recipientLabel by remember { mutableStateOf("") }
    var destinationType by remember { mutableStateOf("mobile_push") }
    var triggerOnStart by remember { mutableStateOf(true) }
    var triggerOnStop by remember { mutableStateOf(false) }
    var triggerOnCofrontChange by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text(if (state.activationUrl == null) "New invite" else "Invite created") },
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            state.error?.let { msg ->
                ErrorBanner(msg, modifier = Modifier.padding(vertical = 8.dp))
            }

            if (state.activationUrl != null) {
                ActivationUrlPanel(
                    channelName = state.createdChannelName ?: "Channel",
                    activationUrl = state.activationUrl!!,
                    onDone = onCreated,
                    context = context,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Channel name") },
                    placeholder = { Text("Fronts update") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = recipientLabel,
                    onValueChange = { recipientLabel = it },
                    label = { Text("Recipient label (optional)") },
                    placeholder = { Text("Mara") },
                    supportingText = { Text("So you remember who this is for.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(20.dp))
                Text(
                    "Delivery method",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                Column(Modifier.selectableGroup()) {
                    DestinationOption(
                        value = "mobile_push",
                        title = "Mobile push",
                        subtitle = "Recipient must have Sheaf installed and logged in. " +
                            "Delivered to every device on their account.",
                        selected = destinationType == "mobile_push",
                        onSelect = { destinationType = "mobile_push" },
                    )
                    DestinationOption(
                        value = "web_push",
                        title = "Web push (browser)",
                        subtitle = "Works in any modern browser, no app needed",
                        selected = destinationType == "web_push",
                        onSelect = { destinationType = "web_push" },
                    )
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    "Notify on",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                TriggerCheckbox(
                    label = "Member starts fronting",
                    checked = triggerOnStart,
                    onCheckedChange = { triggerOnStart = it },
                )
                TriggerCheckbox(
                    label = "Member stops fronting",
                    checked = triggerOnStop,
                    onCheckedChange = { triggerOnStop = it },
                )
                TriggerCheckbox(
                    label = "Co-fronter set changes",
                    checked = triggerOnCofrontChange,
                    onCheckedChange = { triggerOnCofrontChange = it },
                )

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        viewModel.create(
                            name = name.trim(),
                            recipientLabel = recipientLabel,
                            destinationType = destinationType,
                            triggerOnStart = triggerOnStart,
                            triggerOnStop = triggerOnStop,
                            triggerOnCofrontChange = triggerOnCofrontChange,
                        )
                    },
                    enabled = name.isNotBlank() && !state.isSubmitting &&
                        (triggerOnStart || triggerOnStop || triggerOnCofrontChange),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Create invite")
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun DestinationOption(
    value: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TriggerCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = checked,
                onClick = { onCheckedChange(!checked) },
                role = Role.Checkbox,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(label)
    }
}

@Composable
private fun ActivationUrlPanel(
    channelName: String,
    activationUrl: String,
    onDone: () -> Unit,
    context: Context,
) {
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "\"$channelName\" is ready",
            style = MaterialTheme.typography.titleMedium,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Send this link to the recipient. They open it on their device and the " +
            "channel goes active. The link is single-use; you can re-issue if it " +
            "expires.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            activationUrl,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Sheaf invite", activationUrl))
                Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Copy")
        }
        OutlinedButton(
            onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, activationUrl)
                    putExtra(Intent.EXTRA_SUBJECT, "Subscribe to my front updates")
                }
                context.startActivity(Intent.createChooser(send, "Share invite link"))
            },
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Outlined.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Share")
        }
    }
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Done") }
    Spacer(Modifier.height(24.dp))
}
