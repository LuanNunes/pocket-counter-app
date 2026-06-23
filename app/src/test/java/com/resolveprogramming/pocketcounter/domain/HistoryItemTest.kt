package com.resolveprogramming.pocketcounter.domain

import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class HistoryItemTest {

    private fun makeItem(
        id: String = "item-1",
        seriesId: String? = null,
        name: String? = null,
        description: String? = null,
    ) = HistoryItem(
        id = id,
        date = LocalDate.of(2026, 6, 4),
        amount = BigDecimal("100.00"),
        type = TransactionType.EXPENSE,
        tagIds = null,
        statusPayment = PaymentStatus.PAID,
        seriesId = seriesId,
        name = name,
        description = description,
    )

    @Test
    fun `isFixo is false when seriesId is null`() {
        val item = makeItem(seriesId = null)

        assertFalse(item.isFixo)
    }

    @Test
    fun `isFixo is true when seriesId is set`() {
        val item = makeItem(seriesId = "s-123")

        assertTrue(item.isFixo)
    }

    @Test
    fun `displayTitle returns name when name is present`() {
        val item = makeItem(name = "Supermercado Extra", description = "Compras do mês")

        assertEquals("Supermercado Extra", item.displayTitle())
    }

    @Test
    fun `displayTitle falls back to description when name is null`() {
        val item = makeItem(name = null, description = "Compras do mês")

        assertEquals("Compras do mês", item.displayTitle())
    }

    @Test
    fun `displayTitle falls back to description when name is blank`() {
        val item = makeItem(name = "   ", description = "Compras do mês")

        assertEquals("Compras do mês", item.displayTitle())
    }

    @Test
    fun `displayTitle returns dash when both name and description are null`() {
        val item = makeItem(name = null, description = null)

        assertEquals("—", item.displayTitle())
    }

    @Test
    fun `displayTitle returns dash when both name and description are blank`() {
        val item = makeItem(name = "", description = "   ")

        assertEquals("—", item.displayTitle())
    }
}
