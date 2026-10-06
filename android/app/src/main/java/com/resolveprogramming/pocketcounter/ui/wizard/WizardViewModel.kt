package com.resolveprogramming.pocketcounter.ui.wizard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.local.AppMessageRelay
import com.resolveprogramming.pocketcounter.data.repository.BlockedSourceRepository
import com.resolveprogramming.pocketcounter.data.repository.CardLast4Repository
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.ClassificationRuleRepository
import com.resolveprogramming.pocketcounter.data.repository.IssuerCardRepository
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.PaymentMethodDictionaryRepository
import com.resolveprogramming.pocketcounter.data.repository.PaymentMethodPrefsRepository
import com.resolveprogramming.pocketcounter.data.repository.ProductiveSourceRepository
import com.resolveprogramming.pocketcounter.data.repository.RuleWriteOutcome
import com.resolveprogramming.pocketcounter.data.repository.SeriesRepository
import com.resolveprogramming.pocketcounter.data.repository.TagInput
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.IgnoreScope
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
import com.resolveprogramming.pocketcounter.domain.notification.NotificationEvidence
import com.resolveprogramming.pocketcounter.domain.notification.NotificationTokenizer
import com.resolveprogramming.pocketcounter.domain.notification.PaymentMethodResolver
import com.resolveprogramming.pocketcounter.domain.notification.SourceBlocklist
import com.resolveprogramming.pocketcounter.domain.notification.resolveDraftFromNotification
import com.resolveprogramming.pocketcounter.domain.rules.IgnoreOptions
import com.resolveprogramming.pocketcounter.domain.rules.TeachPatternResolver
import com.resolveprogramming.pocketcounter.domain.usecase.ConfirmClassifiedNotificationUseCase
import com.resolveprogramming.pocketcounter.ui.contextos.TagFormMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
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
    /** Initial-load failure. Rendered as a full-screen recoverable error, so it is load-only. */
    val error: String? = null,
    /** Transient feedback for a failure that happens with the wizard on screen (save/confirm). */
    val toastMessage: String? = null,
    /**
     * Set when the notification carried a "final NNNN" hint that could not be matched to a
     * known card in the local last-4 map. The UI should prompt the user to assign it to an
     * existing card (via [WizardViewModel.assignLast4ToCard]) or dismiss the prompt.
     */
    val unknownCardLast4: String? = null,
    /** User-configured enabled payment methods; used to filter the method-selection UI. */
    val enabledMethods: Set<PaymentMethod> = PaymentMethodPreferences.default,
    /**
     * Whether the load resolved a payment method. Stored, not derived: derived from the live draft,
     * the payment step's support line would vanish the instant the user picked a method.
     */
    val paymentPrefilled: Boolean = false,
    /** Confirmed transactions this notification's source app has already produced on this device. */
    val sourceTransactionCount: Int = 0,
    val tagForm: TagFormMode? = null,
    val isSavingTag: Boolean = false,
    /** Create-tag failure, shown inside the sheet: a toast would be drawn behind its own scrim. */
    val tagFormError: String? = null,
    /** Seeds the create-tag sheet's name field when the step opened it from a search query. */
    val tagFormInitialName: String = "",
) {
    /** Kind the tag step browses and a newly created tag takes; a notification can reach it with no type. */
    val effectiveTagType: TransactionType
        get() = draft.type ?: TransactionType.EXPENSE

    /**
     * Creating a tag under a merely guessed kind is irreversible and would strand it out of the
     * other kind's universe; and the expense form cannot be saved with no context to pick.
     */
    val canCreateTag: Boolean
        get() {
            draft.type ?: return false
            if (effectiveTagType == TransactionType.EXPENSE) return contexts.isNotEmpty()
            return true
        }

    /** The teach toggle is disabled, not failing, when no selected tag could carry a rule. */
    val canTeachRule: Boolean
        get() = draft.teachableTag(allTags) != null

    val selectionRange: IntRange?
        get() = if (selectionAnchor != null && selectionFocus != null) {
            minOf(selectionAnchor, selectionFocus)..maxOf(selectionAnchor, selectionFocus)
        } else {
            null
        }

    /**
     * Derived, not stored, so a mid-wizard merchant edit keeps the "Ignorar" dialog's promised
     * pattern and the persisted rule pattern identical.
     */
    private val ignorePattern: String?
        get() = notification?.let { TeachPatternResolver.resolve(draft, it, forIgnoreRule = true) }

    val ignoreOption: IgnoreScope?
        get() = notification?.let { IgnoreOptions.resolve(ignorePattern, it.app, it.channel) }

    val ignoreDefaultLearn: Boolean
        get() = ignoreOption is IgnoreScope.Pattern
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
    private val issuerCardRepository: IssuerCardRepository,
    private val paymentMethodPrefsRepository: PaymentMethodPrefsRepository,
    private val paymentMethodDictionaryRepository: PaymentMethodDictionaryRepository,
    private val blockedSourceRepository: BlockedSourceRepository,
    private val productiveSourceRepository: ProductiveSourceRepository,
    private val appMessageRelay: AppMessageRelay,
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
            val dictDeferred = async {
                runCatching { paymentMethodDictionaryRepository.getMap() }.getOrDefault(emptyMap())
            }
            val issuerDeferred = async {
                runCatching { issuerCardRepository.getMap() }.getOrDefault(emptyMap())
            }
            // Read through the repository, not off the state snapshot: the init collector may not
            // have emitted yet, and a user down to one enabled method would lose the prefill.
            val enabledDeferred = async {
                runCatching { paymentMethodPrefsRepository.enabledMethods.first() }
                    .getOrDefault(PaymentMethodPreferences.default)
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
            // Only startable once the source app is known; still overlaps the classify round-trip.
            val productiveDeferred = async {
                runCatching { productiveSourceRepository.countFor(base.app) }.getOrDefault(0)
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
                    enabledMethods = enabledDeferred.await(),
                    toastMessage = _state.value.toastMessage,
                )
                return@launch
            }

            val notification = classified?.notification ?: base
            val degradeToast = classifyResult.exceptionOrNull()?.let(::classifyFailureMessage)

            val tokens = notification.tokens.ifEmpty {
                NotificationTokenizer.tokenize(notification.text, notification.parsed)
            }

            val resolved = resolveDraftFromNotification(
                notification = notification,
                evidence = NotificationEvidence(
                    last4Map = last4MapDeferred.await(),
                    cards = cards,
                    learnedIssuers = issuerDeferred.await(),
                    paymentMethodDictionary = dictDeferred.await(),
                    enabledMethods = enabledDeferred.await(),
                ),
            )

            // Switching to a different item resets to that item's fresh draft/step/tokens; only the
            // on-screen transition kept the previous item visible until this point. enabledMethods
            // comes from the same read the prefill used, so state and prefill cannot disagree.
            _state.value = WizardUiState(
                notification = notification,
                draft = resolved.draft,
                step = resolveStartStep(notification),
                queue = queue,
                cards = cards,
                allTags = tags,
                contexts = contexts,
                availableSeries = series,
                tokens = tokens,
                isLoading = false,
                unknownCardLast4 = resolved.unknownLast4,
                enabledMethods = enabledDeferred.await(),
                paymentPrefilled = resolved.draft.paymentMethod != null,
                toastMessage = degradeToast ?: _state.value.toastMessage,
                sourceTransactionCount = productiveDeferred.await(),
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

    fun openCreateTag(idContext: String?, name: String = "") {
        if (!_state.value.canCreateTag) return
        _state.update {
            it.copy(
                tagForm = newTagForm(it.effectiveTagType, idContext),
                tagFormError = null,
                tagFormInitialName = name,
            )
        }
    }

    private fun newTagForm(kind: TransactionType, idContext: String?): TagFormMode {
        if (kind == TransactionType.INCOME) return TagFormMode.AddIncome
        return TagFormMode.Add(idContext.orEmpty())
    }

    fun closeCreateTag() {
        _state.update { it.copy(tagForm = null, tagFormError = null, isSavingTag = false) }
    }

    fun clearTagFormError() {
        _state.update { it.copy(tagFormError = null) }
    }

    fun saveNewTag(input: TagInput) {
        if (_state.value.isSavingTag) return
        _state.update { it.copy(isSavingTag = true, tagFormError = null) }
        viewModelScope.launch {
            tagRepository.createTag(input)
                .onSuccess { tag ->
                    _state.update {
                        it.copy(
                            allTags = it.allTags.filterNot { existing -> existing.id == tag.id } + tag,
                            draft = it.draft.withTagSelected(tag.id),
                            tagForm = null,
                            isSavingTag = false,
                            tagFormError = null,
                            toastMessage = "Tag \"${tag.name}\" criada",
                        )
                    }
                }
                .onFailure { e ->
                    val message = tagCreateFailureMessage(e)
                    _state.update {
                        // Dismissed mid-flight: no sheet means no scrim, so the toast is visible.
                        it.tagForm ?: return@update it.copy(isSavingTag = false, toastMessage = message)
                        it.copy(isSavingTag = false, tagFormError = message)
                    }
                }
        }
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
                    recordProductiveSource()
                    // Process the review queue in place: load the next pending item, or return to
                    // the app when none remain.
                    advanceToNext(onDone)
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, toastMessage = failureMessage(e)) }
                }
        }
    }

    /**
     * Feedback for a failed save/confirm. [WizardUiState.error] can't carry it: the screen only
     * renders that when the notification failed to load, so a failed save used to flip the CTA back
     * from "Salvando..." and say nothing at all — indistinguishable from a dead button.
     *
     * The cause rides along ("HTTP 400 Bad Request", a connection failure) because "tente novamente"
     * alone is unactionable when the request is the thing that is broken.
     */
    private fun failureMessage(e: Throwable): String {
        val detail = e.message?.trim()?.takeIf { it.isNotEmpty() } ?: return "Não foi possível salvar"
        return "Não foi possível salvar: ${detail.take(DETAIL_MAX_CHARS)}"
    }

    private fun tagCreateFailureMessage(e: Throwable): String {
        val detail = e.message?.trim()?.takeIf { it.isNotEmpty() } ?: return "Não foi possível criar a tag"
        return "Não foi possível criar a tag: ${detail.take(DETAIL_MAX_CHARS)}"
    }

    /**
     * Feedback for a /classify that failed: the wizard falls back to the unclassified notification,
     * so the draft opens with no suggested tags, method or card. Says that outright — the user has
     * to fill those in, and a bare "falhou" would leave them trusting an empty draft.
     */
    private fun classifyFailureMessage(e: Throwable): String {
        val suffix = "Confira tipo, pagamento e tags."
        val detail = e.message?.trim()?.takeIf { it.isNotEmpty() }
            ?: return "Classificação indisponível. $suffix"
        return "Classificação indisponível: ${detail.take(DETAIL_MAX_CHARS)}. $suffix"
    }

    fun consumeToast() = _state.update { it.copy(toastMessage = null) }

    /**
     * Discards the captured notification (marks it ignored so it leaves "Para revisar") according to
     * [scope], then advances the queue: loads the next pending item in place, or returns to the app
     * via [onDone] when none remain.
     */
    fun ignore(scope: IgnoreScope, onDone: () -> Unit) {
        if (_state.value.isSaving || _state.value.isSwitching) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            when (scope) {
                IgnoreScope.ThisOnly -> {
                    notificationRepository.markIgnored(notificationId)
                    advanceToNext(onDone)
                }
                is IgnoreScope.Pattern -> {
                    val ruleFailure = learnIgnorePatternRule(scope.pattern)
                    notificationRepository.markIgnored(notificationId)
                    advanceToNext(onDone, farewell = ruleFailure)
                }
                is IgnoreScope.Source -> ignoreSource(scope.app, onDone)
            }
        }
    }

    /**
     * Creates an IGNORE-action classification rule carrying [pattern] verbatim — the pattern the
     * dialog named is what gets stored, not a re-derived one. Carries no tag: broad patterns the
     * SUGGEST path refuses are accepted here, since a rule with no tag can't mis-tag anything.
     * A Duplicate means the rule is already there, which is what the user asked for.
     */
    private suspend fun learnIgnorePatternRule(pattern: String): String? {
        val created = classificationRuleRepository.create(ClassificationRule.ignore(pattern))
        return when (created.getOrNull()) {
            RuleWriteOutcome.Saved, RuleWriteOutcome.Duplicate -> null
            is RuleWriteOutcome.Rejected, null -> "Notificação ignorada, mas não foi possível salvar a regra."
        }
    }

    /**
     * Blocks [app] entirely, ignores the current item, and best-effort bulk-ignores every other
     * currently pending item from the same normalized source — so blocking "Google" clears every
     * stray push already sitting in the queue, not just the one on screen.
     */
    private suspend fun ignoreSource(app: String, onDone: () -> Unit) {
        // A failed local write must not take the ignore down with it, but it can't be silent either:
        // the source keeps capturing, and the user was just promised it wouldn't.
        val blocked = runCatching { blockedSourceRepository.block(app, Instant.now()) }.isSuccess
        notificationRepository.markIgnored(notificationId)
        // A label that normalizes to blank is unblockable, so it must not sweep the queue either:
        // a null key would otherwise match every other blank-labelled item and discard them too.
        val key = SourceBlocklist.keyOf(app) ?: run {
            advanceToNext(onDone)
            return
        }
        val others = notificationRepository.getPendingReview().getOrDefault(emptyList())
            .filter { it.id != notificationId && SourceBlocklist.keyOf(it.app) == key }
        var ignoredCount = 0
        others.forEach { item ->
            notificationRepository.markIgnored(item.id).onSuccess { ignoredCount++ }
        }
        val farewell = sourceBlockToast(app, ignoredCount)
            .takeIf { blocked }
            ?: "Notificação ignorada, mas não foi possível bloquear $app."
        advanceToNext(onDone, exclude = others.map { it.id }.toSet(), farewell = farewell)
    }

    private fun sourceBlockToast(app: String, otherIgnoredCount: Int): String {
        val base = "Notificações do $app não serão mais capturadas."
        if (otherIgnoredCount == 0) return base
        if (otherIgnoredCount == 1) return "$base 1 pendente foi descartada."
        return "$base $otherIgnoredCount pendentes foram descartadas."
    }

    /**
     * [exclude] guards against a [NotificationRepository.getPendingReview] response that has not yet
     * caught up with ids this call just bulk-ignored. [farewell] goes to [AppMessageRelay] when the
     * queue empties, because this screen is popped right after, taking its toast host with it.
     */
    private suspend fun advanceToNext(
        onDone: () -> Unit,
        exclude: Set<String> = emptySet(),
        farewell: String? = null,
    ) {
        val next = notificationRepository.getPendingReview().getOrNull()
            ?.firstOrNull { it.id != notificationId && it.id !in exclude }
        if (next == null) {
            farewell?.let(appMessageRelay::send)
            onDone()
            return
        }
        // Clear the save/ignore flag so the in-place switch isn't blocked by its own guard.
        _state.update { it.copy(isSaving = false, toastMessage = farewell ?: it.toastMessage) }
        goTo(next.id)
    }

    /**
     * Teaches a SUGGEST rule when the user enabled "Aprender este padrão". A rule carries exactly one
     * tag, so the first tag the user picked — [WizardDraft.tagIds] is append-ordered — is the one
     * taught; the others apply to this transaction only. A DUPLICATE means the rule is already there.
     * Best-effort — failures are swallowed.
     */
    private suspend fun learnRuleIfRequested(draft: WizardDraft) {
        if (!draft.learnRule) return
        val notification = _state.value.notification ?: return
        val pattern = TeachPatternResolver.resolve(draft, notification, forIgnoreRule = false) ?: return
        val tag = draft.teachableTag(_state.value.allTags) ?: return
        val rule = ClassificationRule.suggest(pattern, tag.id)
        if (rule.writeBlocker(tag.kind) != null) return
        classificationRuleRepository.create(rule)
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
        if (normalizedSpan in TeachPatternResolver.CARD_HINT_WORDS) return
        if (normalizedSpan.isNotEmpty() && normalizedSpan.all { it.isDigit() }) return
        if (BrNotificationParser.parsePaymentMethod(span) == method) return
        runCatching { paymentMethodDictionaryRepository.learn(span, method) }
    }

    /**
     * Counts this source as productive, so the "Ignorar" dialog can warn before silencing an app
     * that already pays off. Best-effort — a device-local counter never fails a confirm.
     */
    private suspend fun recordProductiveSource() {
        val app = _state.value.notification?.app ?: return
        runCatching { productiveSourceRepository.record(app) }
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
            confirmClassifiedNotification(notificationId, _state.value.draft, pendingTransactionId = pendingId)
                .onSuccess {
                    recordProductiveSource()
                    _state.update {
                        it.copy(isSaving = false, isConfirmingPending = false, pendingConfirmed = true)
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, toastMessage = failureMessage(e)) }
                }
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
     * Dismisses the unknown-card prompt without associating the last-4 or changing the draft.
     */
    fun dismissUnknownCard() {
        _state.update { it.copy(unknownCardLast4 = null) }
    }

    private companion object {
        /** Keeps a long cause (a stack-trace-ish message) from overflowing the toast pill. */
        private const val DETAIL_MAX_CHARS = 90
    }
}
