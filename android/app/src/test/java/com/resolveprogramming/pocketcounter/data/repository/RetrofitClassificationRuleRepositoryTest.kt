package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.ApiException
import com.resolveprogramming.pocketcounter.data.remote.api.ClassificationRuleApi
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassificationRuleDto
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class RetrofitClassificationRuleRepositoryTest {

    private val api = mockk<ClassificationRuleApi>()
    private val repo = RetrofitClassificationRuleRepository(api, Json { ignoreUnknownKeys = true })

    private fun httpError(status: Int, body: String = "") = HttpException(
        Response.error<ClassificationRuleDto>(status, body.toResponseBody("application/json".toMediaType())),
    )

    private val saved = ClassificationRuleDto(id = "rule-1", pattern = "Ifood", idTag = "tag-1", action = "SUGGEST")

    @Test
    fun create_posts_andReportsSaved() = runTest {
        coEvery { api.create(any()) } returns saved

        val result = repo.create(ClassificationRule.suggest("Ifood", "tag-1"))

        assertEquals(RuleWriteOutcome.Saved, result.getOrNull())
        val body = slot<ClassificationRuleDto>()
        coVerify { api.create(capture(body)) }
        assertEquals("Ifood", body.captured.pattern)
        assertEquals("tag-1", body.captured.idTag)
    }

    @Test
    fun create_409_isDuplicateNotFailure() = runTest {
        coEvery { api.create(any()) } throws httpError(409, """{"code":"CONFLICT","message":"x"}""")

        val result = repo.create(ClassificationRule.suggest("Ifood", "tag-1"))

        assertTrue(result.isSuccess)
        assertEquals(RuleWriteOutcome.Duplicate, result.getOrNull())
    }

    @Test
    fun create_422_isRejectedCarryingTheServersLocalizedMessage() = runTest {
        val message = "O padrão pode ter no máximo 500 caracteres."
        coEvery { api.create(any()) } throws
            httpError(422, """{"code":"UNPROCESSABLE_ENTITY","message":"$message"}""")

        val result = repo.create(ClassificationRule.suggest("Ifood", "tag-1"))

        assertTrue(result.isSuccess)
        assertEquals(RuleWriteOutcome.Rejected(message), result.getOrNull())
    }

    @Test
    fun create_422_withoutAReadableBody_isRejectedWithoutAMessage() = runTest {
        coEvery { api.create(any()) } throws httpError(422)

        assertEquals(
            RuleWriteOutcome.Rejected(null),
            repo.create(ClassificationRule.suggest("Ifood", "tag-1")).getOrNull(),
        )
    }

    @Test
    fun create_500_isFailureKeepingTheStatusAndNotLeakingHttpException() = runTest {
        coEvery { api.create(any()) } throws httpError(500)

        val result = repo.create(ClassificationRule.suggest("Ifood", "tag-1"))

        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull() is HttpException)
        assertEquals(500, (result.exceptionOrNull() as ApiException).status)
    }

    @Test
    fun create_ioException_isFailure() = runTest {
        coEvery { api.create(any()) } throws IOException("offline")

        val result = repo.create(ClassificationRule.suggest("Ifood", "tag-1"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun create_blockedRule_failsWithoutCallingTheApi() = runTest {
        val result = repo.create(ClassificationRule.suggest("  *", "tag-1"))

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { api.create(any()) }
    }

    @Test
    fun update_putsUnderItsId_andReportsSaved() = runTest {
        coEvery { api.update(any(), any()) } returns saved

        val result = repo.update(ClassificationRule.suggest("Ifood", "tag-1").copy(id = "rule-1"))

        assertEquals(RuleWriteOutcome.Saved, result.getOrNull())
        coVerify { api.update("rule-1", any()) }
    }

    @Test
    fun update_409_isDuplicateNotFailure() = runTest {
        coEvery { api.update(any(), any()) } throws httpError(409)

        val result = repo.update(ClassificationRule.suggest("Ifood", "tag-1").copy(id = "rule-1"))

        assertEquals(RuleWriteOutcome.Duplicate, result.getOrNull())
    }

    @Test
    fun update_500_isFailure() = runTest {
        coEvery { api.update(any(), any()) } throws httpError(500)

        val result = repo.update(ClassificationRule.suggest("Ifood", "tag-1").copy(id = "rule-1"))

        assertTrue(result.isFailure)
    }

    @Test
    fun update_withoutId_failsWithoutCallingTheApi() = runTest {
        val result = repo.update(ClassificationRule.suggest("Ifood", "tag-1"))

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { api.update(any(), any()) }
    }

    @Test
    fun getAll_httpFailure_doesNotLeakHttpException() = runTest {
        coEvery { api.getAll() } throws httpError(500)

        val result = repo.getAll()

        assertTrue(result.isFailure)
        assertEquals(500, (result.exceptionOrNull() as ApiException).status)
    }

    @Test
    fun delete_httpFailure_doesNotLeakHttpException() = runTest {
        coEvery { api.delete(any()) } throws httpError(404)

        val result = repo.delete("rule-1")

        assertTrue(result.isFailure)
        assertEquals(404, (result.exceptionOrNull() as ApiException).status)
    }
}
