package com.resolveprogramming.pocketcounter.domain

import com.resolveprogramming.pocketcounter.domain.model.ReportPeriod
import com.resolveprogramming.pocketcounter.domain.model.reportPeriodMonths
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class ReportPeriodMonthsTest {

    @Test
    fun `MES is just the anchor`() {
        assertEquals(listOf(YearMonth.of(2026, 5)), reportPeriodMonths(ReportPeriod.MES, YearMonth.of(2026, 5)))
    }

    @Test
    fun `TRIMESTRE is the calendar quarter containing the anchor`() {
        assertEquals(
            listOf(YearMonth.of(2026, 4), YearMonth.of(2026, 5), YearMonth.of(2026, 6)),
            reportPeriodMonths(ReportPeriod.TRIMESTRE, YearMonth.of(2026, 5)),
        )
        assertEquals(
            listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3)),
            reportPeriodMonths(ReportPeriod.TRIMESTRE, YearMonth.of(2026, 1)),
        )
        assertEquals(
            listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11), YearMonth.of(2026, 12)),
            reportPeriodMonths(ReportPeriod.TRIMESTRE, YearMonth.of(2026, 12)),
        )
    }

    @Test
    fun `ANO is Jan through Dec of the anchor year`() {
        val months = reportPeriodMonths(ReportPeriod.ANO, YearMonth.of(2026, 7))
        assertEquals(12, months.size)
        assertEquals(YearMonth.of(2026, 1), months.first())
        assertEquals(YearMonth.of(2026, 12), months.last())
    }
}
