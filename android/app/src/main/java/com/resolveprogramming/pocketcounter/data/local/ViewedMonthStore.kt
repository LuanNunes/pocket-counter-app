package com.resolveprogramming.pocketcounter.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-scoped, app-wide "viewed month" shared by every month-scoped screen (Transações, Cartões, …)
 * so stepping the month on one screen is reflected on the others. Held as a "YYYY-MM" key. Resets to the
 * current month on each cold start.
 */
@Singleton
class ViewedMonthStore @Inject constructor() {

    private val _month = MutableStateFlow(YearMonth.now().toString())
    val month: StateFlow<String> = _month.asStateFlow()

    fun step(delta: Int) {
        _month.update { YearMonth.parse(it).plusMonths(delta.toLong()).toString() }
    }

    fun set(monthKey: String) {
        _month.value = monthKey
    }
}
