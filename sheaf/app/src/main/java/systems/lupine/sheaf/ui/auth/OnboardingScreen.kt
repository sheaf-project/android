package systems.lupine.sheaf.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.ui.settings.SettingsViewModel
import systems.lupine.sheaf.ui.settings.TotpSetupSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onNavigateToSystemSafety: () -> Unit,
    onNavigateToSpImport: () -> Unit,
    onContinue: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by settingsViewModel.state.collectAsState()
    var showTotpSheet by remember { mutableStateOf(false) }
    val totpEnabled = state.user?.totpEnabled == true

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(80.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("👋", style = MaterialTheme.typography.displaySmall)
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Welcome to Sheaf",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "A few quick steps to get your account set up. You can also do these later from Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            OnboardingAction(
                icon = Icons.Outlined.Lock,
                title = "Set up two-factor auth",
                description = "Protect your account with an authenticator app.",
                completed = totpEnabled,
                onClick = {
                    if (!totpEnabled) {
                        settingsViewModel.startTotpSetup()
                        showTotpSheet = true
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            OnboardingAction(
                icon = Icons.Outlined.Shield,
                title = "Configure System Safety",
                description = "Add a grace period and re-auth for destructive actions.",
                onClick = onNavigateToSystemSafety,
            )
            Spacer(Modifier.height(12.dp))
            OnboardingAction(
                icon = Icons.Outlined.Upload,
                title = "Import from Simply Plural",
                description = "Bring across your members, groups, and history.",
                onClick = onNavigateToSpImport,
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Continue to Sheaf")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showTotpSheet) {
        TotpSetupSheet(
            state = state,
            onAdvanceToVerify = { settingsViewModel.advanceTotpToVerify() },
            onVerify = { code -> settingsViewModel.verifyTotp(code) },
            onAdvanceToDone = { settingsViewModel.advanceTotpToDone() },
            onDismiss = {
                showTotpSheet = false
                settingsViewModel.resetTotpSetup()
            },
        )
    }
}

@Composable
private fun OnboardingAction(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    completed: Boolean = false,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = if (completed) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (completed) MaterialTheme.colorScheme.onTertiaryContainer
                               else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (completed) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Done", style = MaterialTheme.typography.labelSmall) },
                            enabled = false,
                        )
                    }
                }
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
