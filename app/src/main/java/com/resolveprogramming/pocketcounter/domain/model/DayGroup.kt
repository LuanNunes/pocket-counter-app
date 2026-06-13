package com.resolveprogramming.pocketcounter.domain.model

import java.time.LocalDate

/** A day's worth of ledger rows, with a pt-BR header label ("Hoje" / "Ontem" / "13 jun"). */
data class DayGroup(
    val date: LocalDate,
    val label: String,
    val items: List<HistoryItem>,
)
