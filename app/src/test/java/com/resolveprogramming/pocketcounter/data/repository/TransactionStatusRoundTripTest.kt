package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.api.TransactionApi
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionDto
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Payment status must survive the write→read round trip: what the form saved is what the ledger
 * (and therefore the edit sheet, which seeds from it) shows next. The fake API echoes the stored
 * body back the way the backend does — it copies `statusPayment` verbatim — so a status that flips
 * here is the app's own doing, not the server's.
 */
class TransactionStatusRoundTripTest {

    private val api = mockk<TransactionApi>()
    private val repo = RetrofitTransactionRepository(api)

    private val date = LocalDate.of(2026, 8, 20)
    private val ref = 202608

    private fun draft(status: PaymentStatus, method: PaymentMethod? = PaymentMethod.PIX) = WizardDraft(
        type = TransactionType.EXPENSE,
        amount = BigDecimal("42.50"),
        date = date,
        statusPayment = status,
        paymentMethod = method,
        cardId = "card-1".takeIf { method == PaymentMethod.CREDIT },
        name = "Mercado",
    )

    /** Saves the draft, echoes the stored row back through the month read, returns what the UI sees. */
    private suspend fun roundTrip(status: PaymentStatus, method: PaymentMethod? = PaymentMethod.PIX): PaymentStatus {
        val sent = slot<TransactionDto>()
        coEvery { api.addExpense(capture(sent)) } returns "tx-1"

        repo.save(draft(status, method)).getOrThrow()

        coEvery { api.getExpenses(ref) } returns listOf(sent.captured.copy(id = "tx-1"))
        coEvery { api.getIncomes(ref) } returns emptyList()

        val items = repo.getMonth("2026-08").getOrThrow()
        assertEquals(1, items.size)
        return items.single().statusPayment
    }

    @Test
    fun `an expense saved as paid reads back as paid`() = runTest {
        assertEquals(PaymentStatus.PAID, roundTrip(PaymentStatus.PAID))
    }

    @Test
    fun `a credit expense saved as paid reads back as paid`() = runTest {
        assertEquals(PaymentStatus.PAID, roundTrip(PaymentStatus.PAID, method = PaymentMethod.CREDIT))
    }

    @Test
    fun `an expense saved as pending reads back as pending`() = runTest {
        assertEquals(PaymentStatus.PENDING, roundTrip(PaymentStatus.PENDING))
    }

    @Test
    fun `saving as paid sends the payment date the backend requires`() = runTest {
        val sent = slot<TransactionDto>()
        coEvery { api.addExpense(capture(sent)) } returns "tx-1"

        repo.save(draft(PaymentStatus.PAID)).getOrThrow()

        assertEquals("PAID", sent.captured.statusPayment)
        // The backend rejects PAID with no datePaid ("Payment date is required when status is PAID").
        assertNotNull(sent.captured.datePaid)
    }
}
