package com.resolveprogramming.pocketcounter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.local.TokenStore
import com.resolveprogramming.pocketcounter.data.local.ViewedMonthStore
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.ConfirmReadyItem
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.GroupMode
import com.resolveprogramming.pocketcounter.domain.model.GroupSort
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.HomeKpis
import com.resolveprogramming.pocketcounter.domain.model.LedgerGroup
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.domain.model.groupLedger
import com.resolveprogramming.pocketcounter.domain.notification.confirmReadyItemOf
import com.resolveprogramming.pocketcounter.domain.usecase.ConfirmClassifiedNotificationUseCase
import com.resolveprogramming.pocketcounter.ui.contextos.CuratedPalette
import com.resolveprogramming.pocketcounter.ui.transacoes.FormMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    val pendingReviewCount: Int = 0,
    val pendingReviewFirstId: String? = null,
    /** Pending notifications the classifier recognized — confirmable in one tap, newest cap-limited. */
    val confirmReady: List<ConfirmReadyItem> = emptyList(),
    /** Notification ids whose one-tap confirm is in flight (drives the per-card spinner + double-tap guard). */
    val confirmingIds: Set<String> = emptySet(),
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
    private val confirmClassifiedNotification: ConfirmClassifiedNotificationUseCase,
    private val viewedMonth: ViewedMonthStore,
) : ViewModel() {

    private val ptBr = Locale("pt", "BR")

    // The full month's rows, before listType/groupBy filtering — the source for recomputed().
    private var monthItems: List<HistoryItem> = emptyList()

    // Bumped on each confirm-ready classify pass so only the latest pass commits (see
    // classifyPendingForConfirmReady). Confined to the Main dispatcher, so a plain Int is safe.
    private var classifyGeneration = 0

    private val _state = MutableStateFlow(
        YearMonth.parse(viewedMonth.month.value).let { ym ->
            HomeUiState(
                month = ym,
                monthLabel = monthLabel(ym),
                isCurrentMonth = ym == YearMonth.now(),
            )
        },
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        loadLookups()
        // Follow the app-wide viewed month so Home, Transações, Cartões and Resumo always agree;
        // emits the current value immediately, then on every cross-screen change.
        viewModelScope.launch {
            viewedMonth.month.collect { key ->
                val ym = YearMonth.parse(key)
                _state.update { it.copy(month = ym, monthLabel = monthLabel(ym)) }
                loadMonth()
            }
        }
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
            val pending = notificationRepository.getPendingReview().getOrDefault(emptyList())
            _state.update { s ->
                // A newer month navigation supersedes this in-flight result.
                if (s.month != month) return@update s
                monthItems = items
                s.copy(
                    isLoading = false,
                    isCurrentMonth = current,
                    pendingReviewCount = pending.size.takeIf { current } ?: 0,
                    pendingReviewFirstId = pending.firstOrNull()?.id?.takeIf { current },
                ).recomputed()
            }
            // Recognize confirm-ready items off the critical path: the ledger above is already rendered.
            if (current) {
                classifyPendingForConfirmReady(pending, month)
            } else {
                classifyGeneration++ // invalidate any in-flight pass; off-month shows no confirm-ready cards
                _state.update { if (it.month == month) it.copy(confirmReady = emptyList()) else it }
            }
        }
    }

    /**
     * Classifies up to [CONFIRM_READY_CLASSIFY_CAP] pending notifications concurrently and surfaces the
     * recognized ones as [HomeUiState.confirmReady]. Runs after the ledger render so the month list is
     * never blocked on N `/classify` round-trips, then narrows the "para revisar" banner to the items
     * that still need the wizard.
     *
     * [classifyGeneration] guards against overlapping passes: a month switch OR a same-month reload
     * (e.g. back-to-back [confirm] calls, each triggering [loadMonth]) starts a newer pass, and only the
     * latest one is allowed to commit — so a slow older pass can't overwrite fresh state or transiently
     * re-show an already-confirmed card.
     */
    private fun classifyPendingForConfirmReady(pending: List<NotificationItem>, month: YearMonth) {
        val generation = ++classifyGeneration
        viewModelScope.launch {
            val ready = pending.take(CONFIRM_READY_CLASSIFY_CAP)
                .map { base ->
                    async { notificationRepository.classify(base.id, base).getOrNull()?.let(::confirmReadyItemOf) }
                }
                .awaitAll()
                .filterNotNull()
            _state.update { s ->
                if (s.month != month || generation != classifyGeneration) return@update s
                val readyIds = ready.mapTo(mutableSetOf()) { it.notificationId }
                s.copy(
                    confirmReady = ready,
                    pendingReviewCount = (pending.size - ready.size).coerceAtLeast(0),
                    pendingReviewFirstId = pending.firstOrNull { it.id !in readyIds }?.id,
                )
            }
        }
    }

    /**
     * One-tap confirm of a recognized notification: optimistically drops the card, runs the shared
     * confirm core (create the transaction or mark the matched pending one paid), then reloads the
     * month. On failure the card is restored and a toast is shown. Guards against double-taps via
     * [HomeUiState.confirmingIds].
     */
    fun confirm(item: ConfirmReadyItem) {
        if (item.notificationId in _state.value.confirmingIds) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    confirmingIds = it.confirmingIds + item.notificationId,
                    confirmReady = it.confirmReady.filterNot { c -> c.notificationId == item.notificationId },
                )
            }
            confirmClassifiedNotification(item.notificationId, item.draft, item.pendingTransactionId)
                .onSuccess { transactionId ->
                    _state.update {
                        it.copy(
                            confirmingIds = it.confirmingIds - item.notificationId,
                            flashId = transactionId,
                            flashNonce = it.flashNonce + 1,
                            toastMessage = "Transação confirmada",
                        )
                    }
                    loadMonth()
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            confirmingIds = it.confirmingIds - item.notificationId,
                            confirmReady = it.confirmReady + item,
                            toastMessage = "Não foi possível confirmar",
                        )
                    }
                }
        }
    }

    fun refresh() {
        loadLookups()
        loadMonth()
    }

    // Month navigation writes to the shared store; the collector in init reloads in response, so the
    // change propagates to every month-scoped screen at once.
    fun selectMonth(delta: Int) = viewedMonth.step(delta)

    fun setMonth(month: YearMonth) = viewedMonth.set(month.toString())

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

    private companion object {
        // Per-Home-load classify round-trips are bounded: only the freshest pending items are offered
        // as one-tap confirms; the rest stay in the wizard-path "para revisar" banner.
        const val CONFIRM_READY_CLASSIFY_CAP = 10
    }
}
