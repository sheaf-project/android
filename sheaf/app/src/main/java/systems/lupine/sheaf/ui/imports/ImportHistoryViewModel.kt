package systems.lupine.sheaf.ui.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.ImportJobStatus
import systems.lupine.sheaf.data.model.ImportJobSummary
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class ImportHistoryUiState(
    val items: List<ImportJobSummary> = emptyList(),
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
)

/**
 * History list of the user's import jobs. Wraps the cursor-paginated
 * /v1/imports endpoint. Refresh re-reads from the top; loadMore pulls
 * the next page using the last response's nextCursor.
 *
 * Cancel/archive on individual jobs goes through [ImportJobDetailViewModel];
 * the list view is read-only beyond pull-to-refresh.
 */
@HiltViewModel
class ImportHistoryViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportHistoryUiState())
    val state: StateFlow<ImportHistoryUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { api.listImports(limit = 25, includeArchived = false, cursor = null) }
                .onSuccess { resp ->
                    _state.update {
                        it.copy(
                            items = resp.items,
                            nextCursor = resp.nextCursor,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.toUserMessage("Couldn't load imports"))
                    }
                }
        }
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.isLoadingMore) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true, error = null) }
            runCatching { api.listImports(limit = 25, includeArchived = false, cursor = cursor) }
                .onSuccess { resp ->
                    _state.update {
                        it.copy(
                            items = it.items + resp.items,
                            nextCursor = resp.nextCursor,
                            isLoadingMore = false,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoadingMore = false, error = e.toUserMessage("Couldn't load more"))
                    }
                }
        }
    }
}

/** Whether the list-side action label is "Cancel" or "Archive" or neither. */
internal enum class JobActionability { CANCEL, ARCHIVE, NONE }

internal fun ImportJobSummary.actionability(): JobActionability = when {
    status == ImportJobStatus.PENDING -> JobActionability.CANCEL
    status in ImportJobStatus.terminal && archivedAt == null -> JobActionability.ARCHIVE
    else -> JobActionability.NONE
}

/** Human-readable label for an [ImportJobSummary.source] enum value. */
internal fun importSourceLabel(source: String): String = when (source) {
    "simplyplural_file" -> "Simply Plural"
    "sheaf_file"        -> "Sheaf export"
    "pluralkit_file"    -> "PluralKit (file)"
    "pluralkit_api"     -> "PluralKit (API)"
    "tupperbox_file"    -> "Tupperbox"
    "pluralspace_file"  -> "PluralSpace"
    "prism_file"        -> "Prism"
    else                -> source
}

/** Human-readable label for an [ImportJobSummary.status] enum value. */
internal fun importStatusLabel(status: String): String = when (status) {
    ImportJobStatus.PENDING   -> "Pending"
    ImportJobStatus.RUNNING   -> "Running"
    ImportJobStatus.COMPLETE  -> "Complete"
    ImportJobStatus.FAILED    -> "Failed"
    ImportJobStatus.CANCELLED -> "Cancelled"
    else                      -> status.replaceFirstChar(Char::titlecase)
}

/**
 * Summary count line for the list row, e.g. "12 members · 240 fronts".
 * Keeps it short — full breakdown lives on the detail screen.
 */
internal fun countsSummary(counts: Map<String, Int>): String {
    if (counts.isEmpty()) return ""
    val parts = mutableListOf<String>()
    counts["members_imported"]?.takeIf { it > 0 }?.let { parts += "$it members" }
    counts["fronts_imported"]?.takeIf { it > 0 }?.let { parts += "$it fronts" }
    counts["custom_fronts_imported"]?.takeIf { it > 0 }?.let { parts += "$it custom fronts" }
    counts["groups_imported"]?.takeIf { it > 0 }?.let { parts += "$it groups" }
    counts["tags_imported"]?.takeIf { it > 0 }?.let { parts += "$it tags" }
    return parts.take(3).joinToString(" · ")
}
