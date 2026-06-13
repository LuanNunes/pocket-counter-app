package com.resolveprogramming.pocketcounter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.local.TokenStore
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.PaymentSourceRepository
import com.resolveprogramming.pocketcounter.data.repository.SourceRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.AutomationStat
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.PaymentSource
import com.resolveprogramming.pocketcounter.domain.model.Source
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TransactionTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class HomeUiState(
    val userName: String = "",
    val monthLabel: String = "",
    val balance: BigDecimal = BigDecimal.ZERO,
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val totalExpense: BigDecimal = BigDecimal.ZERO,
    val pendingReview: List<NotificationItem> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val paymentSources: Map<String, PaymentSource> = emptyMap(),
    val sources: Map<String, Source> = emptyMap(),
    val tags: Map<String, Tag> = emptyMap(),
    /** null while loading */
    val automation: AutomationStat? = null,
    /** null while loading */
    val openBillsTotal: BigDecimal? = null,
    val openBillsCount: Int = 0,
    val isLoading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val transactionRepository: TransactionRepository,
    private val paymentSourceRepository: PaymentSourceRepository,
    private val sourceRepository: SourceRepository,
    private val tagRepository: TagRepository,
    private val cardRepository: CardRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            val pending = notificationRepository.getPendingReview().getOrDefault(emptyList())
            val history = transactionRepository.getHistory().getOrDefault(emptyList())
            val paymentSources = paymentSourceRepository.getAll().getOrDefault(emptyList())
            val sources = sourceRepository.getAll().getOrDefault(emptyList())
            val tags = tagRepository.getAllTags().getOrDefault(emptyList())
            val automation = notificationRepository.getAutomationStat().getOrNull()
            val invoices = cardRepository.getOpenInvoices().getOrDefault(emptyList())
            val userName = tokenStore.getUserName().orEmpty()
            val monthLabel = java.time.LocalDate.now().month
                .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("pt", "BR"))
                .uppercase(java.util.Locale("pt", "BR"))

            // Shared fold so Home and the Transações ledger can never disagree.
            val totals = TransactionTotals.from(history)

            val openBillsTotal = invoices
                .fold(BigDecimal.ZERO) { acc, inv -> acc + inv.total }

            _state.value = HomeUiState(
                userName = userName,
                monthLabel = monthLabel,
                pendingReview = pending,
                history = history,
                paymentSources = paymentSources.associateBy { it.id },
                sources = sources.associateBy { it.id },
                tags = tags.associateBy { it.id },
                balance = totals.balance,
                totalIncome = totals.income,
                totalExpense = totals.expense,
                automation = automation,
                openBillsTotal = openBillsTotal,
                openBillsCount = invoices.size,
                isLoading = false,
            )
        }
    }
}
