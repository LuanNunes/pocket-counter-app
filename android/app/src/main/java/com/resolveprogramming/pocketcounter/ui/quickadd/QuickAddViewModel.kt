package com.resolveprogramming.pocketcounter.ui.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.local.LedgerRefreshSignal
import com.resolveprogramming.pocketcounter.data.local.ManualEntryRelay
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.ClassificationRuleRepository
import com.resolveprogramming.pocketcounter.data.repository.DuplicateTransactionException
import com.resolveprogramming.pocketcounter.data.repository.IntentReadFailure
import com.resolveprogramming.pocketcounter.data.repository.IntentReadResult
import com.resolveprogramming.pocketcounter.data.repository.PaymentMethodPrefsRepository
import com.resolveprogramming.pocketcounter.data.repository.TagInput
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionIntentRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.IntentField
import com.resolveprogramming.pocketcounter.domain.model.MissingField
import com.resolveprogramming.pocketcounter.domain.model.NewTagRequest
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TransactionIntent
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.ValueSource
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.domain.model.isComplete
import com.resolveprogramming.pocketcounter.ui.rules.teachRuleNote
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

private const val SECOND_MILLIS = 1000L
private const val DETAIL_MAX_CHARS = 90

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val intentRepository: TransactionIntentRepository,
    private val transactionRepository: TransactionRepository,
    private val classificationRuleRepository: ClassificationRuleRepository,
    private val tagRepository: TagRepository,
    private val cardRepository: CardRepository,
    private val paymentMethodPrefsRepository: PaymentMethodPrefsRepository,
    private val ledgerRefresh: LedgerRefreshSignal,
    private val manualEntryRelay: ManualEntryRelay,
) : ViewModel() {

    private val _state = MutableStateFlow(QuickAddUiState())
    val state: StateFlow<QuickAddUiState> = _state.asStateFlow()

    private var countdown: Job? = null

    init {
        // Drop a sentence an earlier session handed over and nobody collected.
        manualEntryRelay.consume()
        loadLookups()
        viewModelScope.launch {
            paymentMethodPrefsRepository.enabledMethods.collect { enabled ->
                _state.update { it.copy(enabledMethods = enabled) }
            }
        }
    }

    private fun loadLookups() {
        viewModelScope.launch {
            val cards = cardRepository.getCards()
            val tags = tagRepository.getAllTags()
            val contexts = tagRepository.getAllContexts()
            _state.update {
                it.copy(
                    // A failed fetch keeps whatever was already good rather than blanking the pickers.
                    cards = cards.getOrDefault(it.cards),
                    tags = tags.getOrDefault(it.tags),
                    contexts = contexts.getOrDefault(it.contexts),
                    lookupsFailed = cards.isFailure || tags.isFailure || contexts.isFailure,
                )
            }
        }
    }

    fun retryLookups() {
        if (!_state.value.lookupsFailed) return
        loadLookups()
    }

    fun setText(text: String) {
        _state.update { it.copy(text = text.take(QUICK_ADD_MAX_CHARS)) }
    }

    fun submitSentence() {
        val current = _state.value
        if (!current.canSubmitSentence) return
        val text = current.text.trim()
        // Opening the editor and changing nothing: there is nothing to re-read and nothing to lose.
        if (current.intent != null && text == current.readText) {
            _state.update { it.copy(stage = QuickAddStage.PREVIEW, readFailure = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isReading = true, readFailure = null) }
            when (val result = intentRepository.read(text, LocalDate.now())) {
                is IntentReadResult.Read -> applyIntent(result.intent, text)
                is IntentReadResult.Failed -> applyFailure(result.failure)
            }
        }
    }

    private fun applyIntent(intent: TransactionIntent, text: String) {
        // An AMBIGUOUS card with no candidate asks into a dead end; the preview's Cartão row covers it.
        val askable = intent.missing.filterNot {
            it == MissingField.CARD && intent.cardCandidates.isEmpty()
        }
        val resolved = intent.copy(missing = askable)
        val previous = _state.value
        val read = WizardDraft.fromIntent(resolved).withTagsKnownIn(previous.tags)
        val carried = previous.carriedFields(resolved)
        val draft = previous.restore(carried, onto = read)
        _state.update {
            it.copy(
                isReading = false,
                readFailure = null,
                intent = resolved,
                readText = text,
                draft = draft,
                defined = carried,
                askIndex = 0,
                teachPattern = draft.name.orEmpty(),
                stage = QuickAddStage.PREVIEW.takeIf { askable.isEmpty() } ?: QuickAddStage.ASK,
            )
        }
    }

    /**
     * A re-read is authoritative for what the new sentence actually says; every other field the user
     * defined survives it, badge included.
     */
    private fun QuickAddUiState.carriedFields(read: TransactionIntent): Set<IntentField> =
        defined.filterTo(mutableSetOf()) { read.source[it] != ValueSource.WRITTEN }

    private fun QuickAddUiState.restore(fields: Set<IntentField>, onto: WizardDraft): WizardDraft {
        if (fields.isEmpty()) return onto
        val corrected = draft
        val restored = IntentField.entries
            .filter { it in fields }
            .fold(onto) { acc, field -> acc.restoring(field, corrected) }
        // A carried TYPE can orphan a carried tag, and the write rejects the whole row for it.
        val type = restored.type ?: return restored
        return restored.copy(tagIds = restored.tagIds.filter { tagOf(it)?.kind == type })
    }

    private fun applyFailure(failure: IntentReadFailure) {
        _state.update { it.copy(isReading = false, readFailure = failure) }
        val seconds = (failure as? IntentReadFailure.RateLimited)?.retryAfterSeconds ?: return
        startCountdown(seconds)
    }

    private fun startCountdown(seconds: Int) {
        countdown?.cancel()
        if (seconds <= 0) return
        _state.update { it.copy(retrySeconds = seconds) }
        countdown = viewModelScope.launch {
            (seconds - 1 downTo 0).forEach { left ->
                delay(SECOND_MILLIS)
                _state.update { s -> s.copy(retrySeconds = left.takeIf { it > 0 }) }
            }
        }
    }

    /** Back to the sentence with it intact; every answer is kept, and a re-read carries them over. */
    fun editSentence() {
        _state.update {
            it.copy(stage = QuickAddStage.INPUT, askIndex = 0, expandedRow = null, saveFailed = false)
        }
    }

    fun confirmAsk() {
        _state.update { s ->
            if (!s.canConfirmAsk) return@update s
            s.advanced()
        }
    }

    fun backAsk() {
        _state.update { s ->
            if (s.askIndex == 0) return@update s.copy(stage = QuickAddStage.INPUT)
            s.copy(askIndex = s.askIndex - 1)
        }
    }

    private fun QuickAddUiState.advanced(): QuickAddUiState {
        val next = askIndex + 1
        if (next <= missing.lastIndex) return copy(askIndex = next)
        return copy(
            stage = QuickAddStage.PREVIEW,
            askIndex = next,
            teachPattern = teachPattern.ifBlank { draft.name.orEmpty() },
        )
    }

    /** CARD is the only skippable ask: it records nothing, so the row stays *não informado*. */
    fun skipCard() {
        _state.update { it.advanced() }
    }

    fun setAmount(amount: BigDecimal?) {
        updateDraft(IntentField.AMOUNT) { it.copy(amount = amount) }
    }

    fun setName(name: String) {
        updateDraft(IntentField.NAME) { it.copy(name = name) }
    }

    fun setDate(date: LocalDate) {
        updateDraft(IntentField.DATE) { it.copy(date = date) }
    }

    fun setPaymentStatus(status: PaymentStatus) {
        updateDraft(IntentField.STATUS) { it.copy(statusPayment = status) }
    }

    fun setPaymentMethod(method: PaymentMethod?) {
        updateDraft(IntentField.PAYMENT_METHOD) { it.withPaymentMethod(method) }
    }

    fun selectCard(cardId: String) {
        updateDraft(IntentField.CARD, IntentField.PAYMENT_METHOD) { it.withCard(cardId) }
    }

    /** Answering the CARD ask commits the card and moves on; the option is the confirmation. */
    fun answerCard(cardId: String) {
        selectCard(cardId)
        _state.update { it.advanced() }
    }

    /** One tag at most: a transaction's tags are `[{id}]` behind a unique index. */
    fun toggleTag(tagId: String) {
        updateDraft(IntentField.TAG) { draft ->
            draft.copy(tagIds = emptyList<String>().takeIf { tagId in draft.tagIds } ?: listOf(tagId))
        }
    }

    fun openNewTag(contextId: String? = null) {
        if (!_state.value.canCreateTag) return
        _state.update {
            it.copy(isCreatingTag = true, newTagContextId = contextId, tagFormError = null)
        }
    }

    fun closeNewTag() {
        _state.update {
            it.copy(
                isCreatingTag = false,
                newTagContextId = null,
                tagFormError = null,
                isSavingTag = false,
            )
        }
    }

    /**
     * Its own write, not the wizard's: that one appends to `tagIds`, and a second tag here is
     * rejected by the unique index.
     */
    fun createAndApplyTag(request: NewTagRequest) {
        val current = _state.value
        val type = current.draft.type ?: return
        if (current.isSavingTag || !current.canCreateTag || !request.isComplete(type)) return
        _state.update { it.copy(isSavingTag = true, tagFormError = null) }
        viewModelScope.launch {
            tagRepository.createTag(request.toTagInput(type))
                .onSuccess { tag -> onTagCreated(tag) }
                .onFailure { e -> onTagCreateFailed(e) }
        }
    }

    private fun onTagCreated(tag: Tag) {
        _state.update {
            it.copy(
                tags = it.tags.filterNot { existing -> existing.id == tag.id } + tag,
                draft = it.draft.copy(tagIds = listOf(tag.id)),
                defined = it.defined + IntentField.TAG,
                isCreatingTag = false,
                newTagContextId = null,
                isSavingTag = false,
                tagFormError = null,
            )
        }
    }

    private fun onTagCreateFailed(e: Throwable) {
        val detail = e.message?.trim()?.takeIf { it.isNotEmpty() }
        val message = detail?.let { "Não foi possível criar a tag: ${it.take(DETAIL_MAX_CHARS)}" }
            ?: "Não foi possível criar a tag"
        _state.update { it.copy(isSavingTag = false, tagFormError = message) }
    }

    fun selectType(type: TransactionType) {
        _state.update { s ->
            val typed = s.draft.withType(type)
            // An income cannot carry an expense tag, and the rule lives with the tag's kind.
            val kept = typed.tagIds.filter { id -> s.tagOf(id)?.kind != type.opposite() }
            s.copy(draft = typed.copy(tagIds = kept)).defining(IntentField.TYPE)
        }
    }

    fun answerType(type: TransactionType) {
        selectType(type)
        _state.update { it.advanced() }
    }

    fun toggleRow(field: IntentField) {
        _state.update { it.copy(expandedRow = field.takeIf { _ -> it.expandedRow != field }) }
    }

    fun setTeachEnabled(enabled: Boolean) {
        _state.update { it.copy(teachEnabled = enabled) }
    }

    fun setTeachPattern(pattern: String) {
        _state.update { it.copy(teachPattern = pattern) }
    }

    fun save() = write(allowDuplicate = false)

    /** The user looked at the conflict and said file it anyway. */
    fun saveAnyway() = write(allowDuplicate = true)

    fun dismissDuplicate() {
        _state.update { it.copy(duplicate = null) }
    }

    private fun write(allowDuplicate: Boolean) {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, saveFailed = false, duplicate = null) }
            transactionRepository.save(current.draft, allowDuplicate = allowDuplicate)
                .onSuccess { id -> onSaved(id, current) }
                .onFailure { e -> onSaveFailed(e) }
        }
    }

    private suspend fun onSaved(transactionId: String, saved: QuickAddUiState) {
        val note = teachRule(saved)
        _state.update {
            it.copy(
                isSaving = false,
                stage = QuickAddStage.SAVED,
                savedTransactionId = transactionId,
                teachNote = note,
            )
        }
        ledgerRefresh.signal()
    }

    private fun onSaveFailed(e: Throwable) {
        val conflict = e as? DuplicateTransactionException
        _state.update {
            it.copy(
                isSaving = false,
                duplicate = conflict?.let { c -> DuplicateConflict(c.existingTransactionId) },
                saveFailed = conflict == null,
            )
        }
    }

    /**
     * Best-effort: the transaction is the source of truth, so a failed rule write is a note, never an
     * error. The pattern is the user-confirmed field — [com.resolveprogramming.pocketcounter.domain.rules.TeachPatternResolver]
     * would filter it against a notification text that does not exist here.
     */
    private suspend fun teachRule(saved: QuickAddUiState): String? {
        if (!saved.teachEnabled || !saved.canTeach) return null
        val tag = saved.teachableTag ?: return null
        val rule = ClassificationRule.suggest(saved.teachPattern.trim(), tag.id)
        if (rule.writeBlocker(tag.kind) != null) return null
        return classificationRuleRepository.create(rule).getOrNull()?.let(::teachRuleNote)
    }

    /** Hands the sentence to the manual form, the one place quick-add can degrade into. */
    fun escapeToManualEntry() {
        manualEntryRelay.seed(_state.value.text.trim())
    }

    /** Clears every trace of the sentence; the sheet is never reopened holding the last one. */
    fun reset() {
        countdown?.cancel()
        val failed = _state.value.lookupsFailed
        _state.update {
            QuickAddUiState(
                cards = it.cards,
                tags = it.tags,
                contexts = it.contexts,
                enabledMethods = it.enabledMethods,
                lookupsFailed = failed,
            )
        }
        // A sheet reopened after a failed fetch must try again; a successful one keeps the cache.
        if (failed) loadLookups()
    }

    private fun updateDraft(
        vararg fields: IntentField,
        change: (WizardDraft) -> WizardDraft,
    ) {
        _state.update { it.copy(draft = change(it.draft)).defining(*fields) }
    }

    /**
     * A field in `missing` carries no server provenance, so without this an answered value would
     * badge *assumido*. Fields whose value the change dropped leave [QuickAddUiState.defined] with it.
     */
    private fun QuickAddUiState.defining(vararg fields: IntentField): QuickAddUiState =
        copy(defined = (defined + fields).filterTo(mutableSetOf()) { hasValue(draft, it) })
}

private fun TransactionType.opposite(): TransactionType {
    if (this == TransactionType.INCOME) return TransactionType.EXPENSE
    return TransactionType.INCOME
}

private fun NewTagRequest.toTagInput(type: TransactionType): TagInput {
    if (type == TransactionType.INCOME) {
        return TagInput(name.trim(), kind = type, idContext = null, color = color)
    }
    return TagInput(name.trim(), kind = type, idContext = contextId)
}

/** One field's user-defined value, moved onto a fresh reading. */
private fun WizardDraft.restoring(field: IntentField, from: WizardDraft): WizardDraft = when (field) {
    IntentField.TYPE -> from.type?.let { withType(it) } ?: this
    IntentField.AMOUNT -> copy(amount = from.amount)
    IntentField.DATE -> copy(date = from.date)
    IntentField.NAME -> copy(name = from.name, merchant = from.merchant)
    IntentField.PAYMENT_METHOD -> withPaymentMethod(from.paymentMethod)
    IntentField.CARD -> from.cardId?.let { withCard(it) } ?: this
    IntentField.TAG -> copy(tagIds = from.tagIds)
    IntentField.STATUS -> copy(statusPayment = from.statusPayment)
}
