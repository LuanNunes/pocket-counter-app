package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.api.TransactionApi
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionDto
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.serialization.json.Json

/**
 * Regression tests for [RetrofitTransactionRepository.toDto] ensuring that
 * [WizardDraft.name] ("Descrição") is forwarded to [TransactionDto.name] on every
 * write path (save expense, save income, update).
 */
class RetrofitTransactionRepositoryTest {

    private val api = mockk<TransactionApi>()
    private val repo = RetrofitTransactionRepository(api, Json { ignoreUnknownKeys = true })

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

    // -------------------------------------------------------------------------
    // datePurchase — the purchase date now travels in its own field
    // -------------------------------------------------------------------------

    @Test
    fun `save sends the purchase date in datePurchase`() = runTest {
        // The backend picks which invoice a credit purchase lands in from datePurchase; dateDue was
        // only ever an overload for it.
        val captured = slot<TransactionDto>()
        coEvery { api.addExpense(capture(captured)) } returns "tx-1"

        repo.save(expenseDraft("Mercado"))

        assertEquals(fixedDate.toString(), captured.captured.datePurchase)
    }

    @Test
    fun `save still sends dateDue alongside datePurchase`() = runTest {
        // dateDue stays populated: it is the field the month read maps back to HistoryItem.date, and
        // the backend still accepts it as the deprecated fallback.
        val captured = slot<TransactionDto>()
        coEvery { api.addExpense(capture(captured)) } returns "tx-1"

        repo.save(expenseDraft("Mercado"))

        assertEquals(fixedDate.toString(), captured.captured.dateDue)
    }

    @Test
    fun `update sends the purchase date in datePurchase`() = runTest {
        val captured = slot<TransactionDto>()
        coEvery { api.update("tx-99", capture(captured)) } returns "tx-99"

        repo.update("tx-99", expenseDraft("Mercado"))

        assertEquals(fixedDate.toString(), captured.captured.datePurchase)
    }

    @Test
    fun `save income also carries datePurchase`() = runTest {
        val captured = slot<TransactionDto>()
        coEvery { api.addIncome(capture(captured)) } returns "tx-2"

        repo.save(incomeDraft("Salário"))

        assertEquals(fixedDate.toString(), captured.captured.datePurchase)
    }

    // getMonth fetches incomes and expenses concurrently; these lock in the two contracts that the
    // parallelization must not break: incomes-before-expenses order, and failure propagation.

    @Test
    fun `getMonth preserves incomes-before-expenses order after the parallel fetch`() = runTest {
        coEvery { api.getIncomes(202606) } returns listOf(
            TransactionDto(id = "inc-1", transactionType = "INCOME", amount = BigDecimal("200.00")),
        )
        coEvery { api.getExpenses(202606) } returns listOf(
            TransactionDto(id = "exp-1", transactionType = "EXPENSE", amount = BigDecimal("100.00")),
        )

        val result = repo.getMonth("2026-06")

        assertTrue(result.isSuccess)
        assertEquals(listOf("inc-1", "exp-1"), result.getOrThrow().map { it.id })
    }

    @Test
    fun `getMonth surfaces a failure in either call as Result-failure`() = runTest {
        coEvery { api.getIncomes(202606) } returns emptyList()
        coEvery { api.getExpenses(202606) } throws RuntimeException("expenses endpoint down")

        val result = repo.getMonth("2026-06")

        assertTrue(result.isFailure)
    }

    // -------------------------------------------------------------------------
    // allowDuplicate + the 409 it answers
    // -------------------------------------------------------------------------

    private fun conflict(body: String) = HttpException(
        Response.error<Any>(409, body.toResponseBody("application/json".toMediaType())),
    )

    @Test
    fun `save omits allowDuplicate by default`() = runTest {
        val captured = slot<TransactionDto>()
        coEvery { api.addExpense(capture(captured)) } returns "tx-1"

        repo.save(expenseDraft("Mercado"))

        assertEquals(false, captured.captured.allowDuplicate)
    }

    @Test
    fun `save sends allowDuplicate once the user confirmed the repeat`() = runTest {
        val captured = slot<TransactionDto>()
        coEvery { api.addExpense(capture(captured)) } returns "tx-1"

        repo.save(expenseDraft("Mercado"), allowDuplicate = true)

        assertEquals(true, captured.captured.allowDuplicate)
    }

    @Test
    fun `a 409 becomes a duplicate carrying the existing id`() = runTest {
        val body = """{"code":"CONFLICT","message":"Já existe","details":["tx-existing"]}"""
        coEvery { api.addExpense(any()) } throws conflict(body)

        val failure = repo.save(expenseDraft("Mercado")).exceptionOrNull()

        assertEquals("tx-existing", (failure as DuplicateTransactionException).existingTransactionId)
    }

    @Test
    fun `a 409 with no details still reports a duplicate`() = runTest {
        coEvery { api.addExpense(any()) } throws conflict("""{"code":"CONFLICT","message":"Já existe"}""")

        val failure = repo.save(expenseDraft("Mercado")).exceptionOrNull()

        assertTrue(failure is DuplicateTransactionException)
        assertNull((failure as DuplicateTransactionException).existingTransactionId)
    }

    @Test
    fun `a duplicate carries a message the wizard toast can render`() = runTest {
        coEvery { api.addExpense(any()) } throws conflict("""{"code":"CONFLICT","message":"x"}""")

        assertEquals("Transação duplicada", repo.save(expenseDraft("Mercado")).exceptionOrNull()?.message)
    }
}
