package com.resolveprogramming.pocketcounter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.local.LedgerRefreshSignal
import com.resolveprogramming.pocketcounter.data.local.TokenStore
import com.resolveprogramming.pocketcounter.data.local.ViewedMonthStore
import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers
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
import com.resolveprogramming.pocketcounter.ui.format.monthLabelPtBr
import com.resolveprogramming.pocketcounter.ui.transacoes.FormMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.YearMonth
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
    /** True while the classifier's first pass for this month runs, so the UI shows a skeleton instead of a bare banner. */
    val classifying: Boolean = false,
    val openBillsTotal: BigDecimal = BigDecimal.ZERO,
    val openBillsCount: Int = 0,
    /** True only across a month flip, until loadOpenBills settles — keeps the fatura tile from flashing R$ 0. */
    val openBillsLoading: Boolean = false,
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
    val isRefreshing: Boolean = false,
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
    private val ledgerRefresh: LedgerRefreshSignal,
) : ViewModel() {

    // The full month's rows, before listType/groupBy filtering — the source for recomputed().
    private var monthItems: List<HistoryItem> = emptyList()

    // Bumped on each confirm-ready classify pass so only the latest pass commits (see
    // classifyPendingForConfirmReady). Confined to the Main dispatcher, so a plain Int is safe.
    private var classifyGeneration = 0

    // The last month a classify pass fully committed for. Guards the `classifying` skeleton to the first
    // pass of a month only, so background reloads (ledgerRefresh, confirm) don't re-flash it.
    private var lastClassifiedMonth: YearMonth? = null

    private val _state = MutableStateFlow(
        YearMonth.parse(viewedMonth.month.value).let { ym ->
            HomeUiState(
                month = ym,
                monthLabel = monthLabelPtBr(ym),
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
                // Clear the fatura tile on the month flip so it shows a neutral zero — not the previous
                // month's total — while loadOpenBills fetches the new statement off the critical path.
                _state.update {
                    it.copy(
                        month = ym,
                        monthLabel = monthLabelPtBr(ym),
                        openBillsTotal = BigDecimal.ZERO,
                        openBillsCount = 0,
                        openBillsLoading = true,
                    )
                }
                loadMonth()
            }
        }
        // Reload whenever the ledger changes — on this screen or a sibling (e.g. a row marked paid on
        // Transações) — so Pendente/saldo and the fatura tile never go stale in the foreground. This is
        // the single reload path for every mutation: emitters just call signal() and are served here too.
        viewModelScope.launch {
            ledgerRefresh.events.collect { loadMonth(showLoading = false) }
        }
    }

    private fun loadLookups() {
        viewModelScope.launch {
            val tagsResult = tagRepository.getAllTags()
            val contextsResult = tagRepository.getAllContexts()
            val cardsResult = cardRepository.getCards()
            val userName = tokenStore.getUserName().orEmpty()
            _state.update { s ->
                var next = s.copy(userName = userName)
                if (tagsResult.isSuccess) next = next.copy(tags = tagsResult.getOrThrow().associateBy { it.id })
                if (contextsResult.isSuccess) next = next.copy(contexts = contextsResult.getOrThrow())
                if (cardsResult.isSuccess) next = next.copy(cards = cardsResult.getOrThrow().associateBy { it.id })
                next.recomputed()
            }
        }
    }

    private fun loadMonth(showLoading: Boolean = true) {
        viewModelScope.launch { reloadMonth(showLoading) }
    }

    /**
     * Core reload: fetches the ledger and pending queue in parallel and commits the result
     * fail-soft — on failure the existing rendered content is kept and [isLoading] is cleared.
     * Returns the raw [Result] from [TransactionRepository.getMonth] so callers can react
     * (e.g. [onManualRefresh] shows an error toast); never touches [HomeUiState.isRefreshing].
     */
    private suspend fun reloadMonth(showLoading: Boolean): Result<List<HistoryItem>> {
        val month = _state.value.month
        val key = month.toString()
        val current = month == YearMonth.now()
        if (showLoading) _state.update { it.copy(isLoading = true) }
        // The fatura tile is secondary — load it concurrently in its own coroutine so its statement
        // fetch never sits on the ledger's critical path (that was the month-switch delay).
        loadOpenBills(month, key)
        // Fetch the ledger and the pending queue in parallel, not one after the other. Off-months
        // skip the pending call entirely — its banner is gated to the current month anyway.
        return coroutineScope {
            val itemsDeferred = async { transactionRepository.getMonth(key) }
            val pendingDeferred = async {
                if (current) notificationRepository.getPendingReview().getOrDefault(emptyList()) else emptyList()
            }
            val itemsResult = itemsDeferred.await()
            val pending = pendingDeferred.await()
            _state.update { s ->
                // A newer month navigation supersedes this in-flight result.
                if (s.month != month) return@update s
                itemsResult.fold(
                    onSuccess = { items ->
                        monthItems = items
                        s.copy(
                            isLoading = false,
                            isCurrentMonth = current,
                            // On the current month the classify pass is the sole writer of these — its
                            // terminal update settles the accurate pending/ready split even when nothing
                            // is recognized. Keep the prior values here so the banner doesn't flicker in
                            // and back out. Off-month there is no classify pass and no banner, so clear.
                            pendingReviewCount = if (current) s.pendingReviewCount else 0,
                            pendingReviewFirstId = if (current) s.pendingReviewFirstId else null,
                        ).recomputed()
                    },
                    onFailure = {
                        // Fail-soft: keep the existing rendered content; only clear the loading spinner.
                        s.copy(isLoading = false)
                    },
                )
            }
            // Recognize confirm-ready items off the critical path — only when the ledger succeeded.
            if (itemsResult.isSuccess) {
                if (current) {
                    classifyPendingForConfirmReady(pending, month)
                } else {
                    classifyGeneration++ // invalidate any in-flight pass; off-month shows no confirm-ready cards
                    _state.update { if (it.month == month) it.copy(confirmReady = emptyList(), classifying = false) else it }
                }
            }
            itemsResult
        }
    }

    /**
     * Loads the [month]'s open-invoice total for the fatura tile and patches it in independently, so the
     * tile updates a beat after the ledger without blocking it. A newer month navigation supersedes it.
     */
    private fun loadOpenBills(month: YearMonth, key: String) {
        viewModelScope.launch {
            val invoices = cardRepository.getOpenInvoices(RemoteMappers.monthKeyToRef(key))
                .getOrDefault(emptyList())
            val openBillsTotal = invoices.fold(BigDecimal.ZERO) { acc, inv -> acc + inv.total }
            _state.update { s ->
                if (s.month != month) return@update s
                s.copy(openBillsTotal = openBillsTotal, openBillsCount = invoices.size, openBillsLoading = false)
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
        // Only the first pass for a month shows the skeleton; later same-month passes (confirm reloads,
        // ledgerRefresh) run silently so the recognized cards don't blink through a loading state.
        val firstForMonth = lastClassifiedMonth != month
        if (firstForMonth) {
            _state.update { if (it.month == month) it.copy(classifying = true) else it }
        }
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
                lastClassifiedMonth = month
                s.copy(
                    classifying = false,
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
                    // The shared collector (see init) reloads this month for us and every sibling screen.
                    ledgerRefresh.signal()
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            confirmingIds = it.confirmingIds - item.notificationId,
                            confirmReady = it.confirmReady.filterNot { c -> c.notificationId == item.notificationId } + item,
                            toastMessage = "Não foi possível confirmar",
                        )
                    }
                }
        }
    }

    /**
     * Dismisses a single recognized notification without confirming it: optimistically drops only that
     * card, marks the notification ignored server-side, and leaves the ledger and the "para revisar"
     * count untouched. On failure the card is restored. Guards double-taps via [HomeUiState.confirmingIds].
     */
    fun ignore(item: ConfirmReadyItem) {
        if (item.notificationId in _state.value.confirmingIds) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    confirmingIds = it.confirmingIds + item.notificationId,
                    confirmReady = it.confirmReady.filterNot { c -> c.notificationId == item.notificationId },
                )
            }
            notificationRepository.markIgnored(item.notificationId)
                .onSuccess {
                    _state.update {
                        it.copy(
                            confirmingIds = it.confirmingIds - item.notificationId,
                            toastMessage = "Notificação ignorada",
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            confirmingIds = it.confirmingIds - item.notificationId,
                            // Dedup: a same-month re-classify could have re-added this id while the
                            // ignore was in flight; appending blindly would dupe the LazyColumn key.
                            confirmReady = it.confirmReady.filterNot { c -> c.notificationId == item.notificationId } + item,
                            toastMessage = "Não foi possível ignorar",
                        )
                    }
                }
        }
    }

    fun onManualRefresh() {
        if (_state.value.isRefreshing) return
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            loadLookups()
            var failed = false
            try {
                // Hold the indicator for a minimum span while reloading. A reload that resolves within a
                // frame (e.g. connection-refused when the backend is unreachable) would otherwise flip
                // isRefreshing true→false sub-frame, which leaves PullToRefreshBox re-firing onRefresh
                // forever — the "eternal refresh". The floor guarantees a clean true-then-false the
                // gesture can settle on, and the re-entrancy guard above covers the window.
                failed = coroutineScope {
                    val reload = async { reloadMonth(showLoading = false) }
                    delay(MIN_REFRESH_INDICATOR_MS)
                    reload.await().isFailure
                }
            } finally {
                // Clear the flag in finally so a throw (e.g. cancellation) can never strand the pull
                // indicator / disabled icon. Clear + toast in one update so no intermediate state leaks.
                _state.update { s ->
                    s.copy(
                        isRefreshing = false,
                        toastMessage = "Sem conexão. Tente novamente.".takeIf { failed } ?: s.toastMessage,
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
                    // The shared collector (see init) reloads this month for us and every sibling screen.
                    ledgerRefresh.signal()
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
                    // The shared collector (see init) reloads this month for us and every sibling screen.
                    ledgerRefresh.signal()
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

    private companion object {
        // Per-Home-load classify round-trips are bounded: only the freshest pending items are offered
        // as one-tap confirms; the rest stay in the wizard-path "para revisar" banner.
        const val CONFIRM_READY_CLASSIFY_CAP = 10

        // Minimum time the pull-to-refresh indicator stays up, so a sub-frame reload can't strand
        // PullToRefreshBox in a re-triggering loop. Also reads as intentional feedback, not a flicker.
        const val MIN_REFRESH_INDICATOR_MS = 600L
    }
}
