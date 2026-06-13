package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.domain.model.AssistantResult

/**
 * Asks the AI assistant one question. Returns an [AssistantResult] sealed type rather than the
 * usual `Result<T>` because the five outcomes (success / 400 / 429 / 500 / 503) are
 * domain-meaningful UI states, not generic failures — the repo maps HTTP status to them so
 * ViewModels stay transport-agnostic.
 */
interface AssistantRepository {
    suspend fun ask(question: String): AssistantResult
}
