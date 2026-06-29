package com.resolveprogramming.pocketcounter.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.local.ViewedMonthStore
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.InvoiceItem
import com.resolveprogramming.pocketcounter.domain.model.OpenInvoice
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class CartoesUiState(
    val monthKey: String = YearMonth.now().toString(),
    val monthLabel: String = "",
    val grandTotal: BigDecimal = BigDecimal.ZERO,
    val invoices: List<OpenInvoice> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val allContexts: List<TagContext> = emptyList(),
    val isLoading: Boolean = true,
    val showAddCard: Boolean = false,
    val isSavingCard: Boolean = false,
    val toastMessage: String? = null,
)

@HiltViewModel
class CartoesViewModel @Inject constructor(
    private val cardRepository: CardRepository,
    private val tagRepository: TagRepository,
    private val viewedMonth: ViewedMonthStore,
) : ViewModel() {

    private val ptBr = Locale("pt", "BR")
    private val _state = MutableStateFlow(
        CartoesUiState(
            monthKey = viewedMonth.month.value,
            monthLabel = monthLabel(YearMonth.parse(viewedMonth.month.value)),
        ),
    )
    val state: StateFlow<CartoesUiState> = _state.asStateFlow()

    init {
        loadTags()
        // Follow the app-wide viewed month: reload whenever it changes (incl. the initial value).
        viewModelScope.launch {
            viewedMonth.month.collect { key ->
                _state.update {
                    it.copy(monthKey = key, monthLabel = monthLabel(YearMonth.parse(key)), isLoading = true)
                }
                loadData()
            }
        }
    }

    fun loadData() {
        val key = _state.value.monthKey
        viewModelScope.launch {
            cardRepository.getOpenInvoices(refOf(key))
                .onSuccess { invoices ->
                    val grand = invoices.fold(BigDecimal.ZERO) { acc, inv -> acc + inv.total }
                    _state.update {
                        // A newer month step supersedes this result.
                        if (it.monthKey != key) return@update it
                        it.copy(grandTotal = grand, invoices = invoices, isLoading = false)
                    }
                }
                .onFailure {
                    _state.update { if (it.monthKey != key) it else it.copy(isLoading = false) }
                }
        }
    }

    fun stepMonth(delta: Int) = viewedMonth.step(delta)

    private fun refOf(monthKey: String): Int =
        YearMonth.parse(monthKey).let { it.year * 100 + it.monthValue }

    private fun monthLabel(ym: YearMonth): String =
        "${ym.month.getDisplayName(TextStyle.FULL, ptBr)} ${ym.year}"

    private fun loadTags() {
        viewModelScope.launch {
            tagRepository.getAllTags().onSuccess { tags ->
                // Invoice items (and the rules learned from them) are expense-only; never offer
                // income categories in the fatura classify sheet.
                _state.update { it.copy(allTags = tags.filter { t -> t.kind == TransactionType.EXPENSE }) }
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
        val itemId = item.itemId
        if (itemId == null) {
            // Fallback items are plain credit expenses, not invoice line items — they have no
            // item sub-resource to PUT against, so item-level classification is not available.
            _state.update { it.copy(toastMessage = "Esta compra não pode ser classificada na fatura") }
            return
        }
        viewModelScope.launch {
            cardRepository.classifyPurchase(item.invoiceId, itemId, selectedTags, learnRule, card)
                .onSuccess { outcome ->
                    val message = run {
                        if (outcome.ruleRequested && outcome.ruleCreated) return@run "Classificada ✓ + regra criada"
                        if (outcome.ruleRequested && !outcome.ruleCreated) return@run "Classificada ✓ (regra falhou)"
                        "Compra classificada ✓"
                    }
                    _state.update { it.copy(toastMessage = message) }
                    loadData()
                }
                .onFailure {
                    _state.update { it.copy(toastMessage = "Não foi possível classificar") }
                }
        }
    }

    fun openAddCard() = _state.update { it.copy(showAddCard = true) }

    fun dismissAddCard() = _state.update { it.copy(showAddCard = false) }

    fun addCard(name: String, brand: String?, closingDay: Int?, color: String?) {
        viewModelScope.launch {
            _state.update { it.copy(isSavingCard = true) }
            cardRepository.addCard(name, brand, closingDay, color)
                .onSuccess {
                    _state.update {
                        it.copy(isSavingCard = false, showAddCard = false, toastMessage = "Cartão adicionado ✓")
                    }
                    loadData()
                }
                .onFailure {
                    _state.update {
                        it.copy(isSavingCard = false, toastMessage = "Não foi possível adicionar o cartão")
                    }
                }
        }
    }

    fun consumeToast() {
        _state.update { it.copy(toastMessage = null) }
    }
}
