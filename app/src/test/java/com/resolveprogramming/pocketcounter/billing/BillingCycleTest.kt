package com.resolveprogramming.pocketcounter.billing

import com.resolveprogramming.pocketcounter.domain.billing.BillingCycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BillingCycleTest {

    @Test
    fun `closingDate falls this month when today is before billDay`() {
        // billDay = 8, today = 2026-05-05 → closes 2026-05-08
        val today = LocalDate.of(2026, 5, 5)
        assertEquals(LocalDate.of(2026, 5, 8), BillingCycle.closingDate(8, today))
    }

    @Test
    fun `closingDate falls this month when today equals billDay`() {
        val today = LocalDate.of(2026, 5, 8)
        assertEquals(LocalDate.of(2026, 5, 8), BillingCycle.closingDate(8, today))
    }

    @Test
    fun `closingDate falls next month when today is after billDay`() {
        // billDay = 8, today = 2026-05-30 → closes 2026-06-08
        val today = LocalDate.of(2026, 5, 30)
        assertEquals(LocalDate.of(2026, 6, 8), BillingCycle.closingDate(8, today))
    }

    @Test
    fun `closesInDays is zero when today equals billDay`() {
        val today = LocalDate.of(2026, 6, 8)
        assertEquals(0, BillingCycle.closesInDays(8, today))
    }

    @Test
    fun `closesInDays is positive when today is before billDay`() {
        val today = LocalDate.of(2026, 5, 30)
        // closes 2026-06-08 → 9 days away
        assertEquals(9, BillingCycle.closesInDays(8, today))
    }

    @Test
    fun `dueLabel formats correctly in pt-BR lowercase`() {
        val today = LocalDate.of(2026, 5, 30)
        // closes 2026-06-08 → "08 jun"
        assertEquals("08 jun", BillingCycle.dueLabel(8, today))
    }

    @Test
    fun `dueLabel january formats correctly`() {
        val today = LocalDate.of(2026, 1, 5)
        // closes 2026-01-10 → "10 jan"
        assertEquals("10 jan", BillingCycle.dueLabel(10, today))
    }

    @Test
    fun `closingDate handles end-of-month billDay clamping`() {
        // billDay = 31, February 2026 → clamped to Feb 28
        val today = LocalDate.of(2026, 2, 1)
        val result = BillingCycle.closingDate(31, today)
        assertTrue(result.dayOfMonth <= today.lengthOfMonth().coerceAtLeast(28))
    }
}
