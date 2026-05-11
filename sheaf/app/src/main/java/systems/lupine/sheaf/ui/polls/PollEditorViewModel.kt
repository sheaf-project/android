package systems.lupine.sheaf.ui.polls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.PollCreate
import systems.lupine.sheaf.data.model.PollOptionCreate
import systems.lupine.sheaf.util.toUserMessage
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class PollEditorState(
    val isSubmitting: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,

    val question: String = "",
    val description: String = "",
    val kind: String = "single_choice",
    val resultsVisibility: String = "live",
    val closesAtIso: String = defaultClosesAt(),
    val options: List<String> = listOf("", ""),
)

private fun defaultClosesAt(): String {
    val inWeek = Instant.now().plusSeconds(7 * 24 * 3600)
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(inWeek.atZone(ZoneId.systemDefault()))
}

@HiltViewModel
class PollEditorViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(PollEditorState())
    val state: StateFlow<PollEditorState> = _state.asStateFlow()

    fun update(transform: PollEditorState.() -> PollEditorState) {
        _state.update(transform)
    }

    fun setOption(index: Int, value: String) {
        _state.update { s ->
            val next = s.options.toMutableList()
            if (index in next.indices) next[index] = value
            s.copy(options = next)
        }
    }

    fun addOption() {
        _state.update { s ->
            if (s.options.size >= 20) s
            else s.copy(options = s.options + "")
        }
    }

    fun removeOption(index: Int) {
        _state.update { s ->
            if (s.options.size <= 2) s
            else s.copy(options = s.options.filterIndexed { i, _ -> i != index })
        }
    }

    fun submit() {
        val s = _state.value
        if (!s.isValid()) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            runCatching {
                api.createPoll(
                    PollCreate(
                        question = s.question.trim(),
                        description = s.description.trim().takeIf { it.isNotEmpty() },
                        kind = s.kind,
                        resultsVisibility = s.resultsVisibility,
                        closesAt = s.closesAtIso,
                        options = s.options.map { PollOptionCreate(it.trim()) },
                    )
                )
            }
                .onSuccess { _state.update { it.copy(isSubmitting = false, saved = true) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(isSubmitting = false, error = e.toUserMessage("Couldn't create poll"))
                    }
                }
        }
    }
}

private fun PollEditorState.isValid(): Boolean {
    if (question.isBlank()) return false
    val cleanedOptions = options.map { it.trim() }.filter { it.isNotEmpty() }
    if (cleanedOptions.size < 2) return false
    if (cleanedOptions.toSet().size != cleanedOptions.size) return false
    return true
}
