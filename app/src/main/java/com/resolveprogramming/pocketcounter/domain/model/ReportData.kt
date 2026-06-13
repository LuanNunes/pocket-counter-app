package com.resolveprogramming.pocketcounter.domain.model

import java.math.BigDecimal
import java.time.YearMonth

enum class ReportPeriod { MES, TRIMESTRE, ANO }

enum class ReportChartType { BARS, AREA, LINES, PIE }

enum class ReportDetailMode { CARTOES, TABELA }

/** The months a report period covers: MES = the anchor; TRIMESTRE = its calendar quarter; ANO = Jan–Dec. */
fun reportPeriodMonths(period: ReportPeriod, anchor: YearMonth): List<YearMonth> = when (period) {
    ReportPeriod.MES -> listOf(anchor)
    ReportPeriod.TRIMESTRE -> {
        val startMonth = ((anchor.monthValue - 1) / 3) * 3 + 1
        val start = YearMonth.of(anchor.year, startMonth)
        (0..2).map { start.plusMonths(it.toLong()) }
    }
    ReportPeriod.ANO -> (1..12).map { YearMonth.of(anchor.year, it) }
}

/** One month's totals + group buckets within a report period. */
data class ReportMonth(
    val key: String,        // yyyy-MM
    val label: String,      // 3-letter pt-BR month, e.g. "jan"
    val exp: BigDecimal,
    val inc: BigDecimal,
    val saldo: BigDecimal,
    val expGroups: List<SummaryGroup>,
    val incGroups: List<SummaryGroup>,
)

/** A group's value across the period's months (aligned to the month order) + total/delta. */
data class ReportSeries(
    val id: String,
    val name: String,
    val color: Long,
    val vals: List<BigDecimal>,
    val total: BigDecimal,
    /** relative change last-vs-prev month; null = "novo" (no prior). */
    val delta: Float?,
)

data class ReportKpis(
    val exp: BigDecimal,
    val inc: BigDecimal,
    val saldo: BigDecimal,
    val expAvg: BigDecimal,
    val months: Int,
)

data class ReportData(
    val period: ReportPeriod,
    val months: List<ReportMonth>,
    val expSeries: List<ReportSeries>,
    val incSeries: List<ReportSeries>,
    val kpis: ReportKpis,
    val rangeLabel: String,
)

/**
 * Folds per-month group buckets into one series per group, aligned to [monthKeys] order.
 * Pure + testable. [pick] selects the expense or income groups from each month.
 */
fun seriesFrom(
    monthKeys: List<String>,
    months: List<ReportMonth>,
    pick: (ReportMonth) -> List<SummaryGroup>,
): List<ReportSeries> {
    val byKey = months.associateBy { it.key }
    // Collect group identity (id → name/color) from any month that has it.
    val identity = LinkedHashMap<String, Pair<String, Long>>()
    monthKeys.forEach { k ->
        byKey[k]?.let { pick(it) }?.forEach { g -> identity.putIfAbsent(g.id, g.name to g.color) }
    }
    return identity.map { (id, nameColor) ->
        val vals = monthKeys.map { k ->
            byKey[k]?.let { pick(it) }?.firstOrNull { it.id == id }?.total ?: BigDecimal.ZERO
        }
        val total = vals.fold(BigDecimal.ZERO, BigDecimal::add)
        val prev = vals.getOrNull(vals.size - 2) ?: BigDecimal.ZERO
        val last = vals.lastOrNull() ?: BigDecimal.ZERO
        val delta = if (prev > BigDecimal.ONE) {
            last.subtract(prev).divide(prev, 4, java.math.RoundingMode.HALF_UP).toFloat()
        } else {
            null
        }
        ReportSeries(id, nameColor.first, nameColor.second, vals, total, delta)
    }.sortedByDescending { it.total }
}
