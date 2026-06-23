@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package systems.lupine.sheaf.ui.support

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.ui.components.SectionHeader
import systems.lupine.sheaf.ui.components.SheafTopAppBar

@Composable
fun SupportScreen(
    onNavigateUp: () -> Unit,
    viewModel: SupportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    fun open(uri: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
    }

    val config = state.config

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Support") },
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
            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            val hasOperatorContact = config != null && (
                config.supportEmail != null || config.supportUrl != null ||
                    config.supportNote != null || config.statusUrl != null
                )

            if (hasOperatorContact) {
                SectionHeader("Contact this instance", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                config?.supportNote?.let { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                config?.supportEmail?.let { email ->
                    LinkRow(Icons.Outlined.Email, "Email support", email) { open("mailto:$email") }
                }
                config?.supportUrl?.let { url ->
                    LinkRow(Icons.Outlined.Public, "Support site", url) { open(url) }
                }
                config?.statusUrl?.let { url ->
                    LinkRow(Icons.Outlined.MonitorHeart, "Service status", url) { open(url) }
                }
            }

            if (config?.termsUrl != null || config?.privacyUrl != null) {
                SectionHeader("Policies", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                config.termsUrl?.let { url -> LinkRow(Icons.Outlined.Description, "Terms of service", url) { open(url) } }
                config.privacyUrl?.let { url -> LinkRow(Icons.Outlined.Description, "Privacy policy", url) { open(url) } }
            }

            // Static project links, independent of the operator's instance.
            SectionHeader("Sheaf", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            LinkRow(Icons.Outlined.Code, "Source & issues", "github.com/sheaf-project") {
                open("https://github.com/sheaf-project/android/issues")
            }
            LinkRow(Icons.Outlined.Shield, "Report a security issue", "security@sheaf.sh") {
                open("mailto:security@sheaf.sh")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle, maxLines = 1) },
            leadingContent = { Icon(icon, contentDescription = null) },
            trailingContent = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
        )
    }
}
