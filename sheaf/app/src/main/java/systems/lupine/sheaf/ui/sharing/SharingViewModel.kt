package systems.lupine.sheaf.ui.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.PendingExposureRead
import systems.lupine.sheaf.data.model.ShareAudit
import systems.lupine.sheaf.data.model.ShareGrantCreate
import systems.lupine.sheaf.data.model.ShareGrantRead
import systems.lupine.sheaf.data.model.ShareViewCreate
import systems.lupine.sheaf.data.model.ShareViewRead
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

/**
 * A raise the user asked for that the server bounced for credentials. Held so
 * the step-up sheet can replay it verbatim once they are supplied.
 */
sealed interface PendingRaise {
    data class Grant(val body: ShareGrantCreate) : PendingRaise
}

data class SharingUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val views: List<ShareViewRead> = emptyList(),
    val grants: List<ShareGrantRead> = emptyList(),
    val audit: ShareAudit = ShareAudit(),
    val pendingExposures: List<PendingExposureRead> = emptyList(),
    // Instance policy. Off means nothing new can be published, but existing
    // grants stay reachable so revoke never becomes unreachable.
    val publicProfilesEnabled: Boolean = false,
    val adultAttested: Boolean = false,
    val totpEnabled: Boolean = false,
    val authTier: String = "none",
    // Mirrors the server's own predicate for whether a raise needs re-auth. A
    // mirror can drift, so a 403 still wins over this.
    val stepUpArmed: Boolean = false,
    val graceDays: Int = 0,
    val busy: Boolean = false,
    val actionError: String? = null,
    val stepUp: PendingRaise? = null,
    val stepUpError: String? = null,
    val needsAttestation: PendingRaise? = null,
    // Shown exactly once: nothing can read a link token back afterwards.
    val newToken: String? = null,
) {
    val stepUpMeaningful: Boolean get() = stepUpArmed && authTier != "none"
}

@HiltViewModel
class SharingViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(SharingUiState())
    val state: StateFlow<SharingUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            runCatching {
                coroutineScope {
                    val viewsD = async { api.listShareViews() }
                    val grantsD = async { api.listShareGrants() }
                    // Never gated on the instance switch, so it answers even
                    // when everything else is dark.
                    val auditD = async { runCatching { api.getSharingAudit() }.getOrNull() }
                    val safetyD = async { runCatching { api.getSystemSafety() }.getOrNull() }
                    val meD = async { runCatching { api.getMe() }.getOrNull() }
                    Quint(viewsD.await(), grantsD.await(), auditD.await(), safetyD.await(), meD.await())
                }
            }
                .onSuccess { (views, grants, audit, safety, me) ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            views = views,
                            grants = grants,
                            audit = audit ?: ShareAudit(),
                            pendingExposures = safety?.pendingExposures ?: emptyList(),
                            publicProfilesEnabled = me?.publicProfilesEnabled ?: false,
                            adultAttested = me?.adultAttestedAt != null,
                            totpEnabled = me?.totpEnabled == true,
                            authTier = safety?.settings?.authTier ?: "none",
                            stepUpArmed = safety?.settings?.appliesToProfileVisibility ?: false,
                            graceDays = safety?.settings?.gracePeriodDays ?: 0,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, loadError = e.toUserMessage("Failed to load sharing"))
                    }
                }
        }
    }

    private data class Quint<A, B, C, D, E>(
        val a: A, val b: B, val c: C, val d: D, val e: E,
    )

    fun createView(name: String) {
        // Creating an empty view publishes nothing, so it is not an exposure
        // and takes no step-up.
        run(errorFallback = "Failed to create view") {
            api.createShareView(ShareViewCreate(name = name))
            reload()
        }
    }

    fun createGrant(viewId: String, subjectType: String, note: String?, expiresAt: String?) {
        val body = ShareGrantCreate(
            viewId = viewId,
            subjectType = subjectType,
            note = note?.ifBlank { null },
            expiresAt = expiresAt,
        )
        if (!_state.value.adultAttested) {
            _state.update { it.copy(needsAttestation = PendingRaise.Grant(body)) }
            return
        }
        if (_state.value.stepUpMeaningful) {
            _state.update { it.copy(stepUp = PendingRaise.Grant(body), stepUpError = null) }
            return
        }
        submitGrant(body)
    }

    /** Retry the held raise with credentials from the step-up sheet. */
    fun confirmStepUp(password: String?, totpCode: String?) {
        val pending = _state.value.stepUp ?: return
        when (pending) {
            is PendingRaise.Grant -> submitGrant(
                pending.body.copy(
                    password = password?.ifBlank { null },
                    totpCode = totpCode?.ifBlank { null },
                ),
            )
        }
    }

    fun dismissStepUp() { _state.update { it.copy(stepUp = null, stepUpError = null) } }

    fun attestAdult() {
        val held = _state.value.needsAttestation
        viewModelScope.launch {
            _state.update { it.copy(busy = true, actionError = null) }
            runCatching { api.attestAdult() }
                .onSuccess {
                    _state.update { it.copy(busy = false, adultAttested = true, needsAttestation = null) }
                    // Resume what they were doing rather than making them
                    // find the button again.
                    when (held) {
                        is PendingRaise.Grant ->
                            if (_state.value.stepUpMeaningful) {
                                _state.update { it.copy(stepUp = held, stepUpError = null) }
                            } else {
                                submitGrant(held.body)
                            }
                        null -> Unit
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(busy = false, actionError = e.toUserMessage("Failed to record the declaration"))
                    }
                }
        }
    }

    fun dismissAttestation() { _state.update { it.copy(needsAttestation = null) } }

    private fun submitGrant(body: ShareGrantCreate) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, actionError = null, stepUpError = null) }
            runCatching { api.createShareGrant(body) }
                .onSuccess { created ->
                    _state.update {
                        it.copy(
                            busy = false,
                            stepUp = null,
                            newToken = created.token,
                        )
                    }
                    reload()
                }
                .onFailure { e -> handleRaiseFailure(e, PendingRaise.Grant(body), "Failed to create the link") }
        }
    }

    private fun handleRaiseFailure(e: Throwable, raise: PendingRaise, fallback: String) {
        when (val err = e.toShareError(fallback)) {
            // The local mirror said no step-up was needed and the server
            // disagreed. Server wins.
            is ShareError.StepUpRequired ->
                _state.update { it.copy(busy = false, stepUp = raise, stepUpError = null) }
            is ShareError.BadCredentials ->
                _state.update { it.copy(busy = false, stepUp = raise, stepUpError = err.message) }
            is ShareError.AttestationRequired ->
                _state.update { it.copy(busy = false, stepUp = null, needsAttestation = raise) }
            else ->
                _state.update {
                    it.copy(busy = false, stepUp = null, actionError = err.message(fallback))
                }
        }
    }

    // Un-exposing is immediate and ungated server-side, so none of the three
    // below can ask for credentials.

    fun revokeGrant(id: String) = run(errorFallback = "Failed to revoke") {
        api.revokeShareGrant(id)
        reload()
    }

    fun rotateGrant(id: String) = run(errorFallback = "Failed to rotate") {
        val rotated = api.rotateShareGrant(id)
        _state.update { it.copy(newToken = rotated.token) }
        reload()
    }

    fun deleteView(id: String) = run(errorFallback = "Failed to delete view") {
        api.deleteShareView(id)
        reload()
    }

    fun clearToken() { _state.update { it.copy(newToken = null) } }
    fun clearActionError() { _state.update { it.copy(actionError = null) } }
    fun clearLoadError() { _state.update { it.copy(loadError = null) } }

    private suspend fun reload() {
        runCatching {
            coroutineScope {
                val viewsD = async { api.listShareViews() }
                val grantsD = async { api.listShareGrants() }
                val auditD = async { runCatching { api.getSharingAudit() }.getOrNull() }
                Triple(viewsD.await(), grantsD.await(), auditD.await())
            }
        }.onSuccess { (views, grants, audit) ->
            _state.update {
                it.copy(views = views, grants = grants, audit = audit ?: it.audit)
            }
        }
    }

    private fun run(errorFallback: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, actionError = null) }
            runCatching { block() }
                .onSuccess { _state.update { it.copy(busy = false) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(busy = false, actionError = e.toShareError(errorFallback).message(errorFallback))
                    }
                }
        }
    }
}
