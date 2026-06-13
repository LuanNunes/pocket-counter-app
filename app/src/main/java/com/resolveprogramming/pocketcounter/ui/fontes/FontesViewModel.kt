package com.resolveprogramming.pocketcounter.ui.fontes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.repository.PaymentSourceRepository
import com.resolveprogramming.pocketcounter.data.repository.SourceInput
import com.resolveprogramming.pocketcounter.data.repository.SourceRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
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
import java.time.YearMonth
import javax.inject.Inject

sealed interface FonteFormMode {
    data object Add : FonteFormMode
    data class Edit(val id: String) : FonteFormMode
}

/** Sources grouped under one Meio (or the trailing "Sem meio" bucket when [paymentSource] is null). */
data class FonteSection(
    val paymentSource: PaymentSource?,
    val title: String,
    val sources: List<Source>,
)

data class FonteDeleteTarget(
    val id: String,
    val name: String,
    val blocked: Boolean,
    val txCount: Int,
)

/**
 * Groups sources under each payment method, preserving payment order, with a trailing
 * "Sem meio" bucket for sources whose meio is missing. Every known Meio gets a section —
 * even with zero sources — so its per-section "+" add affordance stays reachable. Pure for testability.
 */
internal fun buildFonteSections(
    sources: List<Source>,
    payments: List<PaymentSource>,
): List<FonteSection> {
    val byPayment = sources.groupBy { it.idPaymentSource }
    val knownPaymentIds = payments.map { it.id }.toSet()
    return buildList {
        payments.forEach { ps -> add(FonteSection(ps, ps.name, byPayment[ps.id].orEmpty())) }
        val orphans = sources.filter { it.idPaymentSource !in knownPaymentIds }
        if (orphans.isNotEmpty()) add(FonteSection(null, "Sem meio", orphans))
    }
}

data class FontesUiState(
    val sections: List<FonteSection> = emptyList(),
    val allPaymentSources: List<PaymentSource> = emptyList(),
    val paymentSources: Map<String, PaymentSource> = emptyMap(),
    val tags: List<Tag> = emptyList(),
    val contexts: List<TagContext> = emptyList(),
    val formMode: FonteFormMode? = null,
    val editing: Source? = null,
    val preselectMeioId: String? = null,
    val confirmDelete: FonteDeleteTarget? = null,
    val toastMessage: String? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class FontesViewModel @Inject constructor(
    private val sourceRepository: SourceRepository,
    private val paymentSourceRepository: PaymentSourceRepository,
    private val transactionRepository: TransactionRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FontesUiState())
    val state: StateFlow<FontesUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val sources = sourceRepository.getAll().getOrDefault(emptyList())
            val payments = paymentSourceRepository.getAll().getOrDefault(emptyList())
            val tags = tagRepository.getAllTags().getOrDefault(emptyList())
            val contexts = tagRepository.getAllContexts().getOrDefault(emptyList())

            val sections = buildFonteSections(sources, payments)

            _state.update {
                it.copy(
                    sections = sections,
                    allPaymentSources = payments,
                    paymentSources = payments.associateBy { p -> p.id },
                    tags = tags,
                    contexts = contexts,
                    isLoading = false,
                )
            }
        }
    }

    fun openAdd(preselectMeioId: String? = null) =
        _state.update { it.copy(formMode = FonteFormMode.Add, editing = null, preselectMeioId = preselectMeioId) }

    fun openEdit(id: String) {
        val target = _state.value.sections.flatMap { it.sources }.firstOrNull { it.id == id }
        _state.update { it.copy(formMode = FonteFormMode.Edit(id), editing = target, preselectMeioId = null) }
    }

    fun closeForm() = _state.update { it.copy(formMode = null, editing = null, preselectMeioId = null) }

    fun save(input: SourceInput) {
        val mode = _state.value.formMode ?: return
        viewModelScope.launch {
            val result = when (mode) {
                is FonteFormMode.Add -> sourceRepository.create(input)
                is FonteFormMode.Edit -> sourceRepository.update(mode.id, input)
            }
            result
                .onSuccess {
                    _state.update { it.copy(formMode = null, editing = null, toastMessage = "Fonte salva") }
                    load()
                }
                .onFailure { _state.update { it.copy(toastMessage = "Não foi possível salvar") } }
        }
    }

    fun requestDelete(id: String) {
        viewModelScope.launch {
            val name = _state.value.sections.flatMap { it.sources }.firstOrNull { it.id == id }?.name.orEmpty()
            val txCount = transactionRepository.getMonth(YearMonth.now().toString()).getOrDefault(emptyList())
                .count { it.idSource == id }
            _state.update {
                it.copy(confirmDelete = FonteDeleteTarget(id, name, blocked = txCount > 0, txCount = txCount))
            }
        }
    }

    fun cancelDelete() = _state.update { it.copy(confirmDelete = null) }

    fun confirmDelete() {
        val target = _state.value.confirmDelete ?: return
        if (target.blocked) return
        viewModelScope.launch {
            sourceRepository.delete(target.id)
                .onSuccess {
                    _state.update { it.copy(confirmDelete = null, toastMessage = "Fonte excluída") }
                    load()
                }
                .onFailure {
                    _state.update {
                        it.copy(confirmDelete = null, toastMessage = "Não foi possível excluir: há vínculos")
                    }
                }
        }
    }

    fun consumeToast() = _state.update { it.copy(toastMessage = null) }
}
