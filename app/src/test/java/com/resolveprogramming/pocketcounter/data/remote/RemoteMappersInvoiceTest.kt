package com.resolveprogramming.pocketcounter.data.remote

import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toHistoryItem
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * `isInvoice` has to survive the trip to the domain: without it, "is this pending row an invoice?"
 * degrades to `CREDIT && cardId != null`, which an ordinary pending credit expense satisfies too.
 */
class RemoteMappersInvoiceTest {

    private fun expense(isInvoice: Boolean) = TransactionDto(
        id = "tx-1",
        transactionType = "EXPENSE",
        amount = BigDecimal("8886.92"),
        dateDue = "2026-08-10",
        paymentMethod = "CREDIT",
        cardId = "card-1",
        isInvoice = isInvoice,
    )

    @Test
    fun `an invoice container maps to isInvoice true`() {
        assertTrue(expense(isInvoice = true).toHistoryItem().isInvoice)
    }

    @Test
    fun `a plain credit expense on a card maps to isInvoice false`() {
        assertFalse(expense(isInvoice = false).toHistoryItem().isInvoice)
    }
}
