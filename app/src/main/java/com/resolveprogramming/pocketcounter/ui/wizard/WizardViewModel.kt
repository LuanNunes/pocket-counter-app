package com.resolveprogramming.pocketcounter.ui.wizard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.ClassificationRuleRepository
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.SeriesRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.RuleAction
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.Series
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.Token
import com.resolveprogramming.pocketcounter.domain.model.TokenRole
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.domain.notification.NotificationTokenizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

enum class WizardStep(val index: Int, val label: String, val subtitle: String) {
    TYPE(0, "Tipo de transação", "1 de 4"),
    AMOUNT(1, "Valor e data", "2 de 4"),
    PAYMENT(2, "Pagamento", "3 de 4"),
    TAGS(3, "Tags", "4 de 4"),
}

data class WizardUiState(
    val notification: NotificationItem? = null,
    val draft: WizardDraft = WizardDraft(),
    val step: WizardStep = WizardStep.TYPE,
    val queue: List<String> = emptyList(),
    val cards: List<CreditCard> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val contexts: List<TagContext> = emptyList(),
    val tagSearchQuery: String = "",
    val tokens: List<Token> = emptyList(),
    val selectionAnchor: Int? = null,
    val selectionFocus: Int? = null,
    val availableSeries: List<Series> = emptyList(),
    val pendingTransactionId: String? = null,
    val isConfirmingPending: Boolean = false,
    val isSaving: Boolean = false,
    val pendingConfirmed: Boolean = false,
    val isLoading: Boolean = true,
    val isSwitching: Boolean = false,
    val error: String? = null,
) {
    val selectionRange: IntRange?
        get() = if (selectionAnchor != null && selectionFocus != null) {
            minOf(selectionAnchor, selectionFocus)..maxOf(selectionAnchor, selectionFocus)
        } else {
            null
        }
}

@HiltViewModel
class WizardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val notificationRepository: NotificationRepository,
    private val cardRepository: CardRepository,
    private val tagRepository: TagRepository,
    private val transactionRepository: TransactionRepository,
    private val seriesRepository: SeriesRepository,
    private val classificationRuleRepository: ClassificationRuleRepository,
) : ViewModel() {

    private var notificationId: String = savedStateHandle["notificationId"] ?: ""
    private val _state = MutableStateFlow(WizardUiState())
    val state: StateFlow<WizardUiState> = _state.asStateFlow()

    init {
        loadNotification(initial = true)
    }

    /**
     * Loads [notificationId] into the wizard. On the first load ([initial] = true) the state starts
     * blank, so the screen shows the full-screen spinner. When switching between queued items
     * ([initial] = false) the current item stays on screen (dimmed, behind a slim top progress bar)
     * while the next one resolves — the cached lookups are instant, so the swap is quick.
     */
    private fun loadNotification(initial: Boolean) {
        if (!initial) {
            _state.update { it.copy(isSwitching = true, error = null) }
        }
        viewModelScope.launch {
            val id = notificationId
            // Fetch the independent lookups concurrently — running them sequentially made every
            // notification transition wait on ~6 round-trips back to back, which felt slow.
            val baseDeferred = async { notificationRepository.getById(id).getOrNull() }
            val cardsDeferred = async { cardRepository.getCards().getOrDefault(emptyList()) }
            val tagsDeferred = async { tagRepository.getAllTags().getOrDefault(emptyList()) }
            val contextsDeferred = async { tagRepository.getAllContexts().getOrDefault(emptyList()) }
            val seriesDeferred = async { seriesRepository.getAll().getOrDefault(emptyList()) }
            val queueDeferred = async {
                notificationRepository.getPendingReview().getOrDefault(emptyList()).map { it.id }
            }

            val base = baseDeferred.await()
            if (base == null) {
                // Stale/deleted/already-classified id: surface an error instead of an endless
                // spinner (the screen shows a recoverable failure state with a way out).
                _state.update {
                    it.copy(
                        isLoading = false,
                        isSwitching = false,
                        error = "Não foi possível abrir esta notificação. " +
                            "Ela pode já ter sido classificada ou removida.",
                    )
                }
                return@launch
            }
            val cards = cardsDeferred.await()
            val tags = tagsDeferred.await()
            val contexts = contextsDeferred.await()
            val series = seriesDeferred.await()
            val queue = queueDeferred.await()

            val classifyResult = notificationRepository.classify(id, base)
            val classified = classifyResult.getOrNull()

            if (classified?.pendingTransactionId != null) {
                _state.value = WizardUiState(
                    notification = classified.notification,
                    queue = queue,
                    cards = cards,
                    allTags = tags,
                    contexts = contexts,
                    availableSeries = series,
                    pendingTransactionId = classified.pendingTransactionId,
                    isConfirmingPending = true,
                    isLoading = false,
                )
                return@launch
            }

            val notification = classified?.notification ?: base
            val draft = WizardDraft.fromNotification(notification)
            val degradeError = classifyResult.exceptionOrNull()?.message

            val tokens = notification.tokens.ifEmpty {
                NotificationTokenizer.tokenize(notification.text, notification.parsed)
            }

            // Switching to a different item resets to that item's fresh draft/step/tokens; only the
            // on-screen transition kept the previous item visible until this point.
            _state.value = WizardUiState(
                notification = notification,
                draft = draft,
                step = resolveStartStep(notification),
                queue = queue,
                cards = cards,
                allTags = tags,
                contexts = contexts,
                availableSeries = series,
                tokens = tokens,
                isLoading = false,
                error = degradeError,
            )
        }
    }

    /** Switches the wizard to a different queued item in place, keeping the current one visible. */
    private fun goTo(id: String) {
        if (_state.value.isSwitching || _state.value.isSaving) return
        notificationId = id
        loadNotification(initial = false)
    }

    private fun resolveStartStep(notification: NotificationItem): WizardStep {
        if (notification.status == NotificationStatus.NEEDS_TAGS) return WizardStep.TAGS
        return WizardStep.TYPE
    }

    fun selectType(type: TransactionType) {
        _state.update { it.copy(draft = it.draft.withType(type)) }
    }

    fun updateAmount(amount: BigDecimal?) {
        _state.update { it.copy(draft = it.draft.copy(amount = amount)) }
    }

    /**
     * The "Descrição" field is the persisted title (draft.name); merchant tracks it as the
     * non-persisted series-name/hint fallback, so the two never diverge. Blank → null.
     */
    fun updateName(value: String) {
        _state.update {
            it.copy(draft = it.draft.copy(name = value, merchant = value.takeIf { v -> v.isNotBlank() }))
        }
    }

    fun updateDate(date: LocalDate) {
        _state.update { it.copy(draft = it.draft.copy(date = date)) }
    }

    fun updateStatusPayment(status: PaymentStatus) {
        _state.update { it.copy(draft = it.draft.copy(statusPayment = status)) }
    }

    fun toggleInstallments(enabled: Boolean) {
        _state.update { state ->
            val notification = state.notification
            val draft = run {
                if (enabled && notification?.parsed?.installments != null) {
                    return@run state.draft.copy(
                        installments = notification.parsed.installments,
                        installmentValue = notification.parsed.installmentValue,
                    )
                }
                state.draft.copy(installments = null, installmentValue = null)
            }
            state.copy(draft = draft)
        }
    }

    fun selectPaymentMethod(method: PaymentMethod) {
        _state.update { it.copy(draft = it.draft.withPaymentMethod(method)) }
    }

    fun selectCard(cardId: String) {
        _state.update { it.copy(draft = it.draft.copy(cardId = cardId)) }
    }

    fun toggleFixo(enabled: Boolean) {
        _state.update { it.copy(draft = it.draft.copy(isFixo = enabled)) }
    }

    fun updateRecurrenceDay(day: Int?) {
        _state.update { it.copy(draft = it.draft.copy(recurrenceDay = day)) }
    }

    fun selectSeries(id: String?) {
        _state.update { it.copy(draft = it.draft.copy(seriesId = id)) }
    }

    fun updateTagSearch(query: String) {
        _state.update { it.copy(tagSearchQuery = query) }
    }

    fun toggleTag(tagId: String) {
        _state.update { it.copy(draft = it.draft.withTagToggled(tagId)) }
    }

    fun toggleLearnRule(enabled: Boolean) {
        _state.update { it.copy(draft = it.draft.copy(learnRule = enabled)) }
    }

    /**
     * Routes a token tap into the span selection model:
     *  - tapping an already-assigned token selects its whole contiguous same-role run (edit mode),
     *  - tapping with no active selection starts a length-1 selection,
     *  - tapping with an active selection extends it, keeping the original anchor sticky.
     */
    fun tapToken(i: Int) {
        _state.update { state ->
            val tokens = state.tokens
            val role = tokens.getOrNull(i)?.role
            if (role != null) {
                var start = i
                while (start > 0 && tokens[start - 1].role == role) start--
                var end = i
                while (end < tokens.lastIndex && tokens[end + 1].role == role) end++
                return@update state.copy(selectionAnchor = start, selectionFocus = end)
            }
            if (state.selectionAnchor == null) {
                return@update state.copy(selectionAnchor = i, selectionFocus = i)
            }
            state.copy(selectionFocus = i)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectionAnchor = null, selectionFocus = null) }
    }

    fun assignRoleToSelection(role: TokenRole) {
        _state.update { state ->
            val range = state.selectionRange ?: return@update state
            val joined = state.tokens.subList(range.first, range.last + 1)
                .joinToString(" ") { it.text }
            val newTokens = state.tokens.mapIndexed { i, token ->
                run {
                    if (i in range) return@run token.copy(role = role, value = joined)
                    if (token.role == role) return@run token.copy(role = null, value = null)
                    token
                }
            }
            val newDraft = applyTokenRoleToDraft(state.draft, joined, role)
            state.copy(
                tokens = newTokens,
                draft = newDraft,
                selectionAnchor = null,
                selectionFocus = null,
            )
        }
    }

    fun removeRoleFromSelection() {
        _state.update { state ->
            val range = state.selectionRange ?: return@update state
            val removedRole = state.tokens.getOrNull(range.first)?.role
            val newTokens = state.tokens.mapIndexed { i, token ->
                token.copy(role = null, value = null).takeIf { i in range } ?: token
            }
            val newDraft = run {
                when (removedRole) {
                    TokenRole.AMOUNT -> return@run state.draft.copy(amount = null)
                    // Clear only the merchant hint — name is the user-editable Descrição and must
                    // not be wiped when un-marking the merchant span.
                    TokenRole.MERCHANT -> return@run state.draft.copy(merchant = null)
                    TokenRole.DATE -> return@run state.draft.copy(date = null)
                    TokenRole.TYPE -> Unit
                    TokenRole.PAYMENT -> Unit
                    TokenRole.INSTALLMENTS -> Unit
                    null -> Unit
                }
                state.draft
            }
            state.copy(
                tokens = newTokens,
                draft = newDraft,
                selectionAnchor = null,
                selectionFocus = null,
            )
        }
    }

    private fun applyTokenRoleToDraft(
        draft: WizardDraft,
        joined: String,
        role: TokenRole,
    ): WizardDraft = when (role) {
        TokenRole.AMOUNT ->
            draft.copy(amount = NotificationTokenizer.parseBrAmount(joined) ?: draft.amount)
        TokenRole.MERCHANT -> draft.copy(merchant = joined, name = joined)
        TokenRole.DATE -> draft.copy(date = parseBrDate(joined) ?: draft.date)
        // TYPE/PAYMENT/INSTALLMENTS can't be reliably derived from free token text;
        // the chip still highlights the span, but the draft field is set elsewhere.
        TokenRole.TYPE -> draft
        TokenRole.PAYMENT -> draft
        TokenRole.INSTALLMENTS -> draft
    }

    /** Parses a BR-formatted date token: dd/MM/yyyy, dd/MM/yy, or dd/MM (current year). */
    private fun parseBrDate(text: String): LocalDate? {
        val m = Regex("""(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?""").find(text.trim()) ?: return null
        return runCatching {
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val yearRaw = m.groupValues[3]
            val year = run {
                if (yearRaw.isEmpty()) return@run LocalDate.now().year
                if (yearRaw.length == 2) return@run 2000 + yearRaw.toInt()
                yearRaw.toInt()
            }
            LocalDate.of(year, month, day)
        }.getOrNull()
    }

    fun nextStep() {
        _state.update { state ->
            val nextStep = WizardStep.entries.getOrNull(state.step.index + 1) ?: return@update state
            state.copy(step = nextStep)
        }
    }

    fun previousStep() {
        _state.update { state ->
            val prevStep = WizardStep.entries.getOrNull(state.step.index - 1) ?: return@update state
            state.copy(step = prevStep)
        }
    }

    /** Jumps to the next still-pending item in place; wraps past the last back to the first. */
    fun skipToNext() {
        val state = _state.value
        if (state.isSwitching || state.isSaving) return
        val queue = state.queue
        if (queue.size < 2) return
        val i = queue.indexOf(notificationId)
        if (i < 0) return
        goTo(queue[(i + 1) % queue.size])
    }

    /** Jumps to the previous still-pending item in place; wraps before the first to the last. */
    fun skipToPrevious() {
        val state = _state.value
        if (state.isSwitching || state.isSaving) return
        val queue = state.queue
        if (queue.size < 2) return
        val i = queue.indexOf(notificationId)
        if (i < 0) return
        goTo(queue[(i - 1 + queue.size) % queue.size])
    }

    fun save(onDone: () -> Unit) {
        if (_state.value.isSaving || _state.value.isSwitching) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val draft = _state.value.draft
            transactionRepository.save(draft)
                .onSuccess { transactionId ->
                    // The transaction is the source of truth and is never rolled back. The
                    // recurring-series steps below are best-effort: a series create/link failure
                    // is swallowed so it can't block advancing. Carry-forward later seeds each
                    // instance's amount from the source month — the backend has no series
                    // defaultAmount (handoff §3.3 divergence).
                    linkSeries(draft, transactionId)
                    runCatching { notificationRepository.markClassified(notificationId, transactionId) }
                    // Best-effort: persist a learned rule so future matching notifications
                    // pre-fill these tags. Never blocks advancing.
                    learnRuleIfRequested(draft)
                    // Process the review queue in place: load the next pending item, or return to
                    // the app when none remain.
                    advanceToNext(onDone)
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    /**
     * Discards the captured notification (marks it ignored so it leaves "Para revisar") and then
     * advances the queue: loads the next pending item in place, or returns to the app via [onDone]
     * when none remain. The ignore is best-effort.
     */
    fun ignore(learn: Boolean, onDone: () -> Unit) {
        if (_state.value.isSaving || _state.value.isSwitching) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            // Best-effort: learn an ignore-rule first so future similar notifications are auto-ignored.
            if (learn) learnIgnoreRule()
            notificationRepository.markIgnored(notificationId)
            advanceToNext(onDone)
        }
    }

    /**
     * Creates an IGNORE-action classification rule so the backend auto-ignores future notifications
     * matching the same merchant pattern. Carries only the pattern (no tags/type). Best-effort: a
     * missing pattern or a create failure is swallowed and the ignore still proceeds.
     */
    private suspend fun learnIgnoreRule() {
        val pattern = learnPattern(_state.value.draft) ?: return
        runCatching {
            classificationRuleRepository.create(
                ClassificationRule(
                    id = null,
                    patterns = listOf(pattern),
                    matchType = "CONTAINS",
                    active = true,
                    appliedCount = 0,
                    transactionType = null,
                    paymentMethod = null,
                    cardId = null,
                    tags = emptyList(),
                    action = RuleAction.IGNORE,
                ),
            )
        }
    }

    /** Loads the next still-pending item in place, or returns to the app via [onDone] when none remain. */
    private suspend fun advanceToNext(onDone: () -> Unit) {
        val next = notificationRepository.getPendingReview().getOrNull()
            ?.firstOrNull { it.id != notificationId }
        if (next == null) {
            onDone()
            return
        }
        // Clear the save/ignore flag so the in-place switch isn't blocked by its own guard.
        _state.update { it.copy(isSaving = false) }
        goTo(next.id)
    }

    /**
     * Creates a learned classification rule when the user enabled "Aprender este padrão".
     *
     * The CONTAINS pattern must be a substring of the notification text, or it can never match a
     * future notification — so candidates (merchant first, then parsed merchant, then payment hint)
     * are validated against [NotificationItem.text] and free-text edits to the Descrição that don't
     * appear in the message are skipped. Only tags carrying a context (idCategory) survive
     * serialization, so a rule is created only when at least one such tag exists (income tags have
     * no context, mirroring the card path). Best-effort — failures are swallowed.
     */
    private suspend fun learnRuleIfRequested(draft: WizardDraft) {
        if (!draft.learnRule) return
        val pattern = learnPattern(draft) ?: return
        // Only tags with a context serialize into the rule (ClassificationRuleTagDto needs idCategory).
        val ruleTags = _state.value.allTags.filter { it.id in draft.tagIds && !it.idContext.isNullOrBlank() }
        if (ruleTags.isEmpty()) return
        runCatching {
            classificationRuleRepository.create(
                ClassificationRule(
                    id = null,
                    patterns = listOf(pattern),
                    matchType = "CONTAINS",
                    active = true,
                    appliedCount = 0,
                    transactionType = draft.type,
                    paymentMethod = draft.paymentMethod,
                    cardId = draft.cardId,
                    tags = ruleTags,
                ),
            )
        }
    }

    /**
     * The CONTAINS pattern for a learned rule: the first merchant-ish candidate (edited merchant, then
     * parsed merchant, then payment hint) that is actually a substring of the notification text — a
     * pattern that doesn't appear in the message could never match a future notification.
     */
    private fun learnPattern(draft: WizardDraft): String? {
        val notification = _state.value.notification ?: return null
        return listOfNotNull(
            draft.merchant?.takeIf { it.isNotBlank() },
            notification.parsed.merchantRaw?.takeIf { it.isNotBlank() },
            notification.parsed.paymentHint?.takeIf { it.isNotBlank() },
        ).map { it.trim() }
            .firstOrNull { it.isNotBlank() && notification.text.contains(it, ignoreCase = true) }
    }

    private suspend fun linkSeries(draft: WizardDraft, transactionId: String) {
        if (!draft.isFixo) return
        val existingSeriesId = draft.seriesId
        if (existingSeriesId != null) {
            runCatching {
                seriesRepository.linkTransaction(existingSeriesId, transactionId, includePrevious = false)
            }
            return
        }
        val name = draft.merchant ?: draft.name ?: "Conta fixa"
        val type = draft.type ?: return
        seriesRepository.create(name = name, type = type, recurrenceDay = draft.recurrenceDay)
            .onSuccess { series ->
                if (draft.tagIds.isNotEmpty()) {
                    runCatching { seriesRepository.setTags(series.id, draft.tagIds) }
                }
                runCatching {
                    seriesRepository.linkTransaction(series.id, transactionId, includePrevious = false)
                }
            }
    }

    fun confirmPending() {
        val pendingId = _state.value.pendingTransactionId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            transactionRepository.markPaid(pendingId)
                .onSuccess {
                    runCatching { notificationRepository.markClassified(notificationId, pendingId) }
                    _state.update {
                        it.copy(isSaving = false, isConfirmingPending = false, pendingConfirmed = true)
                    }
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }
}
