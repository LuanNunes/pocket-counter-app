package com.resolveprogramming.pocketcounter.ui.wizard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.repository.CardLast4Repository
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.ClassificationRuleRepository
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.PaymentMethodDictionaryRepository
import com.resolveprogramming.pocketcounter.data.repository.PaymentMethodPrefsRepository
import com.resolveprogramming.pocketcounter.data.repository.SeriesRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.RuleAction
import com.resolveprogramming.pocketcounter.domain.model.Series
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethodPreferences
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.Token
import com.resolveprogramming.pocketcounter.domain.model.TokenRole
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.domain.notification.BrNotificationParser
import com.resolveprogramming.pocketcounter.domain.notification.CardLast4Matcher
import com.resolveprogramming.pocketcounter.domain.notification.NotificationTokenizer
import com.resolveprogramming.pocketcounter.domain.notification.PaymentMethodResolver
import com.resolveprogramming.pocketcounter.domain.rules.RuleTeachPlanner
import com.resolveprogramming.pocketcounter.domain.rules.TeachPatternSanitizer
import com.resolveprogramming.pocketcounter.domain.rules.TeachPlan
import com.resolveprogramming.pocketcounter.domain.usecase.ConfirmClassifiedNotificationUseCase
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
    /**
     * Set when the notification carried a "final NNNN" hint that could not be matched to a
     * known card in the local last-4 map. The UI should prompt the user to assign it to an
     * existing card (via [WizardViewModel.assignLast4ToCard]) or dismiss the prompt.
     */
    val unknownCardLast4: String? = null,
    /** User-configured enabled payment methods; used to filter the method-selection UI. */
    val enabledMethods: Set<PaymentMethod> = PaymentMethodPreferences.default,
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
    private val seriesRepository: SeriesRepository,
    private val classificationRuleRepository: ClassificationRuleRepository,
    private val confirmClassifiedNotification: ConfirmClassifiedNotificationUseCase,
    private val cardLast4Repository: CardLast4Repository,
    private val paymentMethodPrefsRepository: PaymentMethodPrefsRepository,
    private val paymentMethodDictionaryRepository: PaymentMethodDictionaryRepository,
) : ViewModel() {

    private var notificationId: String = savedStateHandle["notificationId"] ?: ""
    private val _state = MutableStateFlow(WizardUiState())
    val state: StateFlow<WizardUiState> = _state.asStateFlow()

    init {
        loadNotification(initial = true)
        viewModelScope.launch {
            paymentMethodPrefsRepository.enabledMethods.collect { enabled ->
                _state.update { it.copy(enabledMethods = enabled) }
            }
        }
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
            val last4MapDeferred = async { cardLast4Repository.getMap() }
            val dictDeferred = async { paymentMethodDictionaryRepository.getMap() }

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
                    enabledMethods = _state.value.enabledMethods,
                )
                return@launch
            }

            val notification = classified?.notification ?: base
            val baseDraft = WizardDraft.fromNotification(notification)
            val degradeError = classifyResult.exceptionOrNull()?.message

            val tokens = notification.tokens.ifEmpty {
                NotificationTokenizer.tokenize(notification.text, notification.parsed)
            }

            // Prefill payment method + card from the local last-4 map when the notification
            // carries a "final NNNN" hint and the draft was not already resolved by a rule.
            val last4Map = last4MapDeferred.await()
            val learnedMap = dictDeferred.await()
            val (last4Draft, unknownLast4) = prefillFromLast4(baseDraft, notification, last4Map)
            // Resolve the payment method: learned dictionary first, then the built-in word list.
            val draft = last4Draft.withResolvedPaymentMethod(notification, learnedMap)

            // Switching to a different item resets to that item's fresh draft/step/tokens; only the
            // on-screen transition kept the previous item visible until this point.
            // enabledMethods is a cross-concern that survives the item reset.
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
                unknownCardLast4 = unknownLast4,
                enabledMethods = _state.value.enabledMethods,
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
            // Shared save core (create the transaction + best-effort markClassified). The transaction
            // is the source of truth and is never rolled back; the wizard-only side-effects below are
            // likewise best-effort and can't block advancing.
            confirmClassifiedNotification(notificationId, draft, pendingTransactionId = null)
                .onSuccess { transactionId ->
                    // Recurring-series link: carry-forward later seeds each instance's amount from the
                    // source month — the backend has no series defaultAmount (handoff §3.3 divergence).
                    linkSeries(draft, transactionId)
                    // Persist a learned rule so future matching notifications pre-fill these tags.
                    learnRuleIfRequested(draft)
                    // Persist a learned payment-method word if the user marked one.
                    learnPaymentMethodIfMarked(draft)
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
     *
     * Unlike the SUGGEST path this accepts a bare gateway marker ("Ifd*"): silencing a whole acquirer
     * is a legitimate thing to ask for, and an IGNORE rule has no tags to overwrite and is never a
     * teach target, so its breadth can't corrupt anyone's classification.
     */
    private suspend fun learnIgnoreRule() {
        val notification = _state.value.notification ?: return
        val pattern = learnPattern(_state.value.draft, notification, allowGatewayMarker = true) ?: return
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
     * Merges or creates a learned classification rule when the user enabled "Aprender este padrão".
     *
     * [RuleTeachPlanner] targets the oldest active SUGGEST rule holding a pattern that both matches
     * this notification's text AND names the same merchant as the taught pattern, so a correction
     * edits the rule the user just saw go wrong instead of minting a near-duplicate beside it — or
     * hijacking the payment gateway's rule when the two only share a "Ifd*"-style prefix. The rule the
     * backend actually applied may therefore be left alone; see [RuleTeachPlanner.plan] for why that
     * is preferred over rewriting someone else's rule. The taught payment method and card ride
     * along: without a "final NNNN" hint (Uber, PIX, débito) classify has nothing to derive them from,
     * and a merchant's method is a property of the merchant. Best-effort — failures are swallowed.
     */
    private suspend fun learnRuleIfRequested(draft: WizardDraft) {
        if (!draft.learnRule) return
        val notification = _state.value.notification ?: return
        val pattern = learnPattern(draft, notification) ?: return
        // Only tags with a context serialize into the rule (ClassificationRuleTagDto needs idCategory).
        val ruleTags = _state.value.allTags.filter { it.id in draft.tagIds && !it.idContext.isNullOrBlank() }
        if (ruleTags.isEmpty()) return
        // A failed load must NOT read as "no rules exist": that would create a duplicate of the very
        // rule we couldn't see, on every 401/timeout. Skipping one teach is the cheaper failure.
        val existing = classificationRuleRepository.getAll().getOrElse { return }
        val plan = RuleTeachPlanner.plan(
            existing = existing,
            notificationText = notification.text,
            pattern = pattern,
            type = draft.type,
            paymentMethod = draft.paymentMethod,
            cardId = draft.cardId,
            tags = ruleTags,
        )
        runCatching {
            when (plan) {
                is TeachPlan.Update -> classificationRuleRepository.update(plan.rule)
                is TeachPlan.Create -> classificationRuleRepository.create(plan.rule)
                is TeachPlan.NoOp -> Unit
            }
        }
    }

    /**
     * Persists a learned payment-method word when the user explicitly marked a PAYMENT span in the
     * token editor and a concrete [WizardDraft.paymentMethod] is set. Best-effort — failures are
     * swallowed.
     *
     * Guards (all must hold; if any fails the call is a no-op):
     *  1. A [TokenRole.PAYMENT] span exists and is exactly ONE token (v1: skip multi-token spans,
     *     which is also what blocks the tokenizer's auto-assigned "final NNNN" two-token span).
     *  2. [WizardDraft.paymentMethod] is non-null.
     *  3. The span is not a card-hint word ("cartão"/"conta"/"final") nor a pure-digit fragment
     *     (e.g. a lone "3685"), so a manually-marked card token can't poison the dictionary.
     *  4. [BrNotificationParser.parsePaymentMethod] does NOT already return the same method for the
     *     span text (don't store words the built-in already handles).
     */
    private suspend fun learnPaymentMethodIfMarked(draft: WizardDraft) {
        val method = draft.paymentMethod ?: return
        val paymentTokens = _state.value.tokens.filter { it.role == TokenRole.PAYMENT }
        if (paymentTokens.size != 1) return
        val span = paymentTokens.first().text
        val normalizedSpan = PaymentMethodResolver.normalizeKey(span)
        if (normalizedSpan in CARD_HINT_WORDS) return
        if (normalizedSpan.isNotEmpty() && normalizedSpan.all { it.isDigit() }) return
        if (BrNotificationParser.parsePaymentMethod(span) == method) return
        runCatching { paymentMethodDictionaryRepository.learn(span, method) }
    }

    /**
     * The CONTAINS pattern for a learned rule, SUGGEST or IGNORE alike.
     *
     * Only the candidate ORDER is decided here, because it is policy about draft fields: the merchant
     * the user just edited beats the parsed one, which beats the payment hint. [TeachPatternSanitizer]
     * owns the rest — trimming trailing punctuation, rejecting bare payment-gateway prefixes ("Ifd*",
     * "Mp *") that would match every merchant behind the gateway unless [allowGatewayMarker], and
     * requiring the survivor to occur in the notification text (case-insensitively, like the rest of
     * `RulePatterns`) since a pattern absent from the message could never match a future one. The
     * candidate is stored verbatim, not as the slice of text it matched.
     *
     * A rejected candidate falls through to the next one; null means no candidate qualified and the
     * caller skips the teach entirely rather than storing a dangerous pattern.
     */
    private fun learnPattern(
        draft: WizardDraft,
        notification: NotificationItem,
        allowGatewayMarker: Boolean = false,
    ): String? =
        TeachPatternSanitizer.choose(
            candidates = listOf(
                draft.merchant,
                notification.parsed.merchantRaw,
                notification.parsed.paymentHint,
            ),
            notificationText = notification.text,
            allowGatewayMarker = allowGatewayMarker,
        )

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
            confirmClassifiedNotification(notificationId, _state.value.draft, pendingTransactionId = pendingId)
                .onSuccess {
                    _state.update {
                        it.copy(isSaving = false, isConfirmingPending = false, pendingConfirmed = true)
                    }
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    /**
     * Associates [cardId] with the current [WizardUiState.unknownCardLast4] in the local
     * last-4 store, applies CREDIT + [cardId] prefill to the draft, and clears the prompt.
     *
     * No-op when there is no pending unknown last-4.
     */
    fun assignLast4ToCard(cardId: String) {
        val last4 = _state.value.unknownCardLast4 ?: return
        viewModelScope.launch {
            cardLast4Repository.associate(cardId, last4)
            _state.update { state ->
                state.copy(draft = state.draft.withCard(cardId), unknownCardLast4 = null)
            }
        }
    }

    /**
     * Applies CREDIT + [cardId] to this draft, keeping the card only when the credit guard holds
     * (an income draft must not carry a card id). Mirrors [WizardDraft.fromNotification].
     */
    private fun WizardDraft.withCard(cardId: String): WizardDraft {
        val withMethod = withPaymentMethod(PaymentMethod.CREDIT)
        if (withMethod.paymentMethod != PaymentMethod.CREDIT) return withMethod
        return withMethod.copy(cardId = cardId)
    }

    /**
     * Resolves the payment method from the learned dictionary and, as a fallback, the built-in
     * word list ([BrNotificationParser.parsePaymentMethod]). No-op when [paymentMethod] is already
     * set. Routed through [WizardDraft.withPaymentMethod] so the credit guard still holds.
     */
    private fun WizardDraft.withResolvedPaymentMethod(
        notification: NotificationItem,
        learnedMap: Map<String, PaymentMethod>,
    ): WizardDraft {
        if (paymentMethod != null) return this
        val method = PaymentMethodResolver.resolve(notification.text, learnedMap) ?: return this
        return withPaymentMethod(method)
    }

    /**
     * Dismisses the unknown-card prompt without associating the last-4 or changing the draft.
     */
    fun dismissUnknownCard() {
        _state.update { it.copy(unknownCardLast4 = null) }
    }

    /**
     * Applies the last-4 prefill to [draft] based on [notification]'s payment hint and the
     * local [last4Map].
     *
     * Returns a pair of (possibly-updated draft, unknownLast4):
     * - If the draft already has CREDIT + cardId (set by a classification rule), it is not
     *   overridden and unknownLast4 is null.
     * - If the hint resolves to a known card, the draft is prefilled with CREDIT + that cardId
     *   and unknownLast4 is null.
     * - If the hint has a 4-digit suffix but it is not in the map, the draft is unchanged and
     *   unknownLast4 is the 4-digit string.
     * - If the hint carries no 4-digit suffix ("cartão", "conta", null), both outputs are
     *   unchanged / null.
     */
    private fun prefillFromLast4(
        draft: WizardDraft,
        notification: NotificationItem,
        last4Map: Map<String, String>,
    ): Pair<WizardDraft, String?> {
        // Classification rule already resolved a specific card — respect it.
        if (draft.paymentMethod == PaymentMethod.CREDIT && draft.cardId != null) {
            return Pair(draft, null)
        }
        val last4 = CardLast4Matcher.extractLast4(notification.parsed.paymentHint)
            ?: return Pair(draft, null)
        val matchedId = CardLast4Matcher.matchCardId(last4, last4Map)
            ?: return Pair(draft, last4)
        return Pair(draft.withCard(matchedId), null)
    }

    private companion object {
        /** Normalized card-hint words that must never be learned as a payment-method token. */
        private val CARD_HINT_WORDS = setOf("cartão", "cartao", "conta", "final")
    }
}
