package systems.lupine.sheaf.ui.auth

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import systems.lupine.sheaf.BuildConfig
import systems.lupine.sheaf.R
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.repository.baseUrlError
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
    val cfClientId by viewModel.cfClientId.collectAsState()

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) onLoginSuccess()
    }

    var step by remember { mutableStateOf(if (savedBaseUrl.isBlank()) "url" else "auth") }
    var urlDraft by remember(savedBaseUrl) { mutableStateOf(savedBaseUrl) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("login") }
    val focusManager = LocalFocusManager.current
    val isLoading = uiState is AuthUiState.Loading

    var logoTapCount by remember { mutableIntStateOf(0) }
    var showCfDialog by remember { mutableStateOf(false) }

    // When server demands TOTP, switch to that step
    val totpState = uiState as? AuthUiState.AwaitingTotp
    val showTotp = totpState != null
    val isSolvingCaptcha = uiState is AuthUiState.SolvingCaptcha

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
            color = if (cfClientId.isNotBlank()) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.primary,
            onClick = {
                logoTapCount++
                if (logoTapCount >= 10) {
                    logoTapCount = 0
                    showCfDialog = true
                }
            },
            modifier = Modifier.size(80.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_login_logo),
                contentDescription = "Sheaf logo",
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showCfDialog) {
            CfAccessDialog(
                currentClientId = cfClientId,
                onSave = { id, secret -> viewModel.saveCfTokens(id, secret); showCfDialog = false },
                onClear = { viewModel.clearCfTokens(); showCfDialog = false },
                onDismiss = { showCfDialog = false },
            )
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
                    onUrlChange = { urlDraft = it; urlError = null },
                    error = urlError,
                    onContinue = {
                        // Default to the hosted instance when the user
                        // leaves the field blank - most users land there
                        // anyway, and the placeholder alone reads as an
                        // example rather than a "press Continue to use
                        // this" hint.
                        val resolved = urlDraft.trim().ifBlank { DEFAULT_HOSTED_INSTANCE }
                        val problem = baseUrlError(resolved, BuildConfig.DEBUG)
                        urlError = problem
                        if (problem == null) {
                            viewModel.saveBaseUrl(resolved)
                            step = "auth"
                        }
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
                    isLoading = isLoading || isSolvingCaptcha,
                    isSolvingCaptcha = isSolvingCaptcha,
                    error = (uiState as? AuthUiState.Error)?.message,
                    onSubmit = {
                        focusManager.clearFocus()
                        if (mode == "login") viewModel.login(email, password)
                        else viewModel.register(email, password, inviteCode)
                    },
                )
                "totp" -> TotpStep(
                    isLoading = isLoading,
                    error = totpState?.error,
                    onSubmit = { code, rememberDevice ->
                        focusManager.clearFocus()
                        viewModel.submitTotp(code, rememberDevice)
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

// ── Cloudflare Access dialog ──────────────────────────────────────────────────

@Composable
private fun CfAccessDialog(
    currentClientId: String,
    onSave: (clientId: String, clientSecret: String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var clientId by remember { mutableStateOf(currentClientId) }
    var clientSecret by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cloudflare Access") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Set service token headers for servers behind Cloudflare Access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text("CF-Access-Client-Id") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = { clientSecret = it },
                    label = { Text("CF-Access-Client-Secret") },
                    placeholder = { if (currentClientId.isNotBlank()) Text("Leave blank to keep existing") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(clientId.trim(), clientSecret.trim()) },
                enabled = clientId.isNotBlank() && (clientSecret.isNotBlank() || currentClientId.isNotBlank()),
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (currentClientId.isNotBlank()) {
                    TextButton(
                        onClick = onClear,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Clear") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

// ── Step 1: server URL ────────────────────────────────────────────────────────

@Composable
private fun ServerUrlStep(
    urlDraft: String,
    onUrlChange: (String) -> Unit,
    error: String?,
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
            label = { Text("Server URL or domain") },
            placeholder = { Text(DEFAULT_HOSTED_INSTANCE) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            // No isNotBlank gate: a blank submit resolves to the hosted
            // instance in the caller, so IME Done from an empty field
            // is a valid "yes, the default is fine" gesture.
            keyboardActions = KeyboardActions(onDone = { onContinue() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) ErrorBanner(error)
        Text(
            "Leave blank to use $DEFAULT_HOSTED_INSTANCE. https:// is added " +
                "automatically. Servers must use https; a path is fine " +
                "(e.g. example.org/sheaf).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Continue")
        }
    }
}

// Hosted Sheaf instance the Continue button falls back to when the
// user leaves the server-URL field blank. Lives at file scope so the
// placeholder, helper text, and outer onContinue all read the same
// string and a future move to a different default only changes here.
private const val DEFAULT_HOSTED_INSTANCE = "app.sheaf.sh"

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
    isSolvingCaptcha: Boolean,
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
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.EmailAddress },
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
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Password },
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
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    if (isSolvingCaptcha) Text("Verifying…")
                }
            } else Text(if (mode == "login") "Sign In" else "Create Account")
        }
    }
}

// ── Step 3: TOTP ──────────────────────────────────────────────────────────────

@Composable
private fun TotpStep(
    isLoading: Boolean,
    error: String?,
    onSubmit: (code: String, rememberDevice: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var rememberDevice by remember { mutableStateOf(false) }
    // A lost/wiped authenticator is exactly when recovery codes matter, and that
    // correlates with "changed phone", so the mobile client has to offer them too.
    var useRecoveryCode by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Refocus the field when switching modes so the user can just keep typing.
    LaunchedEffect(useRecoveryCode) { focusRequester.requestFocus() }

    // Recovery codes are 16 hex chars issued as two hyphenated groups
    // (xxxxxxxx-xxxxxxxx). The hyphen is part of the stored value, so we accept
    // it bare or hyphenated and re-insert the hyphen (lowercased) on submit.
    val recoveryHex = code.filter { it != '-' }
    val canSubmit = if (useRecoveryCode) recoveryHex.length == 16 else code.length == 6

    fun submit() {
        if (!canSubmit) return
        val payload = if (useRecoveryCode) {
            val hex = recoveryHex.lowercase()
            "${hex.substring(0, 8)}-${hex.substring(8)}"
        } else {
            code
        }
        onSubmit(payload, rememberDevice)
    }

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
            if (useRecoveryCode) {
                "Enter one of the recovery codes you saved when you set up two-factor authentication."
            } else {
                "Enter the 6-digit code from your authenticator app."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (error != null) ErrorBanner(error)

        OutlinedTextField(
            value = code,
            onValueChange = { new ->
                // No auto-submit so the user has a chance to tick "Remember this
                // device" before tapping Verify.
                code = if (useRecoveryCode) {
                    // Hex plus an optional hyphen; xxxxxxxx-xxxxxxxx is 17 chars.
                    new.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == '-' }.take(17)
                } else {
                    new.filter { it.isDigit() }.take(6)
                }
            },
            label = { Text(if (useRecoveryCode) "Recovery code" else "Authenticator code") },
            placeholder = { Text(if (useRecoveryCode) "xxxxxxxx-xxxxxxxx" else "000000") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (useRecoveryCode) KeyboardType.Password else KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )

        TextButton(
            onClick = {
                useRecoveryCode = !useRecoveryCode
                code = ""
            },
        ) {
            Text(if (useRecoveryCode) "Use an authenticator code instead" else "Use a recovery code instead")
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { rememberDevice = !rememberDevice },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = rememberDevice,
                onCheckedChange = { rememberDevice = it },
            )
            Text(
                "Remember this device for 30 days",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Button(
            onClick = { submit() },
            enabled = !isLoading && canSubmit,
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
