package com.resolveprogramming.pocketcounter.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
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
}
