package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.api.AssistantApi
import com.resolveprogramming.pocketcounter.data.remote.dto.AssistantAskRequestDto
import com.resolveprogramming.pocketcounter.domain.model.AssistantAnswer
import com.resolveprogramming.pocketcounter.domain.model.AssistantResult
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetrofitAssistantRepository @Inject constructor(
    private val api: AssistantApi,
) : AssistantRepository {

    override suspend fun ask(question: String): AssistantResult {
        return try {
            val res = api.ask(AssistantAskRequestDto(question))
            AssistantResult.Success(
                AssistantAnswer(markdown = res.answer, elapsedMs = res.elapsedMs, remaining = res.remainingQuestions),
            )
        } catch (e: HttpException) {
            run {
                when (e.code()) {
                    400 -> return@run AssistantResult.Validation("Não consegui entender. Reformule a pergunta (máx. 500 caracteres).")
                    429 -> return@run AssistantResult.QuotaExhausted
                    503 -> return@run AssistantResult.Unavailable
                }
                // 401 (token refresh exhausted) and other 5xx fall here intentionally.
                AssistantResult.ServerError
            }
        } catch (e: IOException) {
            AssistantResult.ServerError
        }
    }
}
