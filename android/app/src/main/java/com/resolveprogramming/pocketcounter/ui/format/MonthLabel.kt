package com.resolveprogramming.pocketcounter.ui.format

import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val ptBr = Locale("pt", "BR")

/**
 * The pt-BR month-navigation label shared by every month-scoped screen (Home, Transações, Cartões,
 * Resumo): the full month name with its first letter capitalized, plus the year — e.g. "Junho 2026".
 */
fun monthLabelPtBr(ym: YearMonth): String {
    val month = ym.month.getDisplayName(TextStyle.FULL, ptBr).replaceFirstChar { it.uppercase(ptBr) }
    return "$month ${ym.year}"
}
