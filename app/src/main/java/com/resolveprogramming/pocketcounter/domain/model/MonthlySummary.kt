package com.resolveprogramming.pocketcounter.domain.model

import java.math.BigDecimal

data class SummaryTag(
    val name: String,
    val total: BigDecimal,
)

data class SummaryGroup(
    val id: String,
    val name: String,
    /** ARGB Long — same format as TagContext.color */
    val color: Long,
    val total: BigDecimal,
    /** fraction of grand total, 0..1 */
    val pct: Float,
    /** relative change vs baseline: positive = more spent; null = no baseline (novo) */
    val delta: Float?,
    /** previous period total; null = "sem registro" */
    val prevTotal: BigDecimal?,
    val tags: List<SummaryTag>,
)

data class MonthlySummary(
    val groups: List<SummaryGroup>,
    val total: BigDecimal,
    /** relative change of grand total vs baseline; null if no baseline */
    val totalDelta: Float?,
    /** baseline grand total; null if no baseline */
    val baseTotal: BigDecimal?,
)

data class CompareOption(
    val key: String,
    val label: String,
    val short: String,
)
