package com.resolveprogramming.pocketcounter.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

private const val HEADER = "Accept-Language"
private const val LANGUAGE = "pt-BR"

/**
 * Every localized `message` the app renders comes from the backend, whose locale filter defaults to
 * English when the request carries no language.
 */
@Singleton
class AcceptLanguageInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(HEADER) != null) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(HEADER, LANGUAGE).build())
    }
}
