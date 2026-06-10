package systems.lupine.sheaf.ui.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.AdminAuditEventRead
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SheafTopAppBar
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

private val ADMIN_AUDIT_ACTIONS = listOf(
    "USER_UPDATE", "USER_APPROVE", "USER_REJECT", "USER_MEMBER_LIMIT_SET",
    "USER_SAFETY_RESET", "USER_PENDING_BYPASS", "IMPORT_LOG_VIEW",
    "USER_SESSION_REVOKE", "USER_API_KEYS_ROTATE_ALL", "USER_SUSPEND",
    "USER_UNSUSPEND", "USER_DOSSIER_EXPORT", "USER_BAN", "USER_UNBAN",
    "USER_PASSWORD_RESET", "USER_EMAIL_CHANGE", "USER_TOTP_DISABLE",
    "USER_EMAIL_VERIFY", "USER_DELETION_CANCEL", "INVITE_CREATE",
    "INVITE_DELETE", "JOB_TRIGGER",
)

data class AdminAuditUiState(
    val isLoading: Boolean = false,
    val events: List<AdminAuditEventRead> = emptyList(),
    val page: Int = 0,
    val canLoadMore: Boolean = false,
    val loaded: Boolean = false,
    val actionFilter: String? = null,
    val error: String? = null,
)

@HiltViewModel
class AdminAuditViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminAuditUiState())
    val state: StateFlow<AdminAuditUiState> = _state.asStateFlow()

    init { loadMore(reset = true) }

    fun setActionFilter(action: String?) {
        _state.update { it.copy(actionFilter = action) }
        loadMore(reset = true)
    }

    fun loadMore(reset: Boolean = false) {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            val nextPage = if (reset) 1 else _state.value.page + 1
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                api.getAdminAuditEvents(
                    action = _state.value.actionFilter,
                    page = nextPage,
                    limit = PAGE,
                )
            }
                .onSuccess { rows ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            loaded = true,
                            events = if (reset) rows else it.events + rows,
                            page = nextPage,
                            canLoadMore = rows.size == PAGE,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.toUserMessage("Couldn't load audit log"))
                    }
                }
        }
    }

    companion object { const val PAGE = 50 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAuditScreen(
    onNavigateUp: () -> Unit,
    viewModel: AdminAuditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Audit log") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.error?.let { ErrorBanner(it, modifier = Modifier.padding(16.dp)) }
            ActionFilter(
                selected = state.actionFilter,
                onSelect = viewModel::setActionFilter,
            )
            when {
                state.events.isEmpty() && state.loaded && !state.isLoading ->
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No audit events.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                state.events.isEmpty() && state.isLoading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.events, key = { it.id }) { event ->
                        AuditCard(
                            action = event.action,
                            targetType = event.targetType,
                            actorEmail = event.adminEmail,
                            timestamp = event.createdAt,
                            reason = event.reason,
                            before = event.beforeJson,
                            after = event.afterJson,
                            targetLine = targetLine(event),
                        )
                    }
                    if (state.canLoadMore) {
                        item { LoadMoreRow(isLoading = state.isLoading) { viewModel.loadMore() } }
                    }
                }
            }
        }
    }
}

private fun targetLine(event: AdminAuditEventRead): String =
    buildString {
        append("Target: ")
        append(adminTargetLabel(event.targetType))
        event.targetUserId?.let { append(" · ").append(it.take(8)) }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionFilter(selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val display = selected?.let { adminActionLabel(it) } ?: "All actions"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text("Filter by action") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("All actions") },
                onClick = { onSelect(null); expanded = false },
            )
            ADMIN_AUDIT_ACTIONS.forEach { action ->
                DropdownMenuItem(
                    text = { Text(adminActionLabel(action)) },
                    onClick = { onSelect(action); expanded = false },
                )
            }
        }
    }
}
