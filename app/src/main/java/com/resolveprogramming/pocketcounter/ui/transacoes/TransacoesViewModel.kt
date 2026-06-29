package com.resolveprogramming.pocketcounter.ui.transacoes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers
import com.resolveprogramming.pocketcounter.data.repository.SeriesRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.DayGroup
import com.resolveprogramming.pocketcounter.domain.model.GroupMode
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.LedgerGroup
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.groupLedger
import com.resolveprogramming.pocketcounter.ui.contextos.CuratedPalette
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionTotals
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.domain.model.effectiveTagIds
import java.math.BigDecimal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

/** Ledger row filter: all rows, or only those linked to a recurring series ("fixos"). */
enum class LedgerFilter { TODOS, FIXOS }

/** Whether the transaction form sheet is adding a new row or editing an existing one. */
sealed interface FormMode {
    data class Add(val initialType: TransactionType? = null) : FormMode
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
    val typeFilter: TransactionType = TransactionType.EXPENSE,
    val typeTotal: BigDecimal = BigDecimal.ZERO,
    val typeCount: Int = 0,
    val expenseCount: Int = 0,
    val incomeCount: Int = 0,
    val query: String = "",
    val ledgerFilter: LedgerFilter = LedgerFilter.TODOS,
    val fixoCount: Int = 0,
    val searchOpen: Boolean = false,
    val detailTarget: HistoryItem? = null,
    val tagEditTarget: HistoryItem? = null,
    val formMode: FormMode? = null,
    val confirmDeleteId: String? = null,
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
    private val cardRepository: CardRepository,
    private val tagRepository: TagRepository,
    private val seriesRepository: SeriesRepository,
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
            val cards = cardRepository.getCards().getOrDefault(emptyList())
            val tags = tagRepository.getAllTags().getOrDefault(emptyList())
            val contexts = tagRepository.getAllContexts().getOrDefault(emptyList())
            _state.update {
                it.copy(
                    cards = cards,
                    tags = tags.associateBy { t -> t.id },
                    contexts = contexts,
                )
            }
        }
    }

    private fun loadMonth(showLoading: Boolean = true) {
        val key = _state.value.monthKey
        viewModelScope.launch {
            if (showLoading) _state.update { it.copy(isLoading = true) }
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
            s.copy(searchOpen = open, query = s.query.takeIf { open } ?: "").recomputed()
        }
    }

    fun setTypeFilter(type: TransactionType) {
        _state.update { it.copy(typeFilter = type).recomputed() }
    }

    fun setLedgerFilter(filter: LedgerFilter) {
        _state.update { it.copy(ledgerFilter = filter).recomputed() }
    }

    /**
     * Marks a row as "fixo" by linking it to a recurring series (creating one first if it has
     * none), or unlinks it when it already belongs to a series. Best-effort: failures surface a
     * toast and reload to reflect the committed truth.
     */
    fun toggleFixo(item: HistoryItem) {
        viewModelScope.launch {
            val seriesId = item.seriesId
            if (seriesId != null) {
                seriesRepository.unlinkTransaction(seriesId, item.id)
                    .onSuccess { _state.update { it.copy(toastMessage = "Removido dos fixos") } }
                    .onFailure { _state.update { it.copy(toastMessage = "Não foi possível marcar como fixo") } }
            }
            if (seriesId == null) {
                val name = item.name?.takeIf { it.isNotBlank() }
                    ?: item.displayTitle().takeIf { it != "—" }
                    ?: "Conta fixa"
                seriesRepository.create(
                    name = name,
                    type = item.type,
                    recurrenceDay = item.date.dayOfMonth,
                )
                    .onSuccess { series ->
                        seriesRepository.linkTransaction(series.id, item.id, includePrevious = false)
                            .onSuccess { _state.update { it.copy(toastMessage = "Marcado como fixo") } }
                            .onFailure { _state.update { it.copy(toastMessage = "Não foi possível marcar como fixo") } }
                    }
                    .onFailure { _state.update { it.copy(toastMessage = "Não foi possível marcar como fixo") } }
            }
            loadMonth(showLoading = false)
        }
    }

    fun setGroupMode(mode: GroupMode) {
        _state.update { it.copy(groupMode = mode, collapsedGroupIds = emptySet()).recomputed() }
    }

    fun toggleGroupCollapsed(groupId: String) {
        _state.update {
            val collapsed = groupId in it.collapsedGroupIds
            val next = (it.collapsedGroupIds - groupId).takeIf { collapsed } ?: (it.collapsedGroupIds + groupId)
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
        // Stamp displayOrder = index to mirror what the backend persists (ReorderItemDto), otherwise
        // recomputed()'s LEDGER_ORDER sort (displayOrder-first) would discard the new order on the
        // spot and the move would only "stick" after the backend reload.
        val reordered = orderedIds.mapIndexedNotNull { index, id ->
            _state.value.items.firstOrNull { it.id == id }?.copy(displayOrder = index)
        }
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

    /** Saves tags as a per-transaction override. */
    fun saveTags(item: HistoryItem, selectedTagIds: List<String>) {
        viewModelScope.launch {
            transactionRepository.setTags(item, selectedTagIds)
                .onSuccess {
                    _state.update { it.copy(tagEditTarget = null, toastMessage = "Tags desta transação") }
                }
                .onFailure { _state.update { it.copy(tagEditTarget = null, toastMessage = "Não foi possível salvar as tags") } }
            loadLookups()
            loadMonth()
        }
    }

    fun markPaid(id: String) =
        mutateStatus(id, PaymentStatus.PAID, "Marcada como paga ✓") { transactionRepository.markPaid(id) }

    fun markPending(id: String) =
        mutateStatus(id, PaymentStatus.PENDING, "Marcada como pendente") { transactionRepository.markPending(id) }

    /**
     * Optimistically flips [id]'s payment status to [target] so the row updates instantly, then
     * confirms with the backend via a silent reload (no full-screen spinner). On failure the local
     * items are restored and an error toast is shown.
     */
    private fun mutateStatus(
        id: String,
        target: PaymentStatus,
        successMessage: String,
        action: suspend () -> Result<Unit>,
    ) {
        val previousItems = _state.value.items
        val optimistic = previousItems.map { item ->
            item.copy(statusPayment = target).takeIf { item.id == id } ?: item
        }
        _state.update {
            it.copy(items = optimistic, detailTarget = null, toastMessage = successMessage).recomputed()
        }
        viewModelScope.launch {
            action()
                .onSuccess { loadMonth(showLoading = false) }
                .onFailure {
                    _state.update {
                        it.copy(items = previousItems, toastMessage = "Não foi possível atualizar").recomputed()
                    }
                }
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

    fun openAdd(type: TransactionType? = null) = _state.update { it.copy(formMode = FormMode.Add(type)) }

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
                    val msg = "Transação atualizada".takeIf { mode is FormMode.Edit } ?: "Transação salva"
                    _state.update { it.copy(formMode = null, toastMessage = msg) }
                    loadMonth()
                }
                .onFailure { _state.update { it.copy(toastMessage = "Não foi possível salvar") } }
        }
    }

    /**
     * Seeds the viewed month with the previous month's recurring entries (carry-forward).
     * Target = viewed month ref; source = the month before it. Best-effort: a toast reports the
     * created/skipped counts and the month reloads to surface the new rows.
     */
    fun generateBalance() {
        val viewed = YearMonth.parse(_state.value.monthKey)
        val target = RemoteMappers.monthKeyToRef(viewed.toString())
        val source = RemoteMappers.monthKeyToRef(viewed.minusMonths(1).toString())
        viewModelScope.launch {
            seriesRepository.carryForward(target, source, onlyRecurring = true)
                .onSuccess { result ->
                    _state.update {
                        it.copy(toastMessage = "Gerado: ${result.createdCount} · ignorados: ${result.skippedCount}")
                    }
                    loadMonth()
                }
                .onFailure { _state.update { it.copy(toastMessage = "Não foi possível gerar o saldo") } }
        }
    }

    fun consumeToast() = _state.update { it.copy(toastMessage = null) }

    /**
     * Recomputes all derived display state from [items].
     *
     * Pipeline order: type → search → fixo. This ensures:
     *  - [expenseCount]/[incomeCount] are full-month counts (stable label values).
     *  - [fixoCount] counts fixos within the active type (used for fixo-toggle label).
     *  - [typeTotal]/[typeCount] reflect the visible rows after all filters.
     *  - [dayGroups] and [ledgerGroups] show only the visible rows.
     *  - [totals] is never touched here — it is full-month and set by loadMonth().
     */
    private fun TransacoesUiState.recomputed(): TransacoesUiState {
        val byType = items.filter { it.type == typeFilter }
        val searched = filterItems(byType, query, tags)
        val filtered = searched.filter { it.isFixo }.takeIf { ledgerFilter == LedgerFilter.FIXOS } ?: searched
        val canonical = filtered.sortedWith(HistoryItem.LEDGER_ORDER)
        val days = canonical
            .groupBy { it.date }
            .toSortedMap(reverseOrder())
            .map { (date, dayItems) -> DayGroup(date = date, label = dayLabel(date), items = dayItems) }
        val ledger = run {
            if (groupMode == GroupMode.LISTA) return@run emptyList()
            groupLedger(canonical, groupMode, tags, contexts, CuratedPalette.argb)
        }
        val total = canonical.fold(BigDecimal.ZERO) { acc, item -> acc + item.amount.abs() }
        return copy(
            dayGroups = days,
            ledgerGroups = ledger,
            fixoCount = byType.count { it.isFixo },
            expenseCount = items.count { it.type == TransactionType.EXPENSE },
            incomeCount = items.count { it.type == TransactionType.INCOME },
            typeTotal = total,
            typeCount = canonical.size,
        )
    }

    /** Filters by title/effective-tag name + amount. */
    private fun filterItems(
        items: List<HistoryItem>,
        query: String,
        tags: Map<String, Tag>,
    ): List<HistoryItem> {
        if (query.isBlank()) return items
        val q = query.trim().lowercase(ptBr)
        return items.filter { item ->
            val title = item.displayTitle().lowercase(ptBr)
            val effective = effectiveTagIds(item.tagIds, emptyList())
            val tagNames = effective.mapNotNull { tags[it]?.name?.lowercase(ptBr) }
            val shown = currencyFormat.format(item.amount.abs()).lowercase(ptBr)
            val raw = item.amount.abs().toPlainString()
            title.contains(q) ||
                shown.contains(q) || raw.contains(q) ||
                tagNames.any { it.contains(q) }
        }
    }

    private fun dayLabel(date: LocalDate): String {
        val today = LocalDate.now()
        return run {
            if (date == today) return@run "Hoje"
            if (date == today.minusDays(1)) return@run "Ontem"
            val month = date.month.getDisplayName(TextStyle.SHORT, ptBr).trimEnd('.').lowercase(ptBr)
            "%02d %s".format(date.dayOfMonth, month)
        }
    }

    private fun monthLabel(ym: YearMonth): String {
        val month = ym.month.getDisplayName(TextStyle.FULL, ptBr)
        return "$month ${ym.year}"
    }
}
