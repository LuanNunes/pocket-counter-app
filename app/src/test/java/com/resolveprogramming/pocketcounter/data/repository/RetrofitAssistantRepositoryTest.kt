package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.api.AssistantApi
import com.resolveprogramming.pocketcounter.data.remote.dto.AssistantAskRequestDto
import com.resolveprogramming.pocketcounter.data.remote.dto.AssistantAskResponseDto
import com.resolveprogramming.pocketcounter.domain.model.AssistantResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Characterization tests for RetrofitAssistantRepository.
 *
 * Locks the HTTP-status → AssistantResult mapping:
 *   200 OK   → AssistantResult.Success  (answer/elapsedMs/remaining forwarded to domain)
 *   400      → AssistantResult.Validation (fixed BR message)
 *   429      → AssistantResult.QuotaExhausted
 *   503      → AssistantResult.Unavailable
 *   500/other→ AssistantResult.ServerError
 *   IOException → AssistantResult.ServerError
 */
class RetrofitAssistantRepositoryTest {

    private val api = mockk<AssistantApi>()
    private val repo = RetrofitAssistantRepository(api)

    // Helper: build a Retrofit HttpException with an arbitrary HTTP status code.
    private fun httpException(code: Int): HttpException =
        HttpException(Response.error<Any>(code, "".toResponseBody()))

    // -------------------------------------------------------------------------
    // Success path
    // -------------------------------------------------------------------------

    @Test
    fun `ask success maps dto fields to AssistantAnswer inside Success`() = runTest {
        coEvery { api.ask(AssistantAskRequestDto("Olá")) } returns
            AssistantAskResponseDto(answer = "**oi**", elapsedMs = 4200, remainingQuestions = 3)

        val result = repo.ask("Olá")

        assertTrue(result is AssistantResult.Success)
        val success = result as AssistantResult.Success
        assertEquals("**oi**", success.answer.markdown)
        assertEquals(4200L, success.answer.elapsedMs)
        assertEquals(3, success.answer.remaining)
    }

    @Test
    fun `ask success with zero remaining questions still returns Success`() = runTest {
        coEvery { api.ask(AssistantAskRequestDto("última")) } returns
            AssistantAskResponseDto(answer = "Resposta final.", elapsedMs = 1000, remainingQuestions = 0)

        val result = repo.ask("última")

        assertTrue(result is AssistantResult.Success)
        assertEquals(0, (result as AssistantResult.Success).answer.remaining)
    }

    // -------------------------------------------------------------------------
    // HTTP 400 → Validation
    // -------------------------------------------------------------------------

    @Test
    fun `ask HTTP 400 returns Validation with BR error message`() = runTest {
        coEvery { api.ask(any()) } throws httpException(400)

        val result = repo.ask("pergunta inválida")

        assertTrue(result is AssistantResult.Validation)
        val validation = result as AssistantResult.Validation
        assertEquals(
            "Não consegui entender. Reformule a pergunta (máx. 500 caracteres).",
            validation.message,
        )
    }

    // -------------------------------------------------------------------------
    // HTTP 429 → QuotaExhausted
    // -------------------------------------------------------------------------

    @Test
    fun `ask HTTP 429 returns QuotaExhausted`() = runTest {
        coEvery { api.ask(any()) } throws httpException(429)

        val result = repo.ask("pergunta qualquer")

        assertEquals(AssistantResult.QuotaExhausted, result)
    }

    // -------------------------------------------------------------------------
    // HTTP 503 → Unavailable
    // -------------------------------------------------------------------------

    @Test
    fun `ask HTTP 503 returns Unavailable`() = runTest {
        coEvery { api.ask(any()) } throws httpException(503)

        val result = repo.ask("pergunta qualquer")

        assertEquals(AssistantResult.Unavailable, result)
    }

    // -------------------------------------------------------------------------
    // HTTP 500 (and other unmatched codes) → ServerError
    // -------------------------------------------------------------------------

    @Test
    fun `ask HTTP 500 returns ServerError`() = runTest {
        coEvery { api.ask(any()) } throws httpException(500)

        val result = repo.ask("pergunta qualquer")

        assertEquals(AssistantResult.ServerError, result)
    }

    @Test
    fun `ask HTTP 401 returns ServerError (token refresh exhausted path)`() = runTest {
        coEvery { api.ask(any()) } throws httpException(401)

        val result = repo.ask("pergunta qualquer")

        assertEquals(AssistantResult.ServerError, result)
    }

    @Test
    fun `ask HTTP 502 returns ServerError`() = runTest {
        coEvery { api.ask(any()) } throws httpException(502)

        val result = repo.ask("pergunta qualquer")

        assertEquals(AssistantResult.ServerError, result)
    }

    // -------------------------------------------------------------------------
    // IOException → ServerError
    // -------------------------------------------------------------------------

    @Test
    fun `ask IOException returns ServerError`() = runTest {
        coEvery { api.ask(any()) } throws IOException("connection refused")

        val result = repo.ask("pergunta qualquer")

        assertEquals(AssistantResult.ServerError, result)
    }

    @Test
    fun `ask SocketTimeoutException (subtype of IOException) returns ServerError`() = runTest {
        coEvery { api.ask(any()) } throws java.net.SocketTimeoutException("timeout")

        val result = repo.ask("pergunta qualquer")

        assertEquals(AssistantResult.ServerError, result)
    }
}
