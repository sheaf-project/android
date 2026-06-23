@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package systems.lupine.sheaf.ui.members

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.MemberAvatar
import systems.lupine.sheaf.ui.components.SheafTopAppBar
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class ArchivedMembersUiState(
    val isLoading: Boolean = true,
    val archived: List<MemberRead> = emptyList(),
    val unarchivingId: String? = null,
    val error: String? = null,
)

@HiltViewModel
class ArchivedMembersViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(ArchivedMembersUiState())
    val state: StateFlow<ArchivedMembersUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.archived.isEmpty(), error = null) }
            runCatching { api.listMembers() }
                .onSuccess { members ->
                    _state.update { it.copy(isLoading = false, archived = members.filter { m -> m.isArchived }) }
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.toUserMessage()) } }
        }
    }

    fun unarchive(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(unarchivingId = id, error = null) }
            runCatching { api.unarchiveMember(id) }
                .onSuccess {
                    _state.update { s -> s.copy(unarchivingId = null, archived = s.archived.filterNot { it.id == id }) }
                }
                .onFailure { e -> _state.update { it.copy(unarchivingId = null, error = e.toUserMessage("Couldn't unarchive member")) } }
        }
    }
}

@Composable
fun ArchivedMembersScreen(
    onNavigateUp: () -> Unit,
    viewModel: ArchivedMembersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Archived members") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Text(
                "Archived members are hidden from the roster and from switch and " +
                    "journal pickers, but stay in front history and existing entries. " +
                    "Unarchive one to bring it back.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )

            if (state.error != null) ErrorBanner(state.error!!, modifier = Modifier.padding(horizontal = 16.dp))

            when {
                state.isLoading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.archived.isEmpty() -> Text(
                    "No archived members.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                else -> state.archived.forEach { member ->
                    ListItem(
                        headlineContent = { Text(member.displayNameOrName) },
                        supportingContent = member.pronouns?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                        leadingContent = { MemberAvatar(member, size = 40.dp) },
                        trailingContent = {
                            if (state.unarchivingId == member.id) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                TextButton(onClick = { viewModel.unarchive(member.id) }) { Text("Unarchive") }
                            }
                        },
                    )
                }
            }
        }
    }
}
