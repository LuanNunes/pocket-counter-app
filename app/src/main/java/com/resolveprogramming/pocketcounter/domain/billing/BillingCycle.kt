package com.resolveprogramming.pocketcounter.domain.billing

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object BillingCycle {

    private val dueLabelFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale("pt", "BR"))

    /**
     * Closing date for a billing cycle given [billDay] and a reference [today].
     * If today's day-of-month <= billDay the closing falls this month; otherwise next month.
     */
    fun closingDate(billDay: Int, today: LocalDate = LocalDate.now()): LocalDate {
        val candidate = today.withDayOfMonth(billDay.coerceIn(1, today.lengthOfMonth()))
        return if (today.dayOfMonth <= billDay) candidate
        else {
            val next = today.plusMonths(1)
            next.withDayOfMonth(billDay.coerceIn(1, next.lengthOfMonth()))
        }
    }

    /** Days until closing date (≥ 0). */
    fun closesInDays(billDay: Int, today: LocalDate = LocalDate.now()): Int =
        ChronoUnit.DAYS.between(today, closingDate(billDay, today)).toInt()

    /**
     * Due label formatted as "dd mmm" in pt-BR lower-case, e.g. "08 jun".
     * The due date is the closing date (card statement cuts on billDay).
     * Some JDK locales append a trailing period to abbreviated month names; this is stripped.
     */
    fun dueLabel(billDay: Int, today: LocalDate = LocalDate.now()): String =
        closingDate(billDay, today)
            .format(dueLabelFormatter)
            .lowercase(Locale("pt", "BR"))
            .trimEnd('.')
}
