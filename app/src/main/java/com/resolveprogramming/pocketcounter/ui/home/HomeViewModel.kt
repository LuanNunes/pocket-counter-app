package com.resolveprogramming.pocketcounter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.local.TokenStore
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.GroupMode
import com.resolveprogramming.pocketcounter.domain.model.GroupSort
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.HomeKpis
import com.resolveprogramming.pocketcounter.domain.model.LedgerGroup
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.domain.model.automationPercent
import com.resolveprogramming.pocketcounter.domain.model.groupLedger
import com.resolveprogramming.pocketcounter.ui.contextos.CuratedPalette
import com.resolveprogramming.pocketcounter.ui.transacoes.FormMode
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

data class HomeUiState(
    val userName: String = "",
    val month: YearMonth = YearMonth.now(),
    val monthLabel: String = "",
    val isCurrentMonth: Boolean = true,
    val listType: TransactionType = TransactionType.EXPENSE,
    val groupBy: GroupMode = GroupMode.CONTEXTO,
    val kpis: HomeKpis = HomeKpis.from(emptyList()),
    val balance: BigDecimal = BigDecimal.ZERO,
    /** null off the current month — automation is the current-month inbox statistic. */
    val automationPct: Int? = null,
    val pendingReviewCount: Int = 0,
    val openBillsTotal: BigDecimal = BigDecimal.ZERO,
    val openBillsCount: Int = 0,
    val shownItems: List<HistoryItem> = emptyList(),
    val groupedSections: List<LedgerGroup> = emptyList(),
    val periodTotal: BigDecimal = BigDecimal.ZERO,
    val tags: Map<String, Tag> = emptyMap(),
    val contexts: List<TagContext> = emptyList(),
    val cards: Map<String, CreditCard> = emptyMap(),
    val formMode: FormMode? = null,
    val flashId: String? = null,
    val flashNonce: Int = 0,
    val toastMessage: String? = null,
    val isEmptyMonth: Boolean = false,
    val monthCount: Int = 0,
    val isLoading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val transactionRepository: TransactionRepository,
    private val tagRepository: TagRepository,
    private val cardRepository: CardRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val ptBr = Locale("pt", "BR")

    // The full month's rows, before listType/groupBy filtering — the source for recomputed().
    private var monthItems: List<HistoryItem> = emptyList()

    private val _state = MutableStateFlow(
        HomeUiState(
            month = YearMonth.now(),
            monthLabel = monthLabel(YearMonth.now()),
            isCurrentMonth = true,
        ),
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        loadLookups()
        loadMonth()
    }

    private fun loadLookups() {
        viewModelScope.launch {
            val tags = tagRepository.getAllTags().getOrDefault(emptyList())
            val contexts = tagRepository.getAllContexts().getOrDefault(emptyList())
            val cards = cardRepository.getCards().getOrDefault(emptyList())
            val invoices = cardRepository.getOpenInvoices().getOrDefault(emptyList())
            val userName = tokenStore.getUserName().orEmpty()
            val openBillsTotal = invoices.fold(BigDecimal.ZERO) { acc, inv -> acc + inv.total }
            _state.update {
                it.copy(
                    userName = userName,
                    tags = tags.associateBy { t -> t.id },
                    contexts = contexts,
                    cards = cards.associateBy { c -> c.id },
                    openBillsTotal = openBillsTotal,
                    openBillsCount = invoices.size,
                ).recomputed()
            }
        }
    }

    private fun loadMonth() {
        val month = _state.value.month
        val key = month.toString()
        val current = month == YearMonth.now()
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val items = transactionRepository.getMonth(key).getOrDefault(emptyList())
            val pendingCount = notificationRepository.getPendingReview()
                .getOrDefault(emptyList()).size
            val automation = notificationRepository.getAutomationStat().getOrNull()
            _state.update { s ->
                // A newer month navigation supersedes this in-flight result.
                if (s.month != month) return@update s
                monthItems = items
                val automationPct = automation
                    ?.takeIf { current }
                    ?.let { automationPercent(it.autoDone, it.monthTotal) }
                s.copy(
                    isLoading = false,
                    isCurrentMonth = current,
                    automationPct = automationPct,
                    pendingReviewCount = pendingCount.takeIf { current } ?: 0,
                ).recomputed()
            }
        }
    }

    fun selectMonth(delta: Int) = setMonth(_state.value.month.plusMonths(delta.toLong()))

    fun setMonth(month: YearMonth) {
        _state.update {
            it.copy(
                month = month,
                monthLabel = monthLabel(month),
                isCurrentMonth = month == YearMonth.now(),
            )
        }
        loadMonth()
    }

    fun setListType(type: TransactionType) {
        _state.update { it.copy(listType = type).recomputed() }
    }

    fun setGroupBy(mode: GroupMode) {
        _state.update { it.copy(groupBy = mode).recomputed() }
    }

    fun toggleStatus(item: HistoryItem) {
        viewModelScope.launch {
            val paid = item.statusPayment == PaymentStatus.PAID
            val action = when (paid) {
                true -> transactionRepository.markPending(item.id)
                false -> transactionRepository.markPaid(item.id)
            }
            action
                .onSuccess {
                    val msg = "Marcado como pendente".takeIf { paid } ?: "Marcado como pago"
                    _state.update { it.copy(flashId = item.id, flashNonce = it.flashNonce + 1, toastMessage = msg) }
                    loadMonth()
                }
                .onFailure { _state.update { it.copy(toastMessage = "Não foi possível atualizar") } }
        }
    }

    fun openAdd() = _state.update { it.copy(formMode = FormMode.Add()) }

    fun openEdit(item: HistoryItem) = _state.update { it.copy(formMode = FormMode.Edit(item.id)) }

    fun closeForm() = _state.update { it.copy(formMode = null) }

    fun saveForm(draft: WizardDraft) {
        val mode = _state.value.formMode ?: return
        viewModelScope.launch {
            val result = when (mode) {
                is FormMode.Add -> transactionRepository.save(draft)
                is FormMode.Edit -> transactionRepository.update(mode.itemId, draft)
            }
            result
                .onSuccess { id ->
                    val msg = "Transação atualizada".takeIf { mode is FormMode.Edit } ?: "Transação salva"
                    _state.update { it.copy(formMode = null, flashId = id, flashNonce = it.flashNonce + 1, toastMessage = msg) }
                    loadMonth()
                }
                .onFailure { _state.update { it.copy(toastMessage = "Não foi possível salvar") } }
        }
    }

    fun consumeToast() = _state.update { it.copy(toastMessage = null) }

    fun consumeFlash() = _state.update { it.copy(flashId = null) }

    /** Recomputes KPIs, shown rows, grouped sections and the period total from [monthItems]. */
    private fun HomeUiState.recomputed(): HomeUiState {
        val kpis = HomeKpis.from(monthItems)
        val shown = monthItems
            .filter { it.type == listType }
            .sortedByDescending { it.date }
        val grouped = run {
            if (groupBy == GroupMode.LISTA) return@run emptyList()
            groupLedger(
                items = shown,
                mode = groupBy,
                tags = tags,
                contexts = contexts,
                incomePalette = CuratedPalette.argb,
                expenseSort = GroupSort.SUBTOTAL_DESC,
            )
        }
        val periodTotal = shown.fold(BigDecimal.ZERO) { acc, item -> acc + item.amount.abs() }
        return copy(
            kpis = kpis,
            balance = kpis.totals.balance,
            shownItems = shown,
            groupedSections = grouped,
            periodTotal = periodTotal,
            isEmptyMonth = monthItems.isEmpty(),
            monthCount = monthItems.size,
        )
    }

    private fun monthLabel(ym: YearMonth): String {
        val month = ym.month.getDisplayName(TextStyle.FULL, ptBr).replaceFirstChar { it.uppercase(ptBr) }
        return "$month ${ym.year}"
    }
}
