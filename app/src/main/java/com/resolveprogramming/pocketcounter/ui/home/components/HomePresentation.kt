package com.resolveprogramming.pocketcounter.ui.home.components

/** The visual placeholder for an unknown figure — same glyph the fatura tile already uses. */
internal const val UNKNOWN_FIGURE = "—"

/** The figure once it is known, [UNKNOWN_FIGURE] before that. */
internal fun figureOrDash(value: String, hasLoaded: Boolean): String = value.takeIf { hasLoaded } ?: UNKNOWN_FIGURE

/** The greeting and the avatar initial read from this one string, so a blank name never shows "?". */
internal fun homeGreeting(userName: String): String = userName.trim().ifBlank { "Bem-vindo" }

/** TalkBack contentDescription for the hero's headline, while [hasLoaded] is still false. */
internal fun heroPendingDescription(monthLabel: String, formattedPending: String, hasLoaded: Boolean): String {
    if (!hasLoaded) return "Pendente de $monthLabel, carregando"
    return "Pendente de $monthLabel, $formattedPending"
}

/** TalkBack contentDescription for one Despesas/Receitas/Saldo row in the hero's KPI stack. */
internal fun kpiRowDescription(label: String, formattedValue: String, count: Int, hasLoaded: Boolean): String {
    if (!hasLoaded) return "$label, carregando"
    return "$label, $formattedValue, ${lancamentosCount(count)}"
}

private fun lancamentosCount(count: Int): String {
    if (count == 1) return "1 lançamento"
    return "$count lançamentos"
}

/** The visible sub-line under "Lançamentos" on the swipe cue. */
internal fun swipeCueLabel(count: Int, hasLoaded: Boolean): String {
    if (!hasLoaded) return UNKNOWN_FIGURE
    if (count == 0) return "deslize para ver"
    return "$count no mês"
}

/** Announces [swipeCueLabel] verbatim, so Voice Access can act on what is on screen. */
internal fun swipeCueDescription(count: Int, hasLoaded: Boolean): String {
    if (!hasLoaded) return "Lançamentos, carregando"
    return "Lançamentos, ${swipeCueLabel(count, hasLoaded)}"
}

/** TalkBack contentDescription for the fatura QuickTile. */
internal fun faturasTileDescription(formattedTotal: String, cardCount: Int, isLoading: Boolean): String {
    if (isLoading) return "Faturas, carregando"
    return "Faturas, $formattedTotal, ${cartoesCount(cardCount)}"
}

private fun cartoesCount(count: Int): String {
    if (count == 1) return "1 cartão"
    return "$count cartões"
}

/** TalkBack contentDescription for the Resumo QuickTile — static, mirroring its visible copy. */
internal fun resumoTileDescription(): String = "Resumo do mês, Para onde foi"
