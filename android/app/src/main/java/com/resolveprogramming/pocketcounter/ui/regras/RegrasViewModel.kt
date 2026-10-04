package com.resolveprogramming.pocketcounter.ui.regras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.repository.ClassificationRuleRepository
import com.resolveprogramming.pocketcounter.data.repository.RuleWriteOutcome
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegraDeleteTarget(val id: String, val patternLabel: String)

internal const val DUPLICATE_PATTERN_ERROR =
    "Já existe uma regra com esse padrão. Altere o padrão ou exclua a outra regra."
internal const val UPDATE_FAILED = "Não foi possível atualizar"

data class RegrasUiState(
    val rules: List<ClassificationRule> = emptyList(),
    val tagsById: Map<String, Tag> = emptyMap(),
    val contextsById: Map<String, TagContext> = emptyMap(),
    val confirmDelete: RegraDeleteTarget? = null,
    /** The rule being edited, or null when the edit sheet is closed. */
    val editTarget: ClassificationRule? = null,
    val savingEdit: Boolean = false,
    /** Save failure, shown under the pattern field: inside the sheet a toast renders behind the scrim. */
    val editError: String? = null,
    val toastMessage: String? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class RegrasViewModel @Inject constructor(
    private val ruleRepository: ClassificationRuleRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RegrasUiState())
    val state: StateFlow<RegrasUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val rules = ruleRepository.getAll().getOrDefault(emptyList())
            val tags = tagRepository.getAllTags().getOrDefault(emptyList())
            val contexts = tagRepository.getAllContexts().getOrDefault(emptyList())
            _state.update {
                it.copy(
                    rules = rules,
                    tagsById = tags.associateBy { t -> t.id },
                    contextsById = contexts.associateBy { c -> c.id },
                    isLoading = false,
                )
            }
        }
    }

    fun openEdit(id: String) {
        val rule = _state.value.rules.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(editTarget = rule, editError = null) }
    }

    fun cancelEdit() = _state.update { it.copy(editTarget = null, editError = null) }

    fun clearEditError() = _state.update { it.copy(editError = null) }

    /** Persists the edited [pattern], [idTag] and [active] onto the edited rule, then reloads. */
    fun saveEdit(pattern: String, idTag: String?, active: Boolean) {
        val rule = _state.value.editTarget ?: return
        if (_state.value.savingEdit) return
        _state.update { it.copy(savingEdit = true, editError = null) }
        viewModelScope.launch {
            val updated = rule.copy(pattern = pattern, idTag = idTag, active = active)
            ruleRepository.update(updated)
                .onSuccess { outcome ->
                    when (outcome) {
                        RuleWriteOutcome.Saved -> {
                            _state.update {
                                it.copy(editTarget = null, savingEdit = false, toastMessage = "Regra atualizada")
                            }
                            load()
                        }
                        RuleWriteOutcome.Duplicate -> reportEditError(DUPLICATE_PATTERN_ERROR)
                        // The server's own refusal, already localized; it knows which rule it broke.
                        is RuleWriteOutcome.Rejected ->
                            reportEditError(outcome.message?.takeIf { it.isNotBlank() } ?: UPDATE_FAILED)
                    }
                }
                .onFailure { reportEditError(UPDATE_FAILED) }
        }
    }

    private fun reportEditError(message: String) = _state.update {
        // Dismissed mid-flight: no sheet means no scrim, so the toast is the visible channel.
        if (it.editTarget == null) return@update it.copy(savingEdit = false, toastMessage = message)
        it.copy(savingEdit = false, editError = message)
    }

    fun requestDelete(id: String) {
        val rule = _state.value.rules.firstOrNull { it.id == id } ?: return
        val label = rule.pattern.take(40)
        _state.update {
            it.copy(confirmDelete = RegraDeleteTarget(id, label))
        }
    }

    fun cancelDelete() = _state.update { it.copy(confirmDelete = null) }

    fun confirmDelete() {
        val target = _state.value.confirmDelete ?: return
        viewModelScope.launch {
            ruleRepository.delete(target.id)
                .onSuccess {
                    _state.update { it.copy(confirmDelete = null, toastMessage = "Regra excluída") }
                    load()
                }
                .onFailure {
                    _state.update { it.copy(confirmDelete = null, toastMessage = "Não foi possível excluir") }
                }
        }
    }

    fun consumeToast() = _state.update { it.copy(toastMessage = null) }
}
