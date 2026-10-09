package com.resolveprogramming.pocketcounter.data.remote

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The only thing keeping a typed sentence and a learned pattern out of logcat on a debug build. A
 * mistyped path fails open to the full body, which nothing else would notice.
 */
class BodyLoggingInterceptorTest {

    private val sentence = "paguei 250 numa consulta do cachorro"
    private val json = "application/json".toMediaType()

    private fun logLines(path: String, debug: Boolean = true): List<String> {
        val lines = mutableListOf<String>()
        val interceptor = BodyLoggingInterceptor(debug) { message -> lines += message }
        val request = Request.Builder()
            .url("http://localhost:8080$path")
            .post("""{"text":"$sentence"}""".toRequestBody(json))
            .build()
        val chain = mockk<Interceptor.Chain>()
        val forwarded = slot<Request>()
        every { chain.request() } returns request
        every { chain.connection() } returns null
        every { chain.proceed(capture(forwarded)) } answers {
            Response.Builder()
                .request(forwarded.captured)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"reading":{"name":"Consulta"}}""".toResponseBody(json))
                .build()
        }
        interceptor.intercept(chain)
        return lines
    }

    @Test
    fun `the raw sentence endpoint is logged without its body`() {
        val lines = logLines("/api/v1/transactions/raw")

        assertFalse(lines.any { it.contains(sentence) })
        assertTrue(lines.any { it.contains("/api/v1/transactions/raw") })
    }

    @Test
    fun `the rule endpoint is logged without its body`() {
        val lines = logLines("/api/v1/classification-rules")

        assertFalse(lines.any { it.contains(sentence) })
        assertTrue(lines.any { it.contains("/api/v1/classification-rules") })
    }

    @Test
    fun `every other endpoint keeps full body logging`() {
        val lines = logLines("/api/v1/transactions/expenses")

        assertTrue(lines.any { it.contains(sentence) })
    }

    @Test
    fun `a release build logs nothing at all`() {
        assertTrue(logLines("/api/v1/transactions/expenses", debug = false).isEmpty())
    }
}
