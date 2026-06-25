package com.resolveprogramming.pocketcounter.domain.model

import kotlin.math.roundToInt

data class AutomationStat(
    val monthTotal: Int,
    val autoDone: Int,
)

fun automationPercent(autoDone: Int, monthTotal: Int): Int {
    if (monthTotal == 0) return 0
    return (autoDone.toDouble() / monthTotal * 100).roundToInt().coerceIn(0, 100)
}
