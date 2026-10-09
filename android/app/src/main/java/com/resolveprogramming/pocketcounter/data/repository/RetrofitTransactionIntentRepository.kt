package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.RawIntentMappers.toIntent
import com.resolveprogramming.pocketcounter.data.remote.api.TransactionApi
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionRawRequestDto
import com.resolveprogramming.pocketcounter.data.remote.isBadRequest
import com.resolveprogramming.pocketcounter.data.remote.isServerError
import com.resolveprogramming.pocketcounter.data.remote.isTooManyRequests
import com.resolveprogramming.pocketcounter.data.remote.responseHeader
import kotlin.coroutines.cancellation.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** The sentence is a request body and nothing else: it is never logged, stored or put in a message. */
@Singleton
class RetrofitTransactionIntentRepository @Inject constructor(
    private val api: TransactionApi,
) : TransactionIntentRepository {

    override suspend fun read(text: String, referenceDate: LocalDate): IntentReadResult {
        val body = TransactionRawRequestDto(text = text, referenceDate = referenceDate.toString())
        return try {
            IntentReadResult.Read(api.readRaw(body).toIntent(referenceDate))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            IntentReadResult.Failed(classify(e))
        }
    }

    private fun classify(e: Throwable): IntentReadFailure {
        if (e.isBadRequest()) return IntentReadFailure.NotUnderstood
        if (e.isTooManyRequests()) {
            return IntentReadFailure.RateLimited(e.responseHeader("Retry-After")?.toIntOrNull())
        }
        if (e.isServerError()) return IntentReadFailure.ServerError
        // A SocketTimeoutException is an IOException, so this order is load-bearing.
        if (e is SocketTimeoutException) return IntentReadFailure.Timeout
        if (e is IOException) return IntentReadFailure.Offline
        return IntentReadFailure.Unknown
    }
}
