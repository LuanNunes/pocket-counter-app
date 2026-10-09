package com.resolveprogramming.pocketcounter.data.remote

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Without this header the backend's locale filter answers in English, so every server `message` the
 * app renders — the duplicate conflict, a refused rule — would reach a pt-BR screen in English.
 */
class AcceptLanguageInterceptorTest {

    private val interceptor = AcceptLanguageInterceptor()

    private fun sent(request: Request): Request {
        val chain = mockk<Interceptor.Chain>()
        val captured = slot<Request>()
        every { chain.request() } returns request
        every { chain.proceed(capture(captured)) } answers {
            Response.Builder()
                .request(captured.captured)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
        }
        interceptor.intercept(chain)
        return captured.captured
    }

    private fun request(): Request.Builder = Request.Builder().url("http://localhost/api/v1/transactions/raw")

    @Test
    fun addsPtBrToARequestThatCarriesNoLanguage() {
        assertEquals("pt-BR", sent(request().build()).header("Accept-Language"))
    }

    @Test
    fun keepsALanguageTheCallerAlreadySet() {
        val explicit = request().header("Accept-Language", "en-US").build()

        assertEquals("en-US", sent(explicit).header("Accept-Language"))
    }

    @Test
    fun leavesTheRestOfTheRequestAlone() {
        val original = request().header("Authorization", "Bearer token").build()

        val forwarded = sent(original)

        assertEquals("Bearer token", forwarded.header("Authorization"))
        assertEquals(original.url, forwarded.url)
    }
}
