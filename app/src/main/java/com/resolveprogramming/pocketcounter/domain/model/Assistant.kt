package com.resolveprogramming.pocketcounter.domain.model

enum class AssistantRole { USER, ASSISTANT }

data class AssistantAnswer(
    val markdown: String,
    val elapsedMs: Long,
    val remaining: Int,
)

enum class AssistantMessageStatus { LOADING, OK, ERROR, LIMIT }

/** One Q&A pair: the user's question + its answer/error. */
data class AssistantMessage(
    val id: String,
    val question: String,
    val status: AssistantMessageStatus,
    val answer: AssistantAnswer? = null,
)

/** The five assistant outcomes, mapped from HTTP status in the repository (transport-agnostic above). */
sealed interface AssistantResult {
    data class Success(val answer: AssistantAnswer) : AssistantResult
    data class Validation(val message: String) : AssistantResult // 400 — inline, never enters the thread
    data object QuotaExhausted : AssistantResult                 // 429
    data object ServerError : AssistantResult                    // 500 / network
    data object Unavailable : AssistantResult                    // 503 — assistant disabled
}
