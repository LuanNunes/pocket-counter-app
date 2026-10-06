package com.resolveprogramming.pocketcounter.ui.home.components

import java.time.YearMonth

/** Which figure the hero leads with: what is still to pay, or the closed month's net saldo. */
enum class HighlightData { PENDING, BALANCE }

/**
 * A closed month leads with its saldo; the current or a future month still has dues to come, so it
 * leads with pending.
 */
internal fun highlightDataFor(month: YearMonth, today: YearMonth): HighlightData =
    HighlightData.BALANCE.takeIf { month < today } ?: HighlightData.PENDING
