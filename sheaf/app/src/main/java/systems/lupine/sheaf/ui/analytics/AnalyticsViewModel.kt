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
import retrofit2.HttpException
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
            val preferredTz = canonicalTz(ZoneId.systemDefault().id)
            runCatching {
                // listMembers happens alongside the analytics call so we
                // can render member names + avatars without waiting on a
                // second round-trip. Sorting the stats by total time desc
                // is done in the render layer so the window switch keeps
                // a stable scroll position.
                val analytics = fetchAnalyticsWithTzFallback(
                    since = since.toString(),
                    until = until.toString(),
                    preferredTz = preferredTz,
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

    /**
     * Try the analytics call with [preferredTz]; if the server rejects
     * it as an unknown timezone (legacy IANA alias the server's tzdata
     * doesn't carry — e.g. `US/Eastern` on an Alpine-based image)
     * retry once with UTC so the screen still renders. Hour-of-day
     * shifts by the device offset in that case, which is a worse read
     * than local time but far better than a blank error screen.
     */
    private suspend fun fetchAnalyticsWithTzFallback(
        since: String,
        until: String,
        preferredTz: String,
    ) = try {
        api.getFrontingAnalytics(since = since, until = until, tz = preferredTz)
    } catch (e: HttpException) {
        if (e.code() == 400 && preferredTz != "UTC") {
            api.getFrontingAnalytics(since = since, until = until, tz = "UTC")
        } else {
            throw e
        }
    }
}

/**
 * Maps the most common legacy IANA aliases to their canonical
 * `Region/City` names. Many Docker base images carry only the canonical
 * tzdata and reject legacy aliases like `US/Eastern` with a 400, so we
 * normalise before hitting the wire. Unmapped values pass through.
 *
 * Not exhaustive — covers what real Android devices in the wild tend
 * to report. The HTTP-level retry-with-UTC fallback in
 * [AnalyticsViewModel.fetchAnalyticsWithTzFallback] catches the long
 * tail.
 */
private val TZ_ALIASES: Map<String, String> = mapOf(
    "US/Eastern" to "America/New_York",
    "US/Central" to "America/Chicago",
    "US/Mountain" to "America/Denver",
    "US/Pacific" to "America/Los_Angeles",
    "US/Alaska" to "America/Anchorage",
    "US/Hawaii" to "Pacific/Honolulu",
    "US/Arizona" to "America/Phoenix",
    "US/East-Indiana" to "America/Indiana/Indianapolis",
    "US/Indiana-Starke" to "America/Indiana/Knox",
    "US/Michigan" to "America/Detroit",
    "US/Samoa" to "Pacific/Pago_Pago",
    "Canada/Atlantic" to "America/Halifax",
    "Canada/Central" to "America/Winnipeg",
    "Canada/Eastern" to "America/Toronto",
    "Canada/Mountain" to "America/Edmonton",
    "Canada/Pacific" to "America/Vancouver",
    "Canada/Newfoundland" to "America/St_Johns",
    "Canada/Saskatchewan" to "America/Regina",
    "Canada/Yukon" to "America/Whitehorse",
    "Mexico/General" to "America/Mexico_City",
    "Mexico/BajaNorte" to "America/Tijuana",
    "Mexico/BajaSur" to "America/Mazatlan",
    "Brazil/East" to "America/Sao_Paulo",
    "Brazil/West" to "America/Manaus",
    "Brazil/Acre" to "America/Rio_Branco",
    "Brazil/DeNoronha" to "America/Noronha",
    "Chile/Continental" to "America/Santiago",
    "Chile/EasterIsland" to "Pacific/Easter",
    "Australia/ACT" to "Australia/Sydney",
    "Australia/LHI" to "Australia/Lord_Howe",
    "Australia/NSW" to "Australia/Sydney",
    "Australia/North" to "Australia/Darwin",
    "Australia/Queensland" to "Australia/Brisbane",
    "Australia/South" to "Australia/Adelaide",
    "Australia/Tasmania" to "Australia/Hobart",
    "Australia/Victoria" to "Australia/Melbourne",
    "Australia/West" to "Australia/Perth",
)

internal fun canonicalTz(id: String): String = TZ_ALIASES[id] ?: id
