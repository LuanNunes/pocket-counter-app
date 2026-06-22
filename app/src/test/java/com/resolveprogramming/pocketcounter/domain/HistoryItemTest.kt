package com.resolveprogramming.pocketcounter.domain

import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class HistoryItemTest {

    private fun makeItem(
        id: String = "item-1",
        seriesId: String? = null,
    ) = HistoryItem(
        id = id,
        date = LocalDate.of(2026, 6, 4),
        idSource = "src-1",
        idPaymentSource = "pay-1",
        amount = BigDecimal("100.00"),
        type = TransactionType.EXPENSE,
        tagIds = null,
        statusPayment = PaymentStatus.PAID,
        seriesId = seriesId,
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
}
