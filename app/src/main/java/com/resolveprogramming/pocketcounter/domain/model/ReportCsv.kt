package com.resolveprogramming.pocketcounter.domain.model

import java.math.BigDecimal

private const val SEP = ";"

fun reportToCsv(report: ReportData, kind: TransactionType): String {
    val series = report.expSeries.takeIf { kind == TransactionType.EXPENSE } ?: report.incSeries
    val dimension = "Contexto".takeIf { kind == TransactionType.EXPENSE } ?: "Categoria"
    val monthLabels = report.months.map { it.label }

    val header = (listOf(dimension) + monthLabels + "Total").joinToString(SEP) { csvField(it) }

    val rows = series.map { s ->
        (listOf(csvField(s.name)) +
            s.vals.map { it.toPlainString() } +
            s.total.toPlainString()).joinToString(SEP)
    }

    val monthTotals = report.months.indices.map { i ->
        series.fold(BigDecimal.ZERO) { acc, s -> acc + (s.vals.getOrNull(i) ?: BigDecimal.ZERO) }
    }
    val grandTotal = series.fold(BigDecimal.ZERO) { acc, s -> acc + s.total }
    val totalsRow = (listOf("Total") +
        monthTotals.map { it.toPlainString() } +
        grandTotal.toPlainString()).joinToString(SEP)

    return (listOf(header) + rows + totalsRow).joinToString("\n")
}

private fun csvField(value: String): String {
    if (value.contains(SEP) || value.contains("\"") || value.contains("\n")) {
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
    return value
}
