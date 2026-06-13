package com.resolveprogramming.pocketcounter.ui.assistente

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.repository.AssistantRepository
import com.resolveprogramming.pocketcounter.domain.model.AssistantMessage
import com.resolveprogramming.pocketcounter.domain.model.AssistantMessageStatus
import com.resolveprogramming.pocketcounter.domain.model.AssistantResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

const val ASSISTANT_MAX_CHARS = 500
const val ASSISTANT_DAILY_LIMIT = 5

data class AssistantUiState(
    val items: List<AssistantMessage> = emptyList(),
    val input: String = "",
    val inlineError: String? = null,
    val busy: Boolean = false,
    /** null until the first successful answer reports the server's quota; 0 = exhausted. */
    val remaining: Int? = null,
    val loadingPhase: Int = 0,
    val unavailable: Boolean = false,
) {
    val canSend: Boolean get() = input.isNotBlank() && !busy && remaining != 0 && !unavailable
}

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val repository: AssistantRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()

    fun updateInput(text: String) =
        _state.update { it.copy(input = text.take(ASSISTANT_MAX_CHARS), inlineError = null) }

    fun send() {
        val q = _state.value.input.trim().take(ASSISTANT_MAX_CHARS)
        if (q.isBlank() || _state.value.busy) return
        ask(q)
    }

    fun sendSuggestion(text: String) {
        val s = _state.value
        if (s.busy || s.remaining == 0 || s.unavailable) return
        ask(text.take(ASSISTANT_MAX_CHARS))
    }

    fun retry(itemId: String) {
        val item = _state.value.items.firstOrNull { it.id == itemId } ?: return
        if (_state.value.busy) return
        _state.update { s -> s.copy(items = s.items.filterNot { it.id == itemId }) }
        ask(item.question)
    }

    private fun ask(question: String) {
        val id = UUID.randomUUID().toString()
        _state.update { s ->
            s.copy(
                items = s.items + AssistantMessage(id, question, AssistantMessageStatus.LOADING),
                input = "",
                inlineError = null,
                busy = true,
                loadingPhase = 0,
            )
        }
        // Cosmetic staged loading over the SINGLE request; cancelled when the result lands.
        val timer = viewModelScope.launch {
            delay(2500); _state.update { it.copy(loadingPhase = 1) }
            delay(3000); _state.update { it.copy(loadingPhase = 2) }
        }
        viewModelScope.launch {
            val result = repository.ask(question)
            timer.cancel()
            _state.update { s -> s.applyResult(id, question, result) }
        }
    }

    private fun AssistantUiState.applyResult(
        id: String,
        question: String,
        result: AssistantResult,
    ): AssistantUiState = when (result) {
        is AssistantResult.Success -> copy(
            items = items.map { if (it.id == id) it.copy(status = AssistantMessageStatus.OK, answer = result.answer) else it },
            busy = false,
            remaining = result.answer.remaining,
        )
        is AssistantResult.Validation -> copy(
            items = items.filterNot { it.id == id },
            input = question,
            inlineError = result.message,
            busy = false,
        )
        AssistantResult.QuotaExhausted -> copy(
            items = items.map { if (it.id == id) it.copy(status = AssistantMessageStatus.LIMIT) else it },
            busy = false,
            remaining = 0,
        )
        AssistantResult.ServerError -> copy(
            items = items.map { if (it.id == id) it.copy(status = AssistantMessageStatus.ERROR) else it },
            busy = false,
        )
        AssistantResult.Unavailable -> copy(
            items = items.filterNot { it.id == id },
            busy = false,
            unavailable = true,
        )
    }
}
