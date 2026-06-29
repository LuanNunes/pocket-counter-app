package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.api.SeriesApi
import com.resolveprogramming.pocketcounter.data.remote.dto.CarryForwardRequest
import com.resolveprogramming.pocketcounter.data.remote.dto.CarryForwardResultDto
import com.resolveprogramming.pocketcounter.data.remote.dto.CreateRecurringSeriesRequest
import com.resolveprogramming.pocketcounter.data.remote.dto.RecurringSeriesDto
import com.resolveprogramming.pocketcounter.domain.model.CarryForwardResult
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Assumed SeriesApi Retrofit interface (implementer must match exactly):
 *
 *   interface SeriesApi {
 *       @GET("api/v1/recurring-series")
 *       suspend fun getAll(): List<RecurringSeriesDto>
 *
 *       @POST("api/v1/recurring-series")
 *       suspend fun create(@Body body: CreateRecurringSeriesRequest): RecurringSeriesDto
 *
 *       @PUT("api/v1/recurring-series/{id}/rename")
 *       suspend fun rename(@Path("id") id: String, @Body body: RenameSeriesRequest): Unit
 *
 *       @DELETE("api/v1/recurring-series/{id}")
 *       suspend fun delete(@Path("id") id: String)
 *
 *       @PUT("api/v1/recurring-series/{id}/tags")
 *       suspend fun setTags(@Path("id") id: String, @Body body: SetSeriesTagsRequest): Unit
 *
 *       @POST("api/v1/recurring-series/{seriesId}/transactions/{transactionId}")
 *       suspend fun linkTransaction(
 *           @Path("seriesId") seriesId: String,
 *           @Path("transactionId") transactionId: String,
 *           @Body body: LinkTransactionRequest,
 *       ): Unit
 *
 *       @DELETE("api/v1/recurring-series/{seriesId}/transactions/{transactionId}")
 *       suspend fun unlinkTransaction(
 *           @Path("seriesId") seriesId: String,
 *           @Path("transactionId") transactionId: String,
 *       )
 *
 *       @POST("api/v1/recurring-series/carry-forward/{targetRefYearMonth}")
 *       suspend fun carryForward(
 *           @Path("targetRefYearMonth") targetRefYearMonth: Int,
 *           @Body body: CarryForwardRequest,
 *       ): CarryForwardResultDto
 *   }
 *
 * Assumed DTOs (all @Serializable, in data/remote/dto/):
 *
 *   @Serializable
 *   data class RecurringSeriesDto(
 *       val id: String,
 *       val name: String,
 *       val transactionType: String,        // "INCOME" | "EXPENSE"
 *       val recurrenceDay: Int? = null,
 *       val tagIds: List<String> = emptyList(),
 *   )
 *
 *   @Serializable
 *   data class CreateRecurringSeriesRequest(
 *       val name: String,
 *       val transactionType: String,        // TransactionType.name ("INCOME" | "EXPENSE")
 *       val recurrenceDay: Int?,
 *   )
 *
 *   @Serializable
 *   data class CarryForwardRequest(
 *       val sourceRefYearMonth: Int,
 *       val onlyRecurring: Boolean,
 *   )
 *
 *   @Serializable
 *   data class CarryForwardResultDto(
 *       val createdCount: Int,
 *       val skippedCount: Int,
 *   )
 *
 *   @Serializable
 *   data class RenameSeriesRequest(val name: String)
 *
 *   @Serializable
 *   data class SetSeriesTagsRequest(val tagIds: List<String>)
 *
 *   @Serializable
 *   data class LinkTransactionRequest(val includePrevious: Boolean)
 */
class RetrofitSeriesRepositoryTest {

    private val api: SeriesApi = mockk()

    private val repo = RetrofitSeriesRepository(api)

    // -------------------------------------------------------------------------
    // carryForward
    // -------------------------------------------------------------------------

    @Test
    fun `carryForward maps months to refYearMonth Ints and request body`() = runTest {
        coEvery {
            api.carryForward(202607, CarryForwardRequest(sourceRefYearMonth = 202606, onlyRecurring = true))
        } returns CarryForwardResultDto(createdCount = 3, skippedCount = 1)

        val result = repo.carryForward(
            targetRefYearMonth = 202607,
            sourceRefYearMonth = 202606,
            onlyRecurring = true,
        )

        assertTrue(result.isSuccess)
        val cf = result.getOrThrow()
        assertEquals(3, cf.createdCount)
        assertEquals(1, cf.skippedCount)
        coVerify(exactly = 1) {
            api.carryForward(202607, CarryForwardRequest(sourceRefYearMonth = 202606, onlyRecurring = true))
        }
    }

    @Test
    fun `carryForward maps response dto to CarryForwardResult domain`() = runTest {
        coEvery { api.carryForward(any(), any()) } returns
            CarryForwardResultDto(createdCount = 5, skippedCount = 2)

        val result = repo.carryForward(
            targetRefYearMonth = 202606,
            sourceRefYearMonth = 202605,
            onlyRecurring = false,
        )

        val domain = result.getOrThrow()
        assertEquals(CarryForwardResult(createdCount = 5, skippedCount = 2), domain)
    }

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    @Test
    fun `create sends transactionType by NAME and maps response`() = runTest {
        coEvery {
            api.create(CreateRecurringSeriesRequest("Aluguel", "EXPENSE", 10))
        } returns RecurringSeriesDto(
            id = "s-123",
            name = "Aluguel",
            transactionType = "EXPENSE",
            recurrenceDay = 10,
            tagIds = emptyList(),
        )

        val result = repo.create(name = "Aluguel", type = TransactionType.EXPENSE, recurrenceDay = 10)

        assertTrue(result.isSuccess)
        val series = result.getOrThrow()
        assertEquals("s-123", series.id)
        assertEquals("Aluguel", series.name)
        assertEquals(TransactionType.EXPENSE, series.type)
        assertEquals(10, series.recurrenceDay)
        coVerify(exactly = 1) {
            api.create(CreateRecurringSeriesRequest("Aluguel", "EXPENSE", 10))
        }
    }

    @Test
    fun `create sends INCOME type name for INCOME series`() = runTest {
        coEvery {
            api.create(CreateRecurringSeriesRequest("Salário", "INCOME", null))
        } returns RecurringSeriesDto(
            id = "s-456",
            name = "Salário",
            transactionType = "INCOME",
            recurrenceDay = null,
        )

        val result = repo.create(name = "Salário", type = TransactionType.INCOME, recurrenceDay = null)

        assertTrue(result.isSuccess)
        assertEquals(TransactionType.INCOME, result.getOrThrow().type)
        coVerify(exactly = 1) {
            api.create(CreateRecurringSeriesRequest("Salário", "INCOME", null))
        }
    }

    // -------------------------------------------------------------------------
    // getAll
    // -------------------------------------------------------------------------

    @Test
    fun `getAll maps RecurringSeriesDto list to domain`() = runTest {
        coEvery { api.getAll() } returns listOf(
            RecurringSeriesDto(
                id = "s-1",
                name = "Netflix",
                transactionType = "EXPENSE",
                recurrenceDay = 15,
                tagIds = emptyList(),
            ),
            RecurringSeriesDto(
                id = "s-2",
                name = "Freelance",
                transactionType = "INCOME",
                recurrenceDay = null,
                tagIds = emptyList(),
            ),
        )

        val result = repo.getAll()

        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(2, list.size)

        val expense = list[0]
        assertEquals("s-1", expense.id)
        assertEquals("Netflix", expense.name)
        assertEquals(TransactionType.EXPENSE, expense.type)
        assertEquals(15, expense.recurrenceDay)
        assertEquals(emptyList<String>(), expense.tagIds)

        val income = list[1]
        assertEquals("s-2", income.id)
        assertEquals(TransactionType.INCOME, income.type)
        assertEquals(null, income.recurrenceDay)
    }

    @Test
    fun `getAll returns empty list when api returns empty`() = runTest {
        coEvery { api.getAll() } returns emptyList()

        val result = repo.getAll()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `getAll returns failure when api throws`() = runTest {
        coEvery { api.getAll() } throws RuntimeException("network error")

        val result = repo.getAll()

        assertTrue(result.isFailure)
    }

    // -------------------------------------------------------------------------
    // getAll caching
    // -------------------------------------------------------------------------

    @Test
    fun `getAll caches the series list so a second read hits the api once`() = runTest {
        coEvery { api.getAll() } returns emptyList()

        repo.getAll()
        repo.getAll()

        coVerify(exactly = 1) { api.getAll() }
    }

    @Test
    fun `create invalidates the series cache so the next read refetches`() = runTest {
        coEvery { api.getAll() } returns emptyList()
        coEvery { api.create(any()) } returns RecurringSeriesDto(
            id = "s-1",
            name = "Aluguel",
            transactionType = "EXPENSE",
            recurrenceDay = null,
        )

        repo.getAll()
        repo.create(name = "Aluguel", type = TransactionType.EXPENSE, recurrenceDay = null)
        repo.getAll()

        coVerify(exactly = 2) { api.getAll() }
    }

    @Test
    fun `getAll does not cache a failed read so the next call retries the api`() = runTest {
        coEvery { api.getAll() } throws RuntimeException("boom") andThen emptyList()

        val first = repo.getAll()
        val second = repo.getAll()

        assertTrue(first.isFailure)
        assertTrue(second.isSuccess)
        coVerify(exactly = 2) { api.getAll() }
    }
}
