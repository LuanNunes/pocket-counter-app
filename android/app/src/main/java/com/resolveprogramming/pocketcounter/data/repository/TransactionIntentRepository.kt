package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.domain.model.TransactionIntent
import java.time.LocalDate

/** Why a read did not produce an intent; each case is a distinct copy on screen. */
sealed interface IntentReadFailure {
    /** 400: the request was refused, so the sentence could not be read. */
    data object NotUnderstood : IntentReadFailure

    /** 429: the per-user rate limit, counted down from the server's `Retry-After` when it sent one. */
    data class RateLimited(val retryAfterSeconds: Int?) : IntentReadFailure

    /** 5xx: the backend is at fault, so the user retries rather than rewrites. */
    data object ServerError : IntentReadFailure

    /** The call never reached the server. */
    data object Offline : IntentReadFailure

    /** The call reached the server but no answer came back in time. */
    data object Timeout : IntentReadFailure

    data object Unknown : IntentReadFailure
}

sealed interface IntentReadResult {
    data class Read(val intent: TransactionIntent) : IntentReadResult

    data class Failed(val failure: IntentReadFailure) : IntentReadResult
}

/**
 * Reads one typed sentence into a [TransactionIntent]. Deliberately not `Result<T>`: the UI renders
 * a distinct copy per outcome, and classifying HTTP in the ViewModel is what the data layer exists
 * to prevent.
 */
interface TransactionIntentRepository {
    suspend fun read(text: String, referenceDate: LocalDate): IntentReadResult
}
