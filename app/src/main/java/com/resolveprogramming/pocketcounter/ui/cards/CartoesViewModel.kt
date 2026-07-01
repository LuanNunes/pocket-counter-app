package com.resolveprogramming.pocketcounter.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.local.CardPrefsStore
import com.resolveprogramming.pocketcounter.data.local.ViewedMonthStore
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.InvoiceItem
import com.resolveprogramming.pocketcounter.domain.model.OpenInvoice
import com.resolveprogramming.pocketcounter.domain.model.SummaryGroup
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.buildFaturaBreakdown
import com.resolveprogramming.pocketcounter.ui.format.monthLabelPtBr
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.YearMonth
import javax.inject.Inject
import kotlin.math.roundToInt

data class CartoesUiState(
    val monthKey: String = YearMonth.now().toString(),
    val monthLabel: String = "",
    val grandTotal: BigDecimal = BigDecimal.ZERO,
    val invoices: List<OpenInvoice> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val allContexts: List<TagContext> = emptyList(),
    val categoriesByCardId: Map<String, List<SummaryGroup>> = emptyMap(),
    val categoryA11yByCardId: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val showAddCard: Boolean = false,
    val isSavingCard: Boolean = false,
    val toastMessage: String? = null,
    /** Persisted: whether the gradient fatura card tile is collapsed to a slim line. */
    val cardCollapsed: Boolean = false,
)

@HiltViewModel
class CartoesViewModel @Inject constructor(
    private val cardRepository: CardRepository,
    private val tagRepository: TagRepository,
    private val viewedMonth: ViewedMonthStore,
    private val cardPrefs: CardPrefsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        CartoesUiState(
            monthKey = viewedMonth.month.value,
            monthLabel = monthLabelPtBr(YearMonth.parse(viewedMonth.month.value)),
        ),
    )
    val state: StateFlow<CartoesUiState> = _state.asStateFlow()

    init {
        loadTags()
        // Follow the app-wide viewed month: reload whenever it changes (incl. the initial value).
        viewModelScope.launch {
            viewedMonth.month.collect { key ->
                _state.update {
                    it.copy(monthKey = key, monthLabel = monthLabelPtBr(YearMonth.parse(key)), isLoading = true)
                }
                loadData()
            }
        }
        // Restore the persisted card-tile collapsed preference and keep it in sync.
        viewModelScope.launch {
            cardPrefs.tileCollapsed.collect { collapsed ->
                _state.update { it.copy(cardCollapsed = collapsed) }
            }
        }
    }

    /** Persist the card-tile collapsed preference; the collector above reflects it into state. */
    fun setCardCollapsed(collapsed: Boolean) {
        viewModelScope.launch { cardPrefs.setTileCollapsed(collapsed) }
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
                        it.copy(grandTotal = grand, invoices = invoices, isLoading = false).withCategories()
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

    private fun loadTags() {
        viewModelScope.launch {
            tagRepository.getAllTags().onSuccess { tags ->
                // Invoice items (and the rules learned from them) are expense-only; never offer
                // income categories in the fatura classify sheet.
                _state.update {
                    it.copy(allTags = tags.filter { t -> t.kind == TransactionType.EXPENSE }).withCategories()
                }
            }
            tagRepository.getAllContexts().onSuccess { contexts ->
                _state.update { it.copy(allContexts = contexts).withCategories() }
            }
        }
    }

    private fun CartoesUiState.withCategories(): CartoesUiState {
        val tagToContext = allTags.associate { it.id to it.idContext }
        val contextById = allContexts.associateBy { it.id }
        val byCard = invoices.associate { invoice ->
            invoice.card.id to buildFaturaBreakdown(invoice.items, invoice.total, tagToContext, contextById)
        }
        val a11y = byCard.mapValues { (_, groups) -> categoriesA11y(groups) }
        return copy(categoriesByCardId = byCard, categoryA11yByCardId = a11y)
    }

    // Mirror the chart/list: announce only positive shares, never an over-itemized fatura's negative remainder.
    private fun categoriesA11y(groups: List<SummaryGroup>): String =
        groups.filter { it.pct > 0f }.joinToString(", ") { "${it.name} ${(it.pct * 100).roundToInt()}%" }

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
