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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** A charge can only name its notification on the create call — nothing later can repair a missed link. */
class CardChargeNotificationLinkTest {

    private val api = mockk<TransactionApi>()
    private val repo = RetrofitTransactionRepository(api)

    /** Mirrors NetworkModule's instance: the wire format is part of the contract. */
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun charge(method: PaymentMethod? = PaymentMethod.CREDIT) = WizardDraft(
        type = TransactionType.EXPENSE,
        amount = BigDecimal("34.28"),
        date = LocalDate.of(2026, 8, 18),
        statusPayment = PaymentStatus.PAID,
        paymentMethod = method,
        cardId = "card-1".takeIf { method == PaymentMethod.CREDIT },
        name = "Tomadas Mac",
    )

    private suspend fun capturedExpense(block: suspend () -> Unit): TransactionDto {
        val sent = slot<TransactionDto>()
        coEvery { api.addExpense(capture(sent)) } returns "invoice-1"
        block()
        return sent.captured
    }

    @Test
    fun `a charge confirmed from a notification names it`() = runTest {
        val dto = capturedExpense { repo.save(charge(), notificationId = "notif-42").getOrThrow() }

        assertEquals("notif-42", dto.idNotification)
    }

    @Test
    fun `the link travels under the exact key the backend reads`() = runTest {
        val dto = capturedExpense { repo.save(charge(), notificationId = "notif-42").getOrThrow() }

        assertTrue(json.encodeToString(dto).contains(""""idNotification":"notif-42""""))
    }

    @Test
    fun `a manually entered charge claims no notification`() = runTest {
        val dto = capturedExpense { repo.save(charge()).getOrThrow() }

        assertNull(dto.idNotification)
        assertFalse(json.encodeToString(dto).contains("idNotification"))
    }

    @Test
    fun `a blank id is dropped instead of being sent as a uuid`() = runTest {
        // The backend parses this field as a UUID, so "" would turn a real expense into a 400.
        val dto = capturedExpense { repo.save(charge(), notificationId = "").getOrThrow() }

        assertNull(dto.idNotification)
    }

    @Test
    fun `an income confirmed from a notification names it too`() = runTest {
        val sent = slot<TransactionDto>()
        coEvery { api.addIncome(capture(sent)) } returns "tx-1"

        repo.save(charge(method = null).copy(type = TransactionType.INCOME), notificationId = "notif-7").getOrThrow()

        assertEquals("notif-7", sent.captured.idNotification)
    }

    @Test
    fun `editing a row never claims a link it cannot have`() = runTest {
        val sent = slot<TransactionDto>()
        coEvery { api.update("tx-9", capture(sent)) } returns "tx-9"

        repo.update("tx-9", charge()).getOrThrow()

        assertNull(sent.captured.idNotification)
    }
}
