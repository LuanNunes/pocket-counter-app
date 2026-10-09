package com.resolveprogramming.pocketcounter.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ApiErrorsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun http(status: Int, body: String = "") = HttpException(
        Response.error<Any>(status, body.toResponseBody("application/json".toMediaType())),
    )

    private fun httpWithHeader(status: Int, name: String, value: String): HttpException {
        val raw = okhttp3.Response.Builder()
            .code(status)
            .message("error")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
            .header(name, value)
            .build()
        return HttpException(Response.error<Any>("".toResponseBody("application/json".toMediaType()), raw))
    }

    @Test
    fun httpStatus_httpException_returnsItsStatus() {
        assertEquals(409, http(409).httpStatus())
    }

    @Test
    fun httpStatus_ioException_isNull() {
        assertNull(IOException("offline").httpStatus())
    }

    @Test
    fun isConflict_409_isTrue() {
        assertTrue(http(409).isConflict())
    }

    @Test
    fun isConflict_409WithConflictBody_isTrue() {
        val body = """{"code":"CONFLICT","message":"x","correlationId":"abc"}"""

        assertTrue(http(409, body).isConflict())
    }

    @Test
    fun isConflict_409WithUnreadableBody_isStillTrue() {
        assertTrue(http(409, "<html>").isConflict())
    }

    @Test
    fun isConflict_400_isFalse() {
        assertFalse(http(400, """{"code":"CONFLICT","message":"x"}""").isConflict())
    }

    @Test
    fun isConflict_500_isFalse() {
        assertFalse(http(500).isConflict())
    }

    @Test
    fun isConflict_ioException_isFalse() {
        assertFalse(IOException("offline").isConflict())
    }

    @Test
    fun isUnprocessable_422_isTrue() {
        assertTrue(http(422).isUnprocessable())
    }

    @Test
    fun isUnprocessable_409_isFalse() {
        assertFalse(http(409).isUnprocessable())
    }

    @Test
    fun errorMessage_readsTheLocalizedMessageFromBody() {
        val body = """{"code":"BAD_REQUEST","message":"A tag precisa ser de despesa.","correlationId":"abc"}"""

        assertEquals("A tag precisa ser de despesa.", http(422, body).errorMessage(json))
    }

    @Test
    fun errorMessage_unreadableBody_isNull() {
        assertNull(http(500, "<html>").errorMessage(json))
    }

    @Test
    fun errorMessage_ioException_isNull() {
        assertNull(IOException("offline").errorMessage(json))
    }

    @Test
    fun withoutHttpException_keepsTheStatusAndDropsTheRetrofitType() {
        val translated = http(422).withoutHttpException()

        assertEquals(422, (translated as ApiException).status)
    }

    @Test
    fun withoutHttpException_passesANonHttpThrowableThrough() {
        val offline = IOException("offline")

        assertEquals(offline, offline.withoutHttpException())
    }

    @Test
    fun isBadRequest_400_isTrue() {
        assertTrue(http(400).isBadRequest())
    }

    @Test
    fun isBadRequest_422_isFalse() {
        assertFalse(http(422).isBadRequest())
    }

    @Test
    fun isTooManyRequests_429_isTrue() {
        assertTrue(http(429).isTooManyRequests())
    }

    @Test
    fun isServerError_503_isTrue() {
        assertTrue(http(503).isServerError())
    }

    @Test
    fun isServerError_499_isFalse() {
        assertFalse(http(499).isServerError())
    }

    @Test
    fun isServerError_ioException_isFalse() {
        assertFalse(IOException("offline").isServerError())
    }

    @Test
    fun responseHeader_readsTheHeaderOfTheFailedResponse() {
        assertEquals("30", httpWithHeader(429, "Retry-After", "30").responseHeader("Retry-After"))
    }

    @Test
    fun responseHeader_headerTheResponseDoesNotCarry_isNull() {
        assertNull(httpWithHeader(429, "Retry-After", "30").responseHeader("X-Other"))
    }

    @Test
    fun responseHeader_ioException_isNull() {
        assertNull(IOException("offline").responseHeader("Retry-After"))
    }

    @Test
    fun errorDetails_readsTheDetailsTheBodyCarries() {
        val body = """{"code":"CONFLICT","message":"x","details":["tx-1","tx-2"]}"""

        assertEquals(listOf("tx-1", "tx-2"), http(409, body).errorDetails(json))
    }

    @Test
    fun errorDetails_bodyWithoutDetails_isEmpty() {
        assertTrue(http(409, """{"code":"CONFLICT","message":"x"}""").errorDetails(json).isEmpty())
    }

    @Test
    fun errorDetails_unreadableBody_isEmpty() {
        assertTrue(http(500, "<html>").errorDetails(json).isEmpty())
    }

    @Test
    fun errorDetails_ioException_isEmpty() {
        assertTrue(IOException("offline").errorDetails(json).isEmpty())
    }
}
