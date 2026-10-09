package com.resolveprogramming.pocketcounter.data.remote

import com.resolveprogramming.pocketcounter.data.remote.dto.ErrorResponse
import kotlinx.serialization.json.Json
import retrofit2.HttpException

private const val BAD_REQUEST = 400
private const val CONFLICT = 409
private const val UNPROCESSABLE_ENTITY = 422
private const val TOO_MANY_REQUESTS = 429
private val SERVER_ERRORS = 500..599

/**
 * The only place that knows a failed call is a Retrofit [HttpException]. Repositories translate
 * through these helpers so the exception never leaks past the data layer.
 */
internal fun Throwable.httpStatus(): Int? = (this as? HttpException)?.code()

/** The backend's `message` for an HTTP error body, already localized, or null when there is none to read. */
internal fun Throwable.errorMessage(json: Json): String? {
    val body = (this as? HttpException)?.response()?.errorBody()?.string() ?: return null
    return runCatching { json.decodeFromString<ErrorResponse>(body).message }.getOrNull()
}

/** The backend's `details` for an HTTP error body; empty when there is none to read. */
internal fun Throwable.errorDetails(json: Json): List<String> {
    val body = (this as? HttpException)?.response()?.errorBody()?.string() ?: return emptyList()
    return runCatching { json.decodeFromString<ErrorResponse>(body).details }.getOrDefault(emptyList())
}

/** A 400: the request itself was malformed — for /transactions/raw, the sentence was not read. */
internal fun Throwable.isBadRequest(): Boolean = httpStatus() == BAD_REQUEST

/** The status (409) is the contract; the body's `code` is not required, so an unreadable body still counts. */
internal fun Throwable.isConflict(): Boolean = httpStatus() == CONFLICT

/** A 429: the per-user rate limit, whose `Retry-After` says for how long. */
internal fun Throwable.isTooManyRequests(): Boolean = httpStatus() == TOO_MANY_REQUESTS

/** A 422: the backend refused the body and its [errorMessage] says why. */
internal fun Throwable.isUnprocessable(): Boolean = httpStatus() == UNPROCESSABLE_ENTITY

/** A 5xx: the backend is at fault, so the user is told to try again rather than to rewrite. */
internal fun Throwable.isServerError(): Boolean {
    val status = httpStatus() ?: return false
    return status in SERVER_ERRORS
}

/** A response header of a failed call, e.g. `Retry-After`; null when there is no response. */
internal fun Throwable.responseHeader(name: String): String? =
    (this as? HttpException)?.response()?.headers()?.get(name)

/** What the domain sees of a failed HTTP call: the status, never the Retrofit type. */
internal class ApiException(val status: Int, cause: Throwable) : RuntimeException(cause.message, cause)

internal fun Throwable.withoutHttpException(): Throwable {
    val status = httpStatus() ?: return this
    return ApiException(status, this)
}
