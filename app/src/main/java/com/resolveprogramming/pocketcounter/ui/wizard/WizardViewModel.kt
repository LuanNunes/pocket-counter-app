package com.resolveprogramming.pocketcounter.ui.wizard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.PaymentSourceRepository
import com.resolveprogramming.pocketcounter.data.repository.SourceRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.PaymentSource
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.Source
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
    TYPE(0, "Tipo de transação", "1 de 5"),
    AMOUNT(1, "Valor e data", "2 de 5"),
    PAYMENT(2, "Meio de pagamento", "3 de 5"),
    SOURCE(3, "Source", "4 de 5"),
    TAGS(4, "Tags", "5 de 5"),
}

data class WizardUiState(
    val notification: NotificationItem? = null,
    val draft: WizardDraft = WizardDraft(),
    val step: WizardStep = WizardStep.TYPE,
    val paymentSources: List<PaymentSource> = emptyList(),
    val filteredSources: List<Source> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val contexts: List<TagContext> = emptyList(),
    val sourceSearchQuery: String = "",
    val tagSearchQuery: String = "",
    val tokens: List<Token> = emptyList(),
    val selectedTokenIndex: Int? = null,
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
    private val paymentSourceRepository: PaymentSourceRepository,
    private val sourceRepository: SourceRepository,
    private val tagRepository: TagRepository,
    private val transactionRepository: TransactionRepository,
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
            val paymentSources = paymentSourceRepository.getAll().getOrDefault(emptyList())
            val tags = tagRepository.getAllTags().getOrDefault(emptyList())
            val contexts = tagRepository.getAllContexts().getOrDefault(emptyList())

            val classifyResult = notificationRepository.classify(notificationId, base)
            val classified = classifyResult.getOrNull()

            if (classified?.pendingTransactionId != null) {
                _state.value = WizardUiState(
                    notification = classified.notification,
                    paymentSources = paymentSources,
                    allTags = tags,
                    contexts = contexts,
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
                paymentSources = paymentSources,
                allTags = tags,
                contexts = contexts,
                tokens = tokens,
                isLoading = false,
                error = degradeError,
            )

            if (draft.idPaymentSource != null && draft.type != null) {
                loadFilteredSources(draft.idPaymentSource, draft.type)
            }
        }
    }

    private fun resolveStartStep(notification: NotificationItem): WizardStep =
        when (notification.status) {
            NotificationStatus.NEEDS_TAGS -> WizardStep.TAGS
            else -> WizardStep.TYPE
        }

    fun selectType(type: TransactionType) {
        _state.update { it.copy(draft = it.draft.copy(type = type)) }
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
            val draft = if (enabled && notification?.parsed?.installments != null) {
                state.draft.copy(
                    installments = notification.parsed.installments,
                    installmentValue = notification.parsed.installmentValue,
                )
            } else {
                state.draft.copy(installments = null, installmentValue = null)
            }
            state.copy(draft = draft)
        }
    }

    fun selectPaymentSource(id: String) {
        _state.update { state ->
            val newDraft = state.draft.withPaymentSourceReset(id)
            state.copy(draft = newDraft)
        }
        val draft = _state.value.draft
        if (draft.type != null) {
            viewModelScope.launch { loadFilteredSources(id, draft.type) }
        }
    }

    fun selectSource(id: String) {
        _state.update { it.copy(draft = it.draft.copy(idSource = id)) }
    }

    fun updateSourceSearch(query: String) {
        _state.update { it.copy(sourceSearchQuery = query) }
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
                when {
                    i == tokenIndex -> token.copy(role = role)
                    token.role == role -> token.copy(role = null, value = null)
                    else -> token
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
                if (i == tokenIndex) token.copy(role = null, value = null) else token
            }
            val newDraft = when (removedRole) {
                TokenRole.AMOUNT -> state.draft.copy(amount = null)
                TokenRole.MERCHANT -> state.draft.copy(merchant = null)
                TokenRole.DATE -> state.draft.copy(date = null)
                else -> state.draft
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
            val year = when {
                yearRaw.isEmpty() -> LocalDate.now().year
                yearRaw.length == 2 -> 2000 + yearRaw.toInt()
                else -> yearRaw.toInt()
            }
            LocalDate.of(year, month, day)
        }.getOrNull()
    }

    fun nextStep() {
        _state.update { state ->
            val nextStep = WizardStep.entries.getOrNull(state.step.index + 1)
            if (nextStep != null) {
                state.copy(step = nextStep)
            } else {
                state
            }
        }
        val currentState = _state.value
        if (currentState.step == WizardStep.SOURCE) {
            val draft = currentState.draft
            if (draft.idPaymentSource != null && draft.type != null) {
                viewModelScope.launch { loadFilteredSources(draft.idPaymentSource, draft.type) }
            }
        }
    }

    fun previousStep() {
        _state.update { state ->
            val prevStep = WizardStep.entries.getOrNull(state.step.index - 1)
            if (prevStep != null) state.copy(step = prevStep) else state
        }
    }

    fun save() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            transactionRepository.save(_state.value.draft)
                .onSuccess { transactionId ->
                    runCatching { notificationRepository.markClassified(notificationId, transactionId) }
                    _state.update { it.copy(isSaving = false, isSuccess = true) }
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
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

    fun createSource(name: String) {
        viewModelScope.launch {
            val draft = _state.value.draft
            val paymentSourceId = draft.idPaymentSource ?: return@launch
            val type = draft.type ?: return@launch
            sourceRepository.create(name, paymentSourceId, type)
                .onSuccess { newSource ->
                    _state.update { it.copy(draft = it.draft.copy(idSource = newSource.id)) }
                    loadFilteredSources(paymentSourceId, type)
                }
        }
    }

    private suspend fun loadFilteredSources(paymentSourceId: String, type: TransactionType) {
        sourceRepository.getByPaymentSourceAndType(paymentSourceId, type)
            .onSuccess { sources ->
                _state.update { it.copy(filteredSources = sources) }
            }
    }
}
