package com.resolveprogramming.pocketcounter.ui.transacoes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.PaymentSourceRepository
import com.resolveprogramming.pocketcounter.data.repository.SourceInput
import com.resolveprogramming.pocketcounter.data.repository.SourceRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.DayGroup
import com.resolveprogramming.pocketcounter.domain.model.GroupMode
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.LedgerGroup
import com.resolveprogramming.pocketcounter.domain.model.groupLedger
import com.resolveprogramming.pocketcounter.ui.contextos.CuratedPalette
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.PaymentSource
import com.resolveprogramming.pocketcounter.domain.model.Source
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionTotals
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.domain.model.effectiveTagIds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/**
 * Reorders [itemId] to [targetIndex] within its group, reassigning the group's rows into their
 * existing (possibly non-contiguous) global slots while leaving every other row untouched.
 * Returns the new global id order, or null on a no-op / invalid move. Pure for testability.
 */
internal fun reorderWithinGroup(
    globalIds: List<String>,
    groupIds: List<String>,
    itemId: String,
    targetIndex: Int,
): List<String>? {
    val from = groupIds.indexOf(itemId)
    if (from < 0 || targetIndex == from || targetIndex !in groupIds.indices) return null
    val newGroupOrder = groupIds.toMutableList().apply {
        removeAt(from)
        add(targetIndex, itemId)
    }
    val result = globalIds.toMutableList()
    val positions = groupIds.mapNotNull { id -> globalIds.indexOf(id).takeIf { it >= 0 } }.sorted()
    positions.forEachIndexed { i, pos -> result[pos] = newGroupOrder[i] }
    return result
}

/** Whether the transaction form sheet is adding a new row or editing an existing one. */
sealed interface FormMode {
    data object Add : FormMode
    data class Edit(val itemId: String) : FormMode
}

data class TransacoesUiState(
    val monthKey: String,
    val monthLabel: String = "",
    val items: List<HistoryItem> = emptyList(),
    val dayGroups: List<DayGroup> = emptyList(),
    val groupMode: GroupMode = GroupMode.LISTA,
    val ledgerGroups: List<LedgerGroup> = emptyList(),
    val collapsedGroupIds: Set<String> = emptySet(),
    val totals: TransactionTotals = TransactionTotals.ZERO,
    val query: String = "",
    val searchOpen: Boolean = false,
    val detailTarget: HistoryItem? = null,
    val tagEditTarget: HistoryItem? = null,
    val formMode: FormMode? = null,
    val confirmDeleteId: String? = null,
    val sources: Map<String, Source> = emptyMap(),
    val paymentSources: Map<String, PaymentSource> = emptyMap(),
    val cards: List<CreditCard> = emptyList(),
    val tags: Map<String, Tag> = emptyMap(),
    val contexts: List<TagContext> = emptyList(),
    val toastMessage: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class TransacoesViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val sourceRepository: SourceRepository,
    private val paymentSourceRepository: PaymentSourceRepository,
    private val cardRepository: CardRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val ptBr = Locale("pt", "BR")
    private val currencyFormat = NumberFormat.getCurrencyInstance(ptBr)
    private val _state = MutableStateFlow(
        TransacoesUiState(
            monthKey = YearMonth.now().toString(),
            monthLabel = monthLabel(YearMonth.now()),
        ),
    )
    val state: StateFlow<TransacoesUiState> = _state.asStateFlow()

    init {
        loadLookups()
        loadMonth()
    }

    private fun loadLookups() {
        viewModelScope.launch {
            val sources = sourceRepository.getAll().getOrDefault(emptyList())
            val paymentSources = paymentSourceRepository.getAll().getOrDefault(emptyList())
            val cards = cardRepository.getCards().getOrDefault(emptyList())
            val tags = tagRepository.getAllTags().getOrDefault(emptyList())
            val contexts = tagRepository.getAllContexts().getOrDefault(emptyList())
            _state.update {
                it.copy(
                    sources = sources.associateBy { s -> s.id },
                    paymentSources = paymentSources.associateBy { p -> p.id },
                    cards = cards,
                    tags = tags.associateBy { t -> t.id },
                    contexts = contexts,
                )
            }
        }
    }

    private fun loadMonth() {
        val key = _state.value.monthKey
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            transactionRepository.getMonth(key)
                .onSuccess { items ->
                    _state.update { s ->
                        // A newer month load (or one already applied) supersedes this result.
                        if (s.monthKey != key) return@update s
                        s.copy(
                            items = items,
                            totals = TransactionTotals.from(items),
                            isLoading = false,
                            error = null,
                        ).recomputed()
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun stepMonth(delta: Int) {
        val next = YearMonth.parse(_state.value.monthKey).plusMonths(delta.toLong())
        _state.update {
            it.copy(monthKey = next.toString(), monthLabel = monthLabel(next))
        }
        loadMonth()
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query).recomputed() }
    }

    fun toggleSearch() {
        _state.update { s ->
            val open = !s.searchOpen
            s.copy(searchOpen = open, query = if (open) s.query else "").recomputed()
        }
    }

    fun setGroupMode(mode: GroupMode) {
        _state.update { it.copy(groupMode = mode, collapsedGroupIds = emptySet()).recomputed() }
    }

    fun toggleGroupCollapsed(groupId: String) {
        _state.update {
            val next = if (groupId in it.collapsedGroupIds) it.collapsedGroupIds - groupId else it.collapsedGroupIds + groupId
            it.copy(collapsedGroupIds = next)
        }
    }

    /** Moves a row to [targetIndex] within its group (drag-drop), keeping other rows' slots, then persists. */
    fun moveItemTo(group: LedgerGroup, item: HistoryItem, targetIndex: Int) {
        val reordered = reorderWithinGroup(
            globalIds = _state.value.items.map { it.id },
            groupIds = group.items.map { it.id },
            itemId = item.id,
            targetIndex = targetIndex,
        ) ?: return
        reorderItem(reordered)
    }

    /** Applies a full-month reordering (optimistic) and persists it. Always called with every id. */
    private fun reorderItem(orderedIds: List<String>) {
        // Optimistic local reorder so the UI doesn't flicker; revert via reload on failure.
        val reordered = orderedIds.mapNotNull { id -> _state.value.items.firstOrNull { it.id == id } }
        _state.update { it.copy(items = reordered).recomputed() }
        viewModelScope.launch {
            transactionRepository.reorder(orderedIds)
                .onFailure {
                    _state.update { it.copy(toastMessage = "Não foi possível reordenar") }
                    loadMonth()
                }
        }
    }

    fun openDetail(item: HistoryItem) = _state.update { it.copy(detailTarget = item) }
    fun closeDetail() = _state.update { it.copy(detailTarget = null) }

    fun openTagEdit(item: HistoryItem) = _state.update { it.copy(tagEditTarget = item, detailTarget = null) }
    fun closeTagEdit() = _state.update { it.copy(tagEditTarget = null) }

    /**
     * Saves tags for one transaction. [updateSource] = true (default) writes the tags as the
     * SOURCE's new defaults and sets this transaction back to inherit, so every still-inheriting
     * row retroactively reflects them. false overrides just this transaction.
     */
    fun saveTags(item: HistoryItem, selectedTagIds: List<String>, updateSource: Boolean) {
        viewModelScope.launch {
            val source = _state.value.sources[item.idSource]
            val result = if (updateSource && source != null) {
                sourceRepository.update(
                    source.id,
                    SourceInput(
                        name = source.name,
                        idPaymentSource = source.idPaymentSource,
                        allowsIncome = source.allowsIncome,
                        allowsExpense = source.allowsExpense,
                        refDayRecurring = source.refDayRecurring,
                        amount = source.amount ?: BigDecimal.ZERO,
                        tagIds = selectedTagIds,
                    ),
                ).mapCatching { transactionRepository.setTags(item, null).getOrThrow() }
            } else {
                // Keep an unchanged inheriting transaction inheriting; only override when the
                // selection actually diverges from the currently-effective tags.
                val effective = effectiveTagIds(item.tagIds, source?.tags ?: emptyList())
                val ownTags = if (item.tagIds == null && selectedTagIds.toSet() == effective.toSet()) {
                    null
                } else {
                    selectedTagIds
                }
                transactionRepository.setTags(item, ownTags)
            }
            result
                .onSuccess {
                    val msg = if (updateSource) "Fonte atualizada" else "Tags desta transação"
                    _state.update { it.copy(tagEditTarget = null, toastMessage = msg) }
                }
                .onFailure { _state.update { it.copy(tagEditTarget = null, toastMessage = "Não foi possível salvar as tags") } }
            // Reload regardless so the UI reflects whatever committed (incl. partial source update).
            loadLookups()
            loadMonth()
        }
    }

    fun markPaid(id: String) = mutateStatus { transactionRepository.markPaid(id) }
    fun markPending(id: String) = mutateStatus { transactionRepository.markPending(id) }

    private fun mutateStatus(action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            action()
                .onSuccess {
                    _state.update { it.copy(detailTarget = null, toastMessage = "Status atualizado") }
                    loadMonth()
                }
                .onFailure { _state.update { it.copy(toastMessage = "Não foi possível atualizar") } }
        }
    }

    fun requestDelete(id: String) = _state.update { it.copy(confirmDeleteId = id) }
    fun cancelDelete() = _state.update { it.copy(confirmDeleteId = null) }

    fun confirmDelete() {
        val id = _state.value.confirmDeleteId ?: return
        viewModelScope.launch {
            transactionRepository.delete(id)
                .onSuccess {
                    _state.update {
                        it.copy(confirmDeleteId = null, detailTarget = null, toastMessage = "Transação excluída")
                    }
                    loadMonth()
                }
                .onFailure {
                    _state.update { it.copy(confirmDeleteId = null, toastMessage = "Não foi possível excluir") }
                }
        }
    }

    fun openAdd() = _state.update { it.copy(formMode = FormMode.Add) }

    fun openEdit(item: HistoryItem) = _state.update { it.copy(formMode = FormMode.Edit(item.id)) }

    fun closeForm() = _state.update { it.copy(formMode = null) }

    fun saveForm(draft: WizardDraft) {
        val mode = _state.value.formMode ?: return
        viewModelScope.launch {
            val result = when (mode) {
                is FormMode.Add -> transactionRepository.save(draft).map { }
                is FormMode.Edit -> transactionRepository.update(mode.itemId, draft).map { }
            }
            result
                .onSuccess {
                    val msg = if (mode is FormMode.Edit) "Transação atualizada" else "Transação salva"
                    _state.update { it.copy(formMode = null, toastMessage = msg) }
                    loadMonth()
                }
                .onFailure { _state.update { it.copy(toastMessage = "Não foi possível salvar") } }
        }
    }

    fun consumeToast() = _state.update { it.copy(toastMessage = null) }

    /** Recomputes dayGroups (LISTA) and ledgerGroups (CONTEXTO/TAG) from the filtered items. */
    private fun TransacoesUiState.recomputed(): TransacoesUiState {
        val filtered = filterItems(items, query, sources, paymentSources, tags)
        val days = filtered
            .groupBy { it.date }
            .toSortedMap(reverseOrder())
            .map { (date, dayItems) -> DayGroup(date = date, label = dayLabel(date), items = dayItems) }
        val ledger = if (groupMode == GroupMode.LISTA) {
            emptyList()
        } else {
            groupLedger(filtered, groupMode, sources, tags, contexts, CuratedPalette.argb)
        }
        return copy(dayGroups = days, ledgerGroups = ledger)
    }

    /** Filters by source/payment/effective-tag name + amount (merchant isn't on [HistoryItem]). */
    private fun filterItems(
        items: List<HistoryItem>,
        query: String,
        sources: Map<String, Source>,
        paymentSources: Map<String, PaymentSource>,
        tags: Map<String, Tag>,
    ): List<HistoryItem> {
        if (query.isBlank()) return items
        val q = query.trim().lowercase(ptBr)
        return items.filter { item ->
            val sourceName = sources[item.idSource]?.name?.lowercase(ptBr).orEmpty()
            val payName = paymentSources[item.idPaymentSource]?.name?.lowercase(ptBr).orEmpty()
            val effective = effectiveTagIds(item.tagIds, sources[item.idSource]?.tags ?: emptyList())
            val tagNames = effective.mapNotNull { tags[it]?.name?.lowercase(ptBr) }
            val shown = currencyFormat.format(item.amount.abs()).lowercase(ptBr)
            val raw = item.amount.abs().toPlainString()
            sourceName.contains(q) || payName.contains(q) ||
                shown.contains(q) || raw.contains(q) ||
                tagNames.any { it.contains(q) }
        }
    }

    private fun dayLabel(date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> "Hoje"
            today.minusDays(1) -> "Ontem"
            else -> {
                val month = date.month.getDisplayName(TextStyle.SHORT, ptBr).trimEnd('.').lowercase(ptBr)
                "%02d %s".format(date.dayOfMonth, month)
            }
        }
    }

    private fun monthLabel(ym: YearMonth): String {
        val month = ym.month.getDisplayName(TextStyle.FULL, ptBr)
        return "$month ${ym.year}"
    }
}
