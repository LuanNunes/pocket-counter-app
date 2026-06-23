package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.dto.AssistantAskRequestDto
import com.resolveprogramming.pocketcounter.data.remote.dto.AssistantAskResponseDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the JSON serialization contract for AssistantAskRequestDto and AssistantAskResponseDto
 * against the camelCase wire format the backend produces/consumes.
 *
 * The Json instance mirrors the one provided by NetworkModule exactly:
 *   Json { ignoreUnknownKeys = true; coerceInputValues = true }
 */
class AssistantDtoSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // -------------------------------------------------------------------------
    // AssistantAskResponseDto — deserialization
    // -------------------------------------------------------------------------

    @Test
    fun `decode nominal response body maps all three fields`() {
        val raw = """{"answer":"**oi**","elapsedMs":4200,"remainingQuestions":3}"""

        val dto = json.decodeFromString<AssistantAskResponseDto>(raw)

        assertEquals("**oi**", dto.answer)
        assertEquals(4200L, dto.elapsedMs)
        assertEquals(3, dto.remainingQuestions)
    }

    @Test
    fun `decode response body with extra unknown key does not throw`() {
        val raw = """{"answer":"ok","elapsedMs":100,"remainingQuestions":2,"foo":1}"""

        val dto = json.decodeFromString<AssistantAskResponseDto>(raw)

        assertEquals("ok", dto.answer)
        assertEquals(100L, dto.elapsedMs)
        assertEquals(2, dto.remainingQuestions)
    }

    @Test
    fun `decode response body with defaults when elapsedMs and remainingQuestions are absent`() {
        val raw = """{"answer":"olá"}"""

        val dto = json.decodeFromString<AssistantAskResponseDto>(raw)

        assertEquals("olá", dto.answer)
        assertEquals(0L, dto.elapsedMs)
        assertEquals(0, dto.remainingQuestions)
    }

    @Test
    fun `decode response body preserves markdown content verbatim`() {
        val markdown = "## Resumo\n\n- item 1\n- item 2"
        val raw = """{"answer":"$markdown","elapsedMs":750,"remainingQuestions":4}"""

        val dto = json.decodeFromString<AssistantAskResponseDto>(raw)

        assertEquals(markdown, dto.answer)
    }

    // -------------------------------------------------------------------------
    // AssistantAskRequestDto — serialization
    // -------------------------------------------------------------------------

    @Test
    fun `encode request serializes to camelCase question field`() {
        val dto = AssistantAskRequestDto(question = "Quanto gastei esse mês?")

        val encoded = json.encodeToString(dto)

        assertEquals("""{"question":"Quanto gastei esse mês?"}""", encoded)
    }

    @Test
    fun `encode request with special characters serializes correctly`() {
        val dto = AssistantAskRequestDto(question = "R\$ 1.000,00 em supermercado?")

        val encoded = json.encodeToString(dto)

        // The JSON must contain the question field and nothing else
        val decoded = json.decodeFromString<AssistantAskRequestDto>(encoded)
        assertEquals(dto.question, decoded.question)
    }
}
