package systems.lupine.sheaf.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.FrontingAnalytics
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.util.toUserMessage
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Time windows the user can pick from. Each maps to a [ChronoUnit]
 * span the screen passes as the `since` query param. Matches the
 * iOS app's Week / Month / Year picker; web uses the same intervals
 * just labelled in days.
 */
enum class AnalyticsWindow(val label: String, val days: Long) {
    WEEK("7 days", 7L),
    MONTH("30 days", 30L),
    QUARTER("90 days", 90L),
    YEAR("1 year", 365L);
}

data class AnalyticsUiState(
    val window: AnalyticsWindow = AnalyticsWindow.MONTH,
    val analytics: FrontingAnalytics? = null,
    /** Roster, kept in sync with [analytics.members] order for rendering. */
    val members: List<MemberRead> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(AnalyticsUiState(isLoading = true))
    val state: StateFlow<AnalyticsUiState> = _state.asStateFlow()

    init {
        load(_state.value.window)
    }

    fun setWindow(window: AnalyticsWindow) {
        if (window == _state.value.window) return
        _state.update { it.copy(window = window) }
        load(window)
    }

    fun retry() {
        load(_state.value.window)
    }

    private fun load(window: AnalyticsWindow) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val until = Instant.now()
            val since = until.minus(window.days, ChronoUnit.DAYS)
            val tz = ZoneId.systemDefault().id
            runCatching {
                // listMembers happens alongside the analytics call so we
                // can render member names + avatars without waiting on a
                // second round-trip. Sorting the stats by total time desc
                // is done in the render layer so the window switch keeps
                // a stable scroll position.
                val analytics = api.getFrontingAnalytics(
                    since = since.toString(),
                    until = until.toString(),
                    tz = tz,
                )
                val members = api.listMembers()
                analytics to members
            }
                .onSuccess { (analytics, members) ->
                    _state.update {
                        it.copy(
                            analytics = analytics,
                            members = members,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.toUserMessage("Couldn't load analytics"),
                        )
                    }
                }
        }
    }
}
