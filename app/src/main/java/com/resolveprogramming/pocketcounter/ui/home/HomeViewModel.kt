package com.resolveprogramming.pocketcounter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.local.LedgerRefreshSignal
import com.resolveprogramming.pocketcounter.data.local.TokenStore
import com.resolveprogramming.pocketcounter.data.local.ViewedMonthStore
import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.IssuerCardRepository
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.ConfirmReadyItem
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.GroupMode
import com.resolveprogramming.pocketcounter.domain.model.GroupSort
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.HomeKpis
import com.resolveprogramming.pocketcounter.domain.model.InvoicePaymentMatch
import com.resolveprogramming.pocketcounter.domain.model.LedgerGroup
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.domain.model.groupLedger
import com.resolveprogramming.pocketcounter.domain.notification.InvoicePaymentDetector
import com.resolveprogramming.pocketcounter.domain.notification.IssuerCardMatcher
import com.resolveprogramming.pocketcounter.domain.notification.confirmReadyItemOf
import com.resolveprogramming.pocketcounter.domain.notification.matchInvoicePayment
import com.resolveprogramming.pocketcounter.domain.usecase.ConfirmClassifiedNotificationUseCase
import com.resolveprogramming.pocketcounter.ui.contextos.CuratedPalette
import com.resolveprogramming.pocketcounter.ui.format.monthLabelPtBr
import com.resolveprogramming.pocketcounter.ui.transacoes.FormMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    /** Invoice-payment pushes that matched zero or several pending invoices — pick or dismiss, never confirm. */
    val invoicePrompts: List<InvoicePaymentPrompt> = emptyList(),
    /** The prompt whose picker sheet is open, if any. */
    val invoicePicker: InvoicePickerState? = null,
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

private data class InvoiceMatchContext(
    val pendingRows: List<HistoryItem> = emptyList(),
    val cards: Collection<CreditCard> = emptyList(),
    val learnedIssuers: Map<String, String> = emptyMap(),
    /** False when a neighbour-month fetch failed, so [pendingRows] is a truncated window. */
    val windowComplete: Boolean = true,
)

/** What one classified notification resolved to: a one-tap confirm, or a prompt that needs the picker. */
private sealed interface ClassifyOutcome {
    data class Ready(val item: ConfirmReadyItem) : ClassifyOutcome
    data class Invoice(val prompt: InvoicePaymentPrompt) : ClassifyOutcome
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val transactionRepository: TransactionRepository,
    private val tagRepository: TagRepository,
    private val cardRepository: CardRepository,
    private val issuerCardRepository: IssuerCardRepository,
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

    // Completed once loadLookups()'s first pass has settled state.cards, carrying whether that
    // pass actually loaded them. Invoice matching awaits this so it never reads an empty cards map
    // that is merely still in flight — or that failed to load — either of which would silently
    // disable the issuer veto for the whole session.
    private val cardsReady = CompletableDeferred<Boolean>()

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
            // Explicit try/catch, not runCatching: its Throwable net would also swallow
            // CancellationException, breaking structured concurrency. A lookup call throwing
            // instead of returning Result.failure must still not strand cardsReady uncompleted —
            // every invoice-shaped classify pass awaits it and would park forever.
            val cardsLoaded = try {
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
                cardsResult.isSuccess
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (!cardsReady.isCompleted) cardsReady.complete(cardsLoaded)
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
                    _state.update {
                        if (it.month != month) return@update it
                        // invoicePicker too: otherwise the sheet floats over another month's Home with
                        // a prompt no longer in state.
                        it.copy(
                            confirmReady = emptyList(),
                            invoicePrompts = emptyList(),
                            invoicePicker = null,
                            classifying = false,
                        )
                    }
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
            val batch = pending.take(CONFIRM_READY_CLASSIFY_CAP)
            val context = invoiceMatchContext(batch, month)
            val outcomes = batch
                .map { base -> async { classifyOne(base, context) } }
                .awaitAll()
                .filterNotNull()
            _state.update { s ->
                if (s.month != month || generation != classifyGeneration) return@update s
                val ready = outcomes.filterIsInstance<ClassifyOutcome.Ready>().map { it.item }
                val prompts = outcomes.filterIsInstance<ClassifyOutcome.Invoice>().map { it.prompt }
                val handled = (ready.map { it.notificationId } + prompts.map { it.notificationId }).toSet()
                lastClassifiedMonth = month
                s.copy(
                    classifying = false,
                    confirmReady = ready,
                    invoicePrompts = prompts,
                    pendingReviewCount = (pending.size - handled.size).coerceAtLeast(0),
                    pendingReviewFirstId = pending.firstOrNull { it.id !in handled }?.id,
                )
            }
        }
    }

    private suspend fun classifyOne(base: NotificationItem, context: InvoiceMatchContext): ClassifyOutcome? {
        val classified = notificationRepository.classify(base.id, base).getOrNull() ?: return null
        val isInvoiceShaped = InvoicePaymentDetector.isInvoicePaymentText(classified.notification.text)
        // A truncated pending-rows window must never resolve an invoice-shaped push: a same-amount
        // sibling invoice sitting in the month that failed to load would otherwise look absent,
        // upgrading ambiguity into a false-confidence Matched. Leave it in the revisar banner instead.
        if (!context.windowComplete && isInvoiceShaped) return null
        // Before confirmReadyItemOf, never after: the backend's own suggestion for these texts is an
        // expense, and acting on it is what duplicates the invoice the user already tracks.
        val match = matchInvoicePayment(
            notification = classified.notification,
            pendingRows = context.pendingRows,
            cards = context.cards,
            learnedIssuers = context.learnedIssuers,
        )
        if (match != null) return invoiceOutcome(classified.notification, match)
        val ready = confirmReadyItemOf(classified) ?: return null
        // The matcher declining (no cent-exact invoice, no resolvable card) must not fall through to
        // a one-tap CREATE: an invoice-shaped push may only settle a row, never create one. A backend-
        // echoed pendingTransactionId is a different, already-safe settle path and stays untouched.
        if (isInvoiceShaped && ready.pendingTransactionId == null) return null
        return ClassifyOutcome.Ready(ready)
    }

    private fun invoiceOutcome(
        notification: NotificationItem,
        match: InvoicePaymentMatch,
    ): ClassifyOutcome = when (match) {
        is InvoicePaymentMatch.Matched -> ClassifyOutcome.Ready(
            ConfirmReadyItem(
                notificationId = notification.id,
                draft = WizardDraft.fromNotification(notification),
                pendingTransactionId = match.invoice.id,
                notification = notification,
                pendingMatch = match.invoice,
                viaLearnedIssuer = match.viaLearnedIssuer,
            ),
        )
        is InvoicePaymentMatch.NeedsChoice -> ClassifyOutcome.Invoice(
            InvoicePaymentPrompt(notification = notification, candidates = match.candidates),
        )
        InvoicePaymentMatch.NoCandidates -> ClassifyOutcome.Invoice(
            InvoicePaymentPrompt(notification = notification, candidates = emptyList()),
        )
    }

    /**
     * The pending rows, cards and learned issuers an invoice-payment match is resolved against.
     * Skipped entirely — and the extra month fetches with it — unless [batch] holds at least one
     * invoice-payment-shaped text.
     */
    private suspend fun invoiceMatchContext(
        batch: List<NotificationItem>,
        month: YearMonth,
    ): InvoiceMatchContext {
        if (batch.none { InvoicePaymentDetector.isInvoicePaymentText(it.text) }) return InvoiceMatchContext()
        // A bounded wait: if loadLookups is still stuck (or somehow never scheduled) past this, treat
        // cards as not loaded rather than parking this classify pass — and the whole "classifying"
        // skeleton with it — forever. windowComplete = false defers the push to manual review instead
        // of resolving it with data we know is incomplete. A completed-but-failed load (cardsLoaded ==
        // false) gets the same treatment: an empty cards map must never be read as "no cards on file".
        val cardsLoaded = withTimeoutOrNull(CARDS_READY_TIMEOUT_MS) { cardsReady.await() } == true
        if (!cardsLoaded) return InvoiceMatchContext(windowComplete = false)
        val pendingRows = pendingRowsAround(month)
        // A read failure here must degrade the same way as a failed cards/pending-rows load: silently
        // falling back to an empty map could resolve a match that only a stale learned entry — now
        // unavailable — would otherwise have vetoed or narrowed to a different row.
        val learnedIssuers = runCatching { issuerCardRepository.getMap() }
        return InvoiceMatchContext(
            pendingRows = pendingRows.orEmpty(),
            cards = _state.value.cards.values,
            learnedIssuers = learnedIssuers.getOrDefault(emptyMap()),
            windowComplete = pendingRows != null && learnedIssuers.isSuccess,
        )
    }

    /**
     * Pending rows for the previous, current and next month, or null when a neighbour-month fetch
     * failed. There is no "all pending" source — only [TransactionRepository.getMonth] — so this is a
     * window, not the full set: an invoice whose DUE date falls outside it is not offered and the user
     * sees "nenhuma fatura pendente". The window spans both neighbours because a payment made near a
     * month boundary settles an invoice due on either side of it.
     */
    private suspend fun pendingRowsAround(month: YearMonth): List<HistoryItem>? = coroutineScope {
        val neighbours = listOf(month.minusMonths(1), month.plusMonths(1))
            .map { async { transactionRepository.getMonth(it.toString()) } }
            .awaitAll()
        if (neighbours.any { it.isFailure }) return@coroutineScope null
        (monthItems + neighbours.flatMap { it.getOrThrow() })
            .distinctBy { it.id }
            .filter { it.statusPayment == PaymentStatus.PENDING }
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
                    // pendingMatch is set only on the invoice-payment path — the toast has to say
                    // which of the two things happened: an invoice was settled, or a transaction saved.
                    val toast = item.pendingMatch?.let { "${it.displayTitle()} marcada como paga ✓" }
                        ?: "Transação confirmada"
                    _state.update {
                        it.copy(
                            confirmingIds = it.confirmingIds - item.notificationId,
                            flashId = transactionId,
                            flashNonce = it.flashNonce + 1,
                            toastMessage = toast,
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
    fun ignore(item: ConfirmReadyItem) = ignoreNotification(
        notificationId = item.notificationId,
        drop = { copy(confirmReady = confirmReady.filterNot { it.notificationId == item.notificationId }) },
        // Dedup: a same-month re-classify could have re-added this id while the ignore was in
        // flight; appending blindly would dupe the LazyColumn key.
        restore = { copy(confirmReady = confirmReady.filterNot { it.notificationId == item.notificationId } + item) },
        // A taught issuer→card mapping has no other undo — there is no "unmark paid" affordance
        // here — so dismissing a match it produced forgets it, which is what stops the same wrong
        // pick from repeating on the next push. A match the learned map had no part in (resolved by
        // amount alone, or by a plain name match) has no such mapping to forget.
        onIgnored = {
            if (item.pendingMatch != null && item.viaLearnedIssuer) {
                runCatching { issuerCardRepository.clear(item.notification.app) }
            }
        },
    )

    /** Dismisses an unresolved invoice-payment prompt. Same semantics as [ignore], different list. */
    fun dismissInvoicePrompt(prompt: InvoicePaymentPrompt) = ignoreNotification(
        notificationId = prompt.notificationId,
        drop = { copy(invoicePrompts = invoicePrompts.filterNot { it.notificationId == prompt.notificationId }) },
        restore = {
            copy(invoicePrompts = invoicePrompts.filterNot { it.notificationId == prompt.notificationId } + prompt)
        },
    )

    private fun ignoreNotification(
        notificationId: String,
        drop: HomeUiState.() -> HomeUiState,
        restore: HomeUiState.() -> HomeUiState,
        onIgnored: suspend () -> Unit = {},
    ) {
        if (notificationId in _state.value.confirmingIds) return
        viewModelScope.launch {
            _state.update { it.copy(confirmingIds = it.confirmingIds + notificationId).drop() }
            notificationRepository.markIgnored(notificationId)
                .onSuccess {
                    onIgnored()
                    _state.update {
                        it.copy(
                            confirmingIds = it.confirmingIds - notificationId,
                            toastMessage = "Notificação ignorada",
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            confirmingIds = it.confirmingIds - notificationId,
                            toastMessage = "Não foi possível ignorar",
                        ).restore()
                    }
                }
        }
    }

    fun openInvoicePicker(prompt: InvoicePaymentPrompt) {
        _state.update { it.copy(invoicePicker = InvoicePickerState(prompt = prompt)) }
    }

    /**
     * Always dismisses, even mid-confirm: `ModalBottomSheet` has already animated to Hidden by the
     * time `onDismissRequest` fires, so vetoing here would only leave state and the (invisible)
     * sheet disagreeing. A confirm already in flight keeps running; see [confirmInvoicePayment]'s
     * `onFailure` for how a failure surfaces once the sheet is gone.
     */
    fun dismissInvoicePicker() {
        _state.update { it.copy(invoicePicker = null) }
    }

    /**
     * Marks the picked invoice paid — never creates a transaction — then teaches the issuer→card
     * association so the next push from this issuer resolves without the picker. On failure the
     * sheet stays open with the selection intact.
     */
    fun confirmInvoicePayment(invoice: HistoryItem) {
        val prompt = _state.value.invoicePicker?.prompt ?: return
        if (prompt.notificationId in _state.value.confirmingIds) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    confirmingIds = it.confirmingIds + prompt.notificationId,
                    invoicePicker = it.invoicePicker?.copy(isConfirming = true, errorMessage = null),
                )
            }
            confirmClassifiedNotification(
                prompt.notificationId,
                WizardDraft.fromNotification(prompt.notification),
                invoice.id,
            )
                .onSuccess {
                    invoice.cardId?.let { cardId ->
                        // Teach the key resolve() would actually have used, not the raw app label —
                        // an SMS/aggregator delivery resolves via the text's leading token instead.
                        val key = IssuerCardMatcher.resolutionKey(
                            app = prompt.notification.app,
                            text = prompt.notification.text,
                            cards = _state.value.cards.values,
                        )
                        runCatching { issuerCardRepository.associate(key, cardId) }
                    }
                    _state.update {
                        it.copy(
                            confirmingIds = it.confirmingIds - prompt.notificationId,
                            invoicePicker = null,
                            invoicePrompts = it.invoicePrompts.filterNot { p ->
                                p.notificationId == prompt.notificationId
                            },
                            flashId = invoice.id,
                            flashNonce = it.flashNonce + 1,
                            toastMessage = "${invoice.displayTitle()} marcada como paga ✓",
                        )
                    }
                    // The shared collector (see init) reloads this month for us and every sibling screen.
                    ledgerRefresh.signal()
                }
                .onFailure {
                    _state.update { s ->
                        // The sheet may have already been dismissed while this call was in flight —
                        // an inline error nobody can see is no error at all, so toast it instead.
                        val message = "Não foi possível marcar como paga. Tente de novo."
                        s.copy(
                            confirmingIds = s.confirmingIds - prompt.notificationId,
                            invoicePicker = s.invoicePicker?.copy(isConfirming = false, errorMessage = message),
                            toastMessage = message.takeIf { s.invoicePicker == null } ?: s.toastMessage,
                        )
                    }
                }
        }
    }

    fun onManualRefresh() {
        if (_state.value.isRefreshing) return
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            // Drop the cached tag/context lookups first so this reload picks up categories/tags added on
            // the web — otherwise loadLookups() below just re-serves the stale in-memory snapshot.
            tagRepository.refreshLookups()
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
        // Never lower to match CONFIRM_READY_VISIBLE_CAP: pendingReviewCount = pending - ready.
        const val CONFIRM_READY_CLASSIFY_CAP = 10

        // Minimum time the pull-to-refresh indicator stays up, so a sub-frame reload can't strand
        // PullToRefreshBox in a re-triggering loop. Also reads as intentional feedback, not a flicker.
        const val MIN_REFRESH_INDICATOR_MS = 600L

        // Bounds how long an invoice-shaped classify pass waits on loadLookups's first pass to
        // settle state.cards. See invoiceMatchContext.
        const val CARDS_READY_TIMEOUT_MS = 5_000L
    }
}
