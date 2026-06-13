package com.resolveprogramming.pocketcounter.domain

import com.resolveprogramming.pocketcounter.domain.model.ReportMonth
import com.resolveprogramming.pocketcounter.domain.model.SummaryGroup
import com.resolveprogramming.pocketcounter.domain.model.seriesFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class ReportSeriesTest {

    private fun group(id: String, total: String) =
        SummaryGroup(id, "name-$id", 0L, BigDecimal(total), 0f, null, null, emptyList())

    private fun month(key: String, groups: List<SummaryGroup>) =
        ReportMonth(key, key.takeLast(2), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, groups, emptyList())

    @Test
    fun `series align per-month values, total, and last-vs-prev delta`() {
        val months = listOf(
            month("2026-01", listOf(group("a", "100"))),
            month("2026-02", listOf(group("a", "150"), group("b", "50"))),
        )
        val keys = listOf("2026-01", "2026-02")

        val series = seriesFrom(keys, months) { it.expGroups }

        val a = series.first { it.id == "a" }
        assertEquals(listOf(BigDecimal("100"), BigDecimal("150")), a.vals)
        assertEquals(BigDecimal("250"), a.total)
        assertEquals(0.5f, a.delta!!, 0.001f) // 100 → 150

        val b = series.first { it.id == "b" }
        assertEquals(listOf(BigDecimal.ZERO, BigDecimal("50")), b.vals)
        assertNull(b.delta) // prev (0) < 1 → "novo"
    }

    @Test
    fun `series sorted by total descending`() {
        val months = listOf(month("2026-01", listOf(group("small", "10"), group("big", "900"))))
        val series = seriesFrom(listOf("2026-01"), months) { it.expGroups }
        assertEquals(listOf("big", "small"), series.map { it.id })
    }
}
