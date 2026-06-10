package systems.lupine.sheaf.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
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
import systems.lupine.sheaf.data.model.UserAdminActivityRead
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SheafTopAppBar
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class AdminActivityUiState(
    val isLoading: Boolean = false,
    val events: List<UserAdminActivityRead> = emptyList(),
    val page: Int = 0,
    val canLoadMore: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AdminActivityViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminActivityUiState())
    val state: StateFlow<AdminActivityUiState> = _state.asStateFlow()

    init { loadMore(reset = true) }

    fun loadMore(reset: Boolean = false) {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            val nextPage = if (reset) 1 else _state.value.page + 1
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { api.getMyAdminActivity(page = nextPage, limit = PAGE) }
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
                        it.copy(isLoading = false, error = e.toUserMessage("Couldn't load activity"))
                    }
                }
        }
    }

    companion object { const val PAGE = 50 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminActivityScreen(
    onNavigateUp: () -> Unit,
    viewModel: AdminActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Admin activity") },
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
            Text(
                "Actions any administrator has taken on your account. This is a " +
                    "transparency record; you don't need to do anything with it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                state.events.isEmpty() && state.loaded && !state.isLoading ->
                    EmptyAuditState("No admin actions on your account.")
                state.events.isEmpty() && state.isLoading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
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
                        )
                    }
                    if (state.canLoadMore) {
                        item {
                            LoadMoreRow(isLoading = state.isLoading) { viewModel.loadMore() }
                        }
                    }
                }
            }
        }
    }
}

// ── Shared audit row UI (also used by the admin viewer) ──────────────────────

@Composable
internal fun AuditCard(
    action: String,
    targetType: String,
    actorEmail: String?,
    timestamp: String,
    reason: String?,
    before: Map<String, Any?>?,
    after: Map<String, Any?>?,
    targetLine: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val beforeLines = auditFieldLines(before)
    val afterLines = auditFieldLines(after)
    val hasDetails = reason != null || beforeLines.isNotEmpty() || afterLines.isNotEmpty()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(enabled = hasDetails) { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(adminActionLabel(action), style = MaterialTheme.typography.titleSmall)
            Text(
                buildString {
                    append(formatAuditTimestamp(timestamp))
                    if (actorEmail != null) append(" · by $actorEmail")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (targetLine != null) {
                Text(
                    targetLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded && hasDetails) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                reason?.let {
                    Text("Reason", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                if (beforeLines.isNotEmpty()) {
                    AuditDiffBlock("Before", beforeLines)
                }
                if (afterLines.isNotEmpty()) {
                    AuditDiffBlock("After", afterLines)
                }
            } else if (hasDetails) {
                Text(
                    "Tap for details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AuditDiffBlock(label: String, lines: List<String>) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
    lines.forEach { line ->
        Text(
            line,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

@Composable
private fun EmptyAuditState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun LoadMoreRow(isLoading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            OutlinedButton(onClick = onClick) { Text("Load more") }
        }
    }
}
