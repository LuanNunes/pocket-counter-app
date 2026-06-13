package com.resolveprogramming.pocketcounter.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.InvoiceItem
import com.resolveprogramming.pocketcounter.domain.model.OpenInvoice
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class CartoesUiState(
    val grandTotal: BigDecimal = BigDecimal.ZERO,
    val invoices: List<OpenInvoice> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val allContexts: List<TagContext> = emptyList(),
    val isLoading: Boolean = true,
    val toastMessage: String? = null,
)

@HiltViewModel
class CartoesViewModel @Inject constructor(
    private val cardRepository: CardRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CartoesUiState())
    val state: StateFlow<CartoesUiState> = _state.asStateFlow()

    init {
        loadData()
        loadTags()
    }

    fun loadData() {
        viewModelScope.launch {
            cardRepository.getOpenInvoices()
                .onSuccess { invoices ->
                    val grand = invoices.fold(BigDecimal.ZERO) { acc, inv -> acc + inv.total }
                    _state.update {
                        it.copy(grandTotal = grand, invoices = invoices, isLoading = false)
                    }
                }
                .onFailure {
                    _state.update { it.copy(isLoading = false) }
                }
        }
    }

    private fun loadTags() {
        viewModelScope.launch {
            tagRepository.getAllTags().onSuccess { tags ->
                _state.update { it.copy(allTags = tags) }
            }
            tagRepository.getAllContexts().onSuccess { contexts ->
                _state.update { it.copy(allContexts = contexts) }
            }
        }
    }

    fun classifyPurchase(
        item: InvoiceItem,
        card: CreditCard,
        selectedTags: List<Tag>,
        learnRule: Boolean,
    ) {
        viewModelScope.launch {
            cardRepository.classifyPurchase(item.transactionId, selectedTags, learnRule, card)
                .onSuccess { outcome ->
                    val message = when {
                        outcome.ruleRequested && outcome.ruleCreated -> "Classificada ✓ + regra criada"
                        outcome.ruleRequested && !outcome.ruleCreated -> "Classificada ✓ (regra falhou)"
                        else -> "Compra classificada ✓"
                    }
                    _state.update { it.copy(toastMessage = message) }
                    loadData()
                }
                .onFailure {
                    _state.update { it.copy(toastMessage = "Não foi possível classificar") }
                }
        }
    }

    fun consumeToast() {
        _state.update { it.copy(toastMessage = null) }
    }
}
