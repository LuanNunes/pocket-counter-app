package com.resolveprogramming.pocketcounter.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/** Neutral grey for the "Sem categoria" fatura bucket. */
const val UNCATEGORIZED_COLOR: Long = 0xFF9AA0A6L

/** Neutral grey for the folded "Outros" donut slice — distinct from [UNCATEGORIZED_COLOR]. */
const val OUTROS_COLOR: Long = 0xFFC4C8CEL

private const val OUTROS_ID = "outros"

/**
 * Buckets a card invoice's items into per-context [SummaryGroup]s whose totals reconcile to
 * [faturaTotal]. The remainder between the fatura total and the sum of itemized amounts lands in
 * the "Sem categoria" bucket so the parts always add up to the whole. Pure + testable.
 */
fun buildFaturaBreakdown(
    items: List<InvoiceItem>,
    faturaTotal: BigDecimal,
    tagToContext: Map<String, String?>,
    contextById: Map<String, TagContext>,
): List<SummaryGroup> {
    if (faturaTotal <= BigDecimal.ZERO) return emptyList()

    class Bucket(val name: String, val color: Long, var total: BigDecimal) {
        val tagTotals: LinkedHashMap<String, BigDecimal> = LinkedHashMap()
    }

    val buckets = LinkedHashMap<String, Bucket>()

    fun uncategorized(): Bucket = buckets.getOrPut(UNCATEGORIZED_CONTEXT_ID) {
        Bucket("Sem categoria", UNCATEGORIZED_COLOR, BigDecimal.ZERO)
    }

    var itemized = BigDecimal.ZERO
    items.forEach { item ->
        itemized += item.amount
        val ctxId = item.tags.firstOrNull()?.id?.let { tagToContext[it] }?.takeIf { it.isNotBlank() }
        val ctx = ctxId?.let { contextById[it] }
        val bucket = if (ctx == null) {
            uncategorized()
        } else {
            buckets.getOrPut(ctx.id) { Bucket(ctx.name, ctx.color, BigDecimal.ZERO) }
        }
        bucket.total += item.amount
        val tagName = item.tags.firstOrNull()?.name ?: "sem etiqueta"
        bucket.tagTotals[tagName] = (bucket.tagTotals[tagName] ?: BigDecimal.ZERO) + item.amount
    }

    // The synthetic remainder (faturaTotal − itemized) folds into "Sem categoria" total only —
    // it is not attributed to any tag because it has no tag context.
    val remainder = faturaTotal - itemized
    if (remainder.signum() != 0) {
        uncategorized().let { it.total += remainder }
    }

    return buckets.map { (id, bucket) ->
        SummaryGroup(
            id = id,
            name = bucket.name,
            color = bucket.color,
            total = bucket.total,
            pct = bucket.total.divide(faturaTotal, 4, RoundingMode.HALF_UP).toFloat(),
            delta = null,
            prevTotal = null,
            tags = bucket.tagTotals.map { (name, total) -> SummaryTag(name, total) }
                .sortedByDescending { it.total },
        )
    }.sortedWith(
        compareBy<SummaryGroup> { it.id in UNCATEGORIZED_GROUP_IDS }.thenByDescending { it.total },
    )
}

/**
 * Caps a breakdown to at most [maxSlices] donut slices: drops non-positive groups, keeps the top
 * real categories, folds the smallest real tail into a single "Outros" slice, and always keeps the
 * "Sem categoria" group(s) as their own final slice. Pure + testable.
 */
fun faturaDonutSlices(groups: List<SummaryGroup>, maxSlices: Int = 7): List<SummaryGroup> {
    val positive = groups.filter { it.pct > 0f }
    if (positive.size <= maxSlices) return positive

    val (uncategorized, real) = positive.partition { it.id in UNCATEGORIZED_GROUP_IDS }
    val keep = (maxSlices - uncategorized.size - 1).coerceAtLeast(1)
    val kept = real.take(keep)
    val tail = real.drop(keep)
    if (tail.isEmpty()) return kept + uncategorized

    val outrosTotal = tail.fold(BigDecimal.ZERO) { acc, g -> acc + g.total }
    val outrosPct = tail.fold(0f) { acc, g -> acc + g.pct }
    val outros = SummaryGroup(
        id = OUTROS_ID,
        name = "Outros",
        color = OUTROS_COLOR,
        total = outrosTotal,
        pct = outrosPct,
        delta = null,
        prevTotal = null,
        tags = emptyList(),
    )
    return kept + outros + uncategorized
}
