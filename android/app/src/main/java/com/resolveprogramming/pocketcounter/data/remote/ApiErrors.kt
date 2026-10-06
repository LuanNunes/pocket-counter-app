package com.resolveprogramming.pocketcounter.data.remote

import com.resolveprogramming.pocketcounter.data.remote.dto.ErrorResponse
import kotlinx.serialization.json.Json
import retrofit2.HttpException

private const val CONFLICT = 409
private const val UNPROCESSABLE_ENTITY = 422

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

/** The status (409) is the contract; the body's `code` is not required, so an unreadable body still counts. */
internal fun Throwable.isConflict(): Boolean = httpStatus() == CONFLICT

/** A 422: the backend refused the body and its [errorMessage] says why. */
internal fun Throwable.isUnprocessable(): Boolean = httpStatus() == UNPROCESSABLE_ENTITY

/** What the domain sees of a failed HTTP call: the status, never the Retrofit type. */
internal class ApiException(val status: Int, cause: Throwable) : RuntimeException(cause.message, cause)

internal fun Throwable.withoutHttpException(): Throwable {
    val status = httpStatus() ?: return this
    return ApiException(status, this)
}
