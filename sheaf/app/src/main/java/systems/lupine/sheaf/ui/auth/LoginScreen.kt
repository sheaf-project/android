package systems.lupine.sheaf.ui.auth

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.ui.components.ErrorBanner

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val savedBaseUrl by viewModel.baseUrl.collectAsState()
    val authConfig by viewModel.authConfig.collectAsState()

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) onLoginSuccess()
    }

    var step by remember { mutableStateOf(if (savedBaseUrl.isBlank()) "url" else "auth") }
    var urlDraft by remember(savedBaseUrl) { mutableStateOf(savedBaseUrl) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("login") }
    val focusManager = LocalFocusManager.current
    val isLoading = uiState is AuthUiState.Loading

    // When server demands TOTP, switch to that step
    val showTotp = uiState is AuthUiState.AwaitingTotp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(80.dp))

        // ── Logo ──────────────────────────────────────────────────────────────
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("S", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimary)
            }
        }

        Spacer(Modifier.height(20.dp))

        Text("Sheaf", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Plural system tracking",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(40.dp))

        AnimatedContent(
            targetState = when {
                showTotp                                          -> "totp"
                uiState is AuthUiState.AwaitingEmailVerification -> "email-verify"
                step == "url"                                    -> "url"
                else                                             -> "auth"
            },
            label = "step",
        ) { currentStep ->
            when (currentStep) {
                "url" -> ServerUrlStep(
                    urlDraft = urlDraft,
                    onUrlChange = { urlDraft = it },
                    onContinue = {
                        viewModel.saveBaseUrl(urlDraft.trim())
                        step = "auth"
                    },
                )
                "auth" -> AuthStep(
                    serverUrl = savedBaseUrl.ifBlank { urlDraft },
                    onChangeServer = { step = "url" },
                    email = email,
                    onEmailChange = { email = it },
                    password = password,
                    onPasswordChange = { password = it },
                    passwordVisible = passwordVisible,
                    onTogglePassword = { passwordVisible = !passwordVisible },
                    mode = mode,
                    onModeChange = { mode = it; viewModel.clearError() },
                    inviteCode = inviteCode,
                    onInviteCodeChange = { inviteCode = it },
                    showInviteCode = mode == "register" && authConfig?.registrationMode == "invite",
                    isLoading = isLoading,
                    error = (uiState as? AuthUiState.Error)?.message,
                    onSubmit = {
                        focusManager.clearFocus()
                        if (mode == "login") viewModel.login(email, password)
                        else viewModel.register(email, password, inviteCode)
                    },
                )
                "totp" -> TotpStep(
                    isLoading = isLoading,
                    error = (uiState as? AuthUiState.Error)?.message,
                    onSubmit = { code ->
                        focusManager.clearFocus()
                        viewModel.submitTotp(code)
                    },
                    onCancel = { viewModel.cancelTotp() },
                )
                "email-verify" -> EmailVerifyStep(
                    isLoading = isLoading,
                    error = (uiState as? AuthUiState.Error)?.message,
                    onVerify = { token ->
                        focusManager.clearFocus()
                        viewModel.verifyEmail(token)
                    },
                    onResend = { viewModel.resendVerificationEmail() },
                    onCancel = { viewModel.cancelEmailVerification() },
                )
            }
        }
    }
}

// ── Step 1: server URL ────────────────────────────────────────────────────────

@Composable
private fun ServerUrlStep(
    urlDraft: String,
    onUrlChange: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Where's your Sheaf server?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "Enter the hosted service URL or your self-hosted instance.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = urlDraft,
            onValueChange = onUrlChange,
            label = { Text("Server URL") },
            placeholder = { Text("https://api.sheaf.app") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (urlDraft.isNotBlank()) onContinue() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onContinue, enabled = urlDraft.isNotBlank(), modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Continue")
        }
    }
}

// ── Step 2: login / register ──────────────────────────────────────────────────

@Composable
private fun AuthStep(
    serverUrl: String,
    onChangeServer: () -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePassword: () -> Unit,
    mode: String,
    onModeChange: (String) -> Unit,
    inviteCode: String,
    onInviteCodeChange: (String) -> Unit,
    showInviteCode: Boolean,
    isLoading: Boolean,
    error: String?,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SuggestionChip(
            onClick = onChangeServer,
            label = {
                Text(
                    serverUrl.removePrefix("https://").removePrefix("http://"),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            },
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(selected = mode == "login", onClick = { onModeChange("login") }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Sign In") }
            SegmentedButton(selected = mode == "register", onClick = { onModeChange("register") }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Register") }
        }
        if (error != null) ErrorBanner(error)
        OutlinedTextField(
            value = email, onValueChange = onEmailChange, label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password, onValueChange = onPasswordChange, label = { Text("Password") }, singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (showInviteCode) ImeAction.Next else ImeAction.Done),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }, onDone = { onSubmit() }),
            trailingIcon = {
                IconButton(onClick = onTogglePassword) {
                    Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (showInviteCode) {
            OutlinedTextField(
                value = inviteCode,
                onValueChange = onInviteCodeChange,
                label = { Text("Invite Code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(onClick = onSubmit, enabled = !isLoading && email.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth().height(48.dp)) {
            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text(if (mode == "login") "Sign In" else "Create Account")
        }
    }
}

// ── Step 3: TOTP ──────────────────────────────────────────────────────────────

@Composable
private fun TotpStep(
    isLoading: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Lock icon
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("🔐", style = MaterialTheme.typography.headlineMedium)
            }
        }

        Text(
            "Two-factor authentication",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "Enter the 6-digit code from your authenticator app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (error != null) ErrorBanner(error)

        OutlinedTextField(
            value = code,
            onValueChange = { new ->
                // Only allow digits, max 6
                val filtered = new.filter { it.isDigit() }.take(6)
                code = filtered
                // Auto-submit when 6 digits entered
                if (filtered.length == 6) onSubmit(filtered)
            },
            label = { Text("Authenticator code") },
            placeholder = { Text("000000") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (code.length == 6) onSubmit(code) }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )

        Button(
            onClick = { onSubmit(code) },
            enabled = !isLoading && code.length == 6,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text("Verify")
        }

        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Back to sign in")
        }
    }
}

// ── Step 4: email verification ────────────────────────────────────────────────

@Composable
private fun EmailVerifyStep(
    isLoading: Boolean,
    error: String?,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
    onCancel: () -> Unit,
) {
    var token by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("✉️", style = MaterialTheme.typography.headlineMedium)
            }
        }

        Text(
            "Check your email",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "We've sent a verification link to your email address. Paste the token from the link below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (error != null) ErrorBanner(error)

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Verification token") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (token.isNotBlank()) onVerify(token) }),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { onVerify(token) },
            enabled = !isLoading && token.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text("Verify Email")
        }

        OutlinedButton(
            onClick = onResend,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Resend Email") }

        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}
