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

    private fun invoice(amount: String, isInvoice: Boolean = true) = HistoryItem(
        id = "inv-1",
        date = LocalDate.of(2026, 6, 4),
        amount = BigDecimal(amount),
        type = TransactionType.EXPENSE,
        tagIds = null,
        statusPayment = PaymentStatus.PENDING,
        isInvoice = isInvoice,
    )

    @Test
    fun `matchesInvoiceAmount is true for an invoice with the same absolute amount`() {
        assertTrue(invoice("-8866.19").matchesInvoiceAmount(BigDecimal("8866.19")))
    }

    @Test
    fun `matchesInvoiceAmount is false for a non-invoice row even with the same amount`() {
        assertFalse(invoice("-8866.19", isInvoice = false).matchesInvoiceAmount(BigDecimal("8866.19")))
    }

    @Test
    fun `matchesInvoiceAmount is false for a different amount`() {
        assertFalse(invoice("-8866.19").matchesInvoiceAmount(BigDecimal("50.00")))
    }

    @Test
    fun `matchesInvoiceAmount is false when the notified amount is null`() {
        assertFalse(invoice("-8866.19").matchesInvoiceAmount(null))
    }

    @Test
    fun `matchesInvoiceAmount ignores scale differences`() {
        assertTrue(invoice("-8886.9").matchesInvoiceAmount(BigDecimal("8886.90")))
    }
}
