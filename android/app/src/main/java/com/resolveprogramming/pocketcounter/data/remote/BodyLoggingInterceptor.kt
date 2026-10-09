package com.resolveprogramming.pocketcounter.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

/** Bodies the privacy policy promises are never stored: a typed sentence and a learned pattern. */
private val SENSITIVE_PATHS = listOf("/api/v1/transactions/raw", "/api/v1/classification-rules")

/**
 * Debug HTTP logging that drops the body for [SENSITIVE_PATHS]; `BASIC` still prints method, URL and
 * status. Two instances rather than one mutable level: the level is shared across threads.
 */
internal class BodyLoggingInterceptor(
    debug: Boolean,
    logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT,
) : Interceptor {

    private val full = logger(logger, HttpLoggingInterceptor.Level.BODY, debug)
    private val redacted = logger(logger, HttpLoggingInterceptor.Level.BASIC, debug)

    override fun intercept(chain: Interceptor.Chain): Response {
        val path = chain.request().url.encodedPath
        if (SENSITIVE_PATHS.any { path.startsWith(it) }) return redacted.intercept(chain)
        return full.intercept(chain)
    }

    private fun logger(
        sink: HttpLoggingInterceptor.Logger,
        level: HttpLoggingInterceptor.Level,
        debug: Boolean,
    ): HttpLoggingInterceptor =
        HttpLoggingInterceptor(sink).apply {
            this.level = level.takeIf { debug } ?: HttpLoggingInterceptor.Level.NONE
        }
}
