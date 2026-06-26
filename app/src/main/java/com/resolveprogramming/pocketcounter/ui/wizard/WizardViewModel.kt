package com.resolveprogramming.pocketcounter.ui.wizard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.SeriesRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
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
    val cards: List<CreditCard> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val contexts: List<TagContext> = emptyList(),
    val tagSearchQuery: String = "",
    val tokens: List<Token> = emptyList(),
    val selectedTokenIndex: Int? = null,
    val availableSeries: List<Series> = emptyList(),
    val pendingTransactionId: String? = null,
    val isConfirmingPending: Boolean = false,
    val isSaving: Boolean = false,
    val isSuccess: Boolean = false,
    val pendingConfirmed: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class WizardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val notificationRepository: NotificationRepository,
    private val cardRepository: CardRepository,
    private val tagRepository: TagRepository,
    private val transactionRepository: TransactionRepository,
    private val seriesRepository: SeriesRepository,
) : ViewModel() {

    private val notificationId: String = savedStateHandle["notificationId"] ?: ""
    private val _state = MutableStateFlow(WizardUiState())
    val state: StateFlow<WizardUiState> = _state.asStateFlow()

    init {
        loadNotification()
    }

    private fun loadNotification() {
        viewModelScope.launch {
            val base = notificationRepository.getById(notificationId).getOrNull()
            if (base == null) {
                // Stale/deleted/already-classified id: surface an error instead of an endless
                // spinner (the screen shows a recoverable failure state with a way out).
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Não foi possível abrir esta notificação. " +
                            "Ela pode já ter sido classificada ou removida.",
                    )
                }
                return@launch
            }
            val cards = cardRepository.getCards().getOrDefault(emptyList())
            val tags = tagRepository.getAllTags().getOrDefault(emptyList())
            val contexts = tagRepository.getAllContexts().getOrDefault(emptyList())
            val series = seriesRepository.getAll().getOrDefault(emptyList())

            val classifyResult = notificationRepository.classify(notificationId, base)
            val classified = classifyResult.getOrNull()

            if (classified?.pendingTransactionId != null) {
                _state.value = WizardUiState(
                    notification = classified.notification,
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

            _state.value = WizardUiState(
                notification = notification,
                draft = draft,
                step = resolveStartStep(notification),
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

    fun selectToken(index: Int?) {
        _state.update { it.copy(selectedTokenIndex = index) }
    }

    fun assignTokenRole(tokenIndex: Int, role: TokenRole) {
        _state.update { state ->
            val newTokens = state.tokens.mapIndexed { i, token ->
                run {
                    if (i == tokenIndex) return@run token.copy(role = role)
                    if (token.role == role) return@run token.copy(role = null, value = null)
                    token
                }
            }

            val newDraft = applyTokenRoleToDraft(state.draft, newTokens[tokenIndex], role)
            state.copy(tokens = newTokens, draft = newDraft, selectedTokenIndex = null)
        }
    }

    fun removeTokenRole(tokenIndex: Int) {
        _state.update { state ->
            val removedRole = state.tokens.getOrNull(tokenIndex)?.role
            val newTokens = state.tokens.mapIndexed { i, token ->
                token.copy(role = null, value = null).takeIf { i == tokenIndex } ?: token
            }
            val newDraft = run {
                when (removedRole) {
                    TokenRole.AMOUNT -> return@run state.draft.copy(amount = null)
                    TokenRole.MERCHANT -> return@run state.draft.copy(merchant = null)
                    TokenRole.DATE -> return@run state.draft.copy(date = null)
                    TokenRole.TYPE -> Unit
                    TokenRole.PAYMENT -> Unit
                    TokenRole.INSTALLMENTS -> Unit
                    null -> Unit
                }
                state.draft
            }
            state.copy(tokens = newTokens, draft = newDraft, selectedTokenIndex = null)
        }
    }

    private fun applyTokenRoleToDraft(
        draft: WizardDraft,
        token: Token,
        role: TokenRole,
    ): WizardDraft = when (role) {
        TokenRole.AMOUNT ->
            draft.copy(amount = NotificationTokenizer.parseBrAmount(token.text) ?: draft.amount)
        TokenRole.MERCHANT -> draft.copy(merchant = token.text)
        TokenRole.DATE -> draft.copy(date = parseBrDate(token.text) ?: draft.date)
        // TYPE/PAYMENT/INSTALLMENTS can't be reliably derived from free token text;
        // the chip still highlights the token, but the draft field is set elsewhere.
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

    fun save() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val draft = _state.value.draft
            transactionRepository.save(draft)
                .onSuccess { transactionId ->
                    // The transaction is the source of truth and is never rolled back. The
                    // recurring-series steps below are best-effort: a series create/link failure
                    // is swallowed so it can't block isSuccess. Carry-forward later seeds each
                    // instance's amount from the source month — the backend has no series
                    // defaultAmount (handoff §3.3 divergence).
                    linkSeries(draft, transactionId)
                    runCatching { notificationRepository.markClassified(notificationId, transactionId) }
                    _state.update { it.copy(isSaving = false, isSuccess = true) }
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    /**
     * Discards the captured notification: marks it ignored on the backend so it leaves "Para
     * revisar", then navigates back via [onComplete]. Navigation runs only after the call returns
     * so leaving the screen doesn't cancel it; the result is best-effort (we leave regardless).
     */
    fun ignore(onComplete: () -> Unit) {
        if (_state.value.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            notificationRepository.markIgnored(notificationId)
            onComplete()
        }
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
