package com.resolveprogramming.pocketcounter.domain.model

import java.math.BigDecimal

enum class GroupMode { LISTA, CONTEXTO, TAG }

/** A ledger section: despesas bucket by Contexto, receitas by categoria de renda (CONTEXTO), or by first tag (TAG). */
data class LedgerGroup(
    val id: String,
    val title: String,
    val color: Long?,
    val items: List<HistoryItem>,
    val subtotal: BigDecimal,
    val type: TransactionType,
)

private const val NO_CTX = "__none"

/**
 * Buckets a month's rows per the group-reorder spec. A row is placed by its FIRST effective tag
 * (never duplicated across groups). LISTA returns empty — the screen keeps its day-grouping there.
 */
fun groupLedger(
    items: List<HistoryItem>,
    mode: GroupMode,
    tags: Map<String, Tag>,
    contexts: List<TagContext>,
    incomePalette: List<Long>,
): List<LedgerGroup> {
    fun firstTagId(item: HistoryItem): String? =
        effectiveTagIds(item.tagIds, emptyList()).firstOrNull()

    fun rowContextId(item: HistoryItem): String {
        val tagId = firstTagId(item) ?: return NO_CTX
        val ctx = tags[tagId]?.idContext
        return if (ctx.isNullOrBlank()) NO_CTX else ctx
    }

    fun subtotal(list: List<HistoryItem>): BigDecimal =
        list.fold(BigDecimal.ZERO) { acc, i -> acc + i.amount.abs() }

    return when (mode) {
        GroupMode.LISTA -> emptyList()

        GroupMode.CONTEXTO -> {
            val expenses = items.filter { it.type == TransactionType.EXPENSE }
            val incomes = items.filter { it.type == TransactionType.INCOME }

            // Expenses by context, in context order, "Sem contexto" last.
            val byCtx = expenses.groupBy { rowContextId(it) }
            val ctxOrder = contexts.map { it.id } + NO_CTX
            val expenseGroups = ctxOrder.mapNotNull { cid ->
                val list = byCtx[cid] ?: return@mapNotNull null
                val ctx = contexts.firstOrNull { it.id == cid }
                LedgerGroup("ctx_$cid", ctx?.name ?: "Sem contexto", ctx?.color, list, subtotal(list), TransactionType.EXPENSE)
            }

            // Incomes by categoria de renda (the effective kind=INCOME tag), ordered by subtotal
            // desc, colored from the income palette. "Sem categoria" when no income tag resolves.
            fun incomeCategoryId(item: HistoryItem): String =
                effectiveTagIds(item.tagIds, emptyList())
                    .firstOrNull { tags[it]?.kind == TransactionType.INCOME }
                    ?: NO_CTX
            val incomeGroups = incomes.groupBy { incomeCategoryId(it) }
                .map { (tid, list) ->
                    LedgerGroup("inc_$tid", tags[tid]?.name ?: "Sem categoria", null, list, subtotal(list), TransactionType.INCOME)
                }
                .sortedByDescending { it.subtotal }
                .mapIndexed { i, g ->
                    g.copy(color = incomePalette.getOrNull(i % incomePalette.size.coerceAtLeast(1)))
                }

            expenseGroups + incomeGroups
        }

        GroupMode.TAG -> {
            val byTag = items.groupBy { firstTagId(it) }
            val tagged = byTag.filterKeys { it != null }.map { (tid, list) ->
                val tag = tags[tid]
                val ctx = contexts.firstOrNull { it.id == tag?.idContext }
                LedgerGroup("tag_$tid", tag?.name ?: "—", ctx?.color, list, subtotal(list), list.first().type)
            }.sortedBy { it.title.lowercase() }
            val untagged = byTag[null]?.let {
                listOf(LedgerGroup("tag_none", "Sem tag", null, it, subtotal(it), it.first().type))
            }.orEmpty()
            tagged + untagged
        }
    }
}
