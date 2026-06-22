package com.resolveprogramming.pocketcounter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.local.TokenStore
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.AutomationStat
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
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
    val tags: Map<String, Tag> = emptyMap(),
    val cards: Map<String, CreditCard> = emptyMap(),
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
            val tags = tagRepository.getAllTags().getOrDefault(emptyList())
            val automation = notificationRepository.getAutomationStat().getOrNull()
            val cards = cardRepository.getCards().getOrDefault(emptyList())
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
                tags = tags.associateBy { it.id },
                cards = cards.associateBy { it.id },
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
