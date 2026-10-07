package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.api.TransactionApi
import com.resolveprogramming.pocketcounter.data.remote.dto.RawReadingDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionRawRequestDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionRawResponseDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.math.BigDecimal
import java.net.SocketTimeoutException
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

class RetrofitTransactionIntentRepositoryTest {

    private val api = mockk<TransactionApi>()
    private val repo = RetrofitTransactionIntentRepository(api)
    private val referenceDate = LocalDate.of(2026, 6, 4)

    private fun httpError(status: Int) = HttpException(
        Response.error<Any>(status, "".toResponseBody("application/json".toMediaType())),
    )

    private fun rateLimited(retryAfter: String?): HttpException {
        val raw = okhttp3.Response.Builder()
            .code(429)
            .message("error")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
            .apply { retryAfter?.let { header("Retry-After", it) } }
            .build()
        return HttpException(Response.error<Any>("".toResponseBody("application/json".toMediaType()), raw))
    }

    private fun IntentReadResult.failure(): IntentReadFailure = (this as IntentReadResult.Failed).failure

    @Test
    fun read_postsTheSentenceWithTheClientsOwnDate_andReturnsTheReading() = runTest {
        coEvery { api.readRaw(any()) } returns TransactionRawResponseDto(
            reading = RawReadingDto(date = "2026-06-04", amount = BigDecimal("12.34")),
        )

        val result = repo.read("uma frase", referenceDate)

        val body = slot<TransactionRawRequestDto>()
        coVerify { api.readRaw(capture(body)) }
        assertEquals("uma frase", body.captured.text)
        assertEquals("2026-06-04", body.captured.referenceDate)
        assertEquals(BigDecimal("12.34"), (result as IntentReadResult.Read).intent.reading.amount)
    }

    @Test
    fun read_400_isNotUnderstood() = runTest {
        coEvery { api.readRaw(any()) } throws httpError(400)

        assertEquals(IntentReadFailure.NotUnderstood, repo.read("uma frase", referenceDate).failure())
    }

    @Test
    fun read_429_isRateLimitedForTheSecondsTheServerAsksFor() = runTest {
        coEvery { api.readRaw(any()) } throws rateLimited(retryAfter = "30")

        assertEquals(
            IntentReadFailure.RateLimited(retryAfterSeconds = 30),
            repo.read("uma frase", referenceDate).failure(),
        )
    }

    @Test
    fun read_429_withoutRetryAfter_isRateLimitedWithoutASecondsCount() = runTest {
        coEvery { api.readRaw(any()) } throws rateLimited(retryAfter = null)

        assertEquals(
            IntentReadFailure.RateLimited(retryAfterSeconds = null),
            repo.read("uma frase", referenceDate).failure(),
        )
    }

    @Test
    fun read_500_isServerError() = runTest {
        coEvery { api.readRaw(any()) } throws httpError(500)

        assertEquals(IntentReadFailure.ServerError, repo.read("uma frase", referenceDate).failure())
    }

    @Test
    fun read_ioException_isOffline() = runTest {
        coEvery { api.readRaw(any()) } throws IOException("no route to host")

        assertEquals(IntentReadFailure.Offline, repo.read("uma frase", referenceDate).failure())
    }

    /** A SocketTimeoutException IS an IOException, so the timeout check has to come first. */
    @Test
    fun read_socketTimeout_isTimeoutNotOffline() = runTest {
        coEvery { api.readRaw(any()) } throws SocketTimeoutException("timeout")

        assertEquals(IntentReadFailure.Timeout, repo.read("uma frase", referenceDate).failure())
    }

    /** Only a 400 means "not understood"; nothing else may borrow that copy. */
    @Test
    fun read_404_isUnknown() = runTest {
        coEvery { api.readRaw(any()) } throws httpError(404)

        assertEquals(IntentReadFailure.Unknown, repo.read("uma frase", referenceDate).failure())
    }

    /** A cancelled sheet is not a failed read: swallowing this breaks structured concurrency. */
    @Test(expected = CancellationException::class)
    fun read_cancellation_isRethrown() = runTest {
        coEvery { api.readRaw(any()) } throws CancellationException("cancelled")

        repo.read("uma frase", referenceDate)
    }
}
