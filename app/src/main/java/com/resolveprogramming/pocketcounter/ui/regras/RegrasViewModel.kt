package com.resolveprogramming.pocketcounter.ui.regras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.repository.ClassificationRuleRepository
import com.resolveprogramming.pocketcounter.data.repository.PaymentSourceRepository
import com.resolveprogramming.pocketcounter.data.repository.SourceRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.PaymentSource
import com.resolveprogramming.pocketcounter.domain.model.Source
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

data class RegrasUiState(
    val rules: List<ClassificationRule> = emptyList(),
    val sourcesById: Map<String, Source> = emptyMap(),
    val paymentSourcesById: Map<String, PaymentSource> = emptyMap(),
    val tagsById: Map<String, Tag> = emptyMap(),
    val contextsById: Map<String, TagContext> = emptyMap(),
    val confirmDelete: RegraDeleteTarget? = null,
    val toastMessage: String? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class RegrasViewModel @Inject constructor(
    private val ruleRepository: ClassificationRuleRepository,
    private val sourceRepository: SourceRepository,
    private val paymentSourceRepository: PaymentSourceRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RegrasUiState())
    val state: StateFlow<RegrasUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val rules = ruleRepository.getAll().getOrDefault(emptyList())
            val sources = sourceRepository.getAll().getOrDefault(emptyList())
            val payments = paymentSourceRepository.getAll().getOrDefault(emptyList())
            val tags = tagRepository.getAllTags().getOrDefault(emptyList())
            val contexts = tagRepository.getAllContexts().getOrDefault(emptyList())
            _state.update {
                it.copy(
                    rules = rules,
                    sourcesById = sources.associateBy { s -> s.id },
                    paymentSourcesById = payments.associateBy { p -> p.id },
                    tagsById = tags.associateBy { t -> t.id },
                    contextsById = contexts.associateBy { c -> c.id },
                    isLoading = false,
                )
            }
        }
    }

    fun requestDelete(id: String) {
        val rule = _state.value.rules.firstOrNull { it.id == id } ?: return
        _state.update {
            it.copy(confirmDelete = RegraDeleteTarget(id, rule.pattern.take(40)))
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
