package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.api.TransactionApi
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionDto
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Regression tests for [RetrofitTransactionRepository.toDto] ensuring that
 * [WizardDraft.name] ("Descrição") is forwarded to [TransactionDto.name] on every
 * write path (save expense, save income, update).
 */
class RetrofitTransactionRepositoryTest {

    private val api = mockk<TransactionApi>()
    private val repo = RetrofitTransactionRepository(api)

    private val fixedDate = LocalDate.of(2026, 6, 26)

    private fun expenseDraft(name: String?) = WizardDraft(
        type = TransactionType.EXPENSE,
        amount = BigDecimal("100.00"),
        date = fixedDate,
        statusPayment = PaymentStatus.PAID,
        name = name,
    )

    private fun incomeDraft(name: String?) = WizardDraft(
        type = TransactionType.INCOME,
        amount = BigDecimal("200.00"),
        date = fixedDate,
        statusPayment = PaymentStatus.PAID,
        name = name,
    )

    @Test
    fun `save expense sends name in dto`() = runTest {
        val captured = slot<TransactionDto>()
        coEvery { api.addExpense(capture(captured)) } returns "tx-1"

        val result = repo.save(expenseDraft("Aluguel"))

        assertTrue(result.isSuccess)
        assertEquals("Aluguel", captured.captured.name)
        assertEquals("EXPENSE", captured.captured.transactionType)
    }

    @Test
    fun `save income routes to addIncome with name`() = runTest {
        val captured = slot<TransactionDto>()
        coEvery { api.addIncome(capture(captured)) } returns "tx-2"

        val result = repo.save(incomeDraft("Salário"))

        assertTrue(result.isSuccess)
        assertEquals("Salário", captured.captured.name)
    }

    @Test
    fun `update sends name in dto`() = runTest {
        val captured = slot<TransactionDto>()
        coEvery { api.update("tx-99", capture(captured)) } returns "tx-99"

        val result = repo.update("tx-99", expenseDraft("Mercado"))

        assertTrue(result.isSuccess)
        assertEquals("Mercado", captured.captured.name)
    }

    @Test
    fun `save with null name sends null name`() = runTest {
        val captured = slot<TransactionDto>()
        coEvery { api.addExpense(capture(captured)) } returns "tx-3"

        val result = repo.save(expenseDraft(null))

        assertTrue(result.isSuccess)
        assertNull(captured.captured.name)
    }
}
