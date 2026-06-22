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
import com.resolveprogramming.pocketcounter.domain.model.groupLedger
import com.resolveprogramming.pocketcounter.ui.contextos.CuratedPalette
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
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
            } else {
                seriesRepository.unlinkTransaction(seriesId, item.id)
                    .onSuccess { _state.update { it.copy(toastMessage = "Removido dos fixos") } }
                    .onFailure { _state.update { it.copy(toastMessage = "Não foi possível marcar como fixo") } }
            }
            loadMonth()
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

    /** Recomputes dayGroups (LISTA) and ledgerGroups (CONTEXTO/TAG) from the filtered items. */
    private fun TransacoesUiState.recomputed(): TransacoesUiState {
        val searched = filterItems(items, query, tags)
        val filtered = if (ledgerFilter == LedgerFilter.FIXOS) searched.filter { it.isFixo } else searched
        val days = filtered
            .groupBy { it.date }
            .toSortedMap(reverseOrder())
            .map { (date, dayItems) -> DayGroup(date = date, label = dayLabel(date), items = dayItems) }
        val ledger = if (groupMode == GroupMode.LISTA) {
            emptyList()
        } else {
            groupLedger(filtered, groupMode, tags, contexts, CuratedPalette.argb)
        }
        return copy(dayGroups = days, ledgerGroups = ledger, fixoCount = items.count { it.isFixo })
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
