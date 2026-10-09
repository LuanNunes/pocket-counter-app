package com.resolveprogramming.pocketcounter.data.remote

import com.resolveprogramming.pocketcounter.data.remote.RawIntentMappers.toIntent
import com.resolveprogramming.pocketcounter.data.remote.dto.CardCandidateDto
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassificationSuggestionDto
import com.resolveprogramming.pocketcounter.data.remote.dto.RawCardDto
import com.resolveprogramming.pocketcounter.data.remote.dto.RawReadingDto
import com.resolveprogramming.pocketcounter.data.remote.dto.RawSourceDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionRawResponseDto
import com.resolveprogramming.pocketcounter.domain.model.CardCandidate
import com.resolveprogramming.pocketcounter.domain.model.CardResolution
import com.resolveprogramming.pocketcounter.domain.model.IntentField
import com.resolveprogramming.pocketcounter.domain.model.MissingField
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.ValueSource
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class RawIntentMappersTest {

    private val referenceDate = LocalDate.of(2026, 6, 4)

    /** Mirrors NetworkModule's Json. */
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun toIntent_responseThatUnderstoodNothing_mapsToAnEmptyIntent() {
        val dto = TransactionRawResponseDto(reading = RawReadingDto(date = "2026-06-04"))

        val intent = dto.toIntent(referenceDate)

        assertNull(intent.reading.type)
        assertNull(intent.reading.amount)
        assertEquals(LocalDate.of(2026, 6, 4), intent.reading.date)
        assertNull(intent.reading.name)
        assertNull(intent.reading.paymentMethod)
        assertEquals(emptyMap<IntentField, ValueSource>(), intent.source)
        assertEquals(CardResolution.NOT_APPLICABLE, intent.cardStatus)
        assertNull(intent.resolvedCard)
        assertEquals(emptyList<CardCandidate>(), intent.cardCandidates)
        assertNull(intent.idTag)
        assertEquals(emptyList<MissingField>(), intent.missing)
    }

    /** Two provenances in one sentence: the card was named, the method was deduced. Never merged. */
    @Test
    fun toIntent_cardWrittenAndMethodInferred_keepsTwoDistinctEntries() {
        val dto = TransactionRawResponseDto(
            reading = RawReadingDto(date = "2026-06-04", paymentMethod = "CREDIT"),
            source = RawSourceDto(card = "WRITTEN", paymentMethod = "INFERRED"),
        )

        val intent = dto.toIntent(referenceDate)

        assertEquals(ValueSource.WRITTEN, intent.source[IntentField.CARD])
        assertEquals(ValueSource.INFERRED, intent.source[IntentField.PAYMENT_METHOD])
        assertEquals(2, intent.source.size)
    }

    @Test
    fun toIntent_resolvedCard_mapsTheStatusAndTheCard() {
        val dto = TransactionRawResponseDto(
            reading = RawReadingDto(date = "2026-06-04", paymentMethod = "CREDIT"),
            card = RawCardDto(
                status = "RESOLVED",
                resolved = CardCandidateDto(id = "card-1", name = "Apelido"),
                candidates = listOf(CardCandidateDto(id = "card-1", name = "Apelido")),
            ),
        )

        val intent = dto.toIntent(referenceDate)

        assertEquals(CardResolution.RESOLVED, intent.cardStatus)
        assertEquals("card-1", intent.resolvedCard?.id)
        assertEquals("Apelido", intent.resolvedCard?.name)
        assertEquals(listOf("card-1"), intent.cardCandidates.map { it.id })
    }

    @Test
    fun toIntent_missing_keepsTheServerOrderAndDropsWhatItCannotRead() {
        val dto = TransactionRawResponseDto(
            reading = RawReadingDto(date = "2026-06-04"),
            missing = listOf("TYPE", "A_FIELD_THIS_BUILD_DOES_NOT_KNOW", "AMOUNT"),
        )

        val intent = dto.toIntent(referenceDate)

        assertEquals(listOf(MissingField.TYPE, MissingField.AMOUNT), intent.missing)
    }

    @Test
    fun toIntent_classifiedTag_carriesBothIds() {
        val dto = TransactionRawResponseDto(
            reading = RawReadingDto(date = "2026-06-04"),
            tag = ClassificationSuggestionDto(idTag = "tag-1", idCategory = "cat-1"),
        )

        val intent = dto.toIntent(referenceDate)

        assertEquals("tag-1", intent.idTag)
        assertEquals("cat-1", intent.idCategory)
    }

    @Test
    fun toIntent_categoryWithoutATag_stillCarriesTheCategory() {
        val dto = TransactionRawResponseDto(
            reading = RawReadingDto(date = "2026-06-04"),
            tag = ClassificationSuggestionDto(idCategory = "cat-1"),
        )

        val intent = dto.toIntent(referenceDate)

        assertNull(intent.idTag)
        assertEquals("cat-1", intent.idCategory)
    }

    @Test
    fun toIntent_noTagSection_leavesBothIdsNull() {
        val dto = TransactionRawResponseDto(reading = RawReadingDto(date = "2026-06-04"))

        val intent = dto.toIntent(referenceDate)

        assertNull(intent.idTag)
        assertNull(intent.idCategory)
    }

    @Test
    fun toIntent_unknownTypeAndMethodStrings_degradeToNull() {
        val dto = TransactionRawResponseDto(
            reading = RawReadingDto(date = "2026-06-04", type = "TRANSFER", paymentMethod = "BOLETO"),
        )

        val intent = dto.toIntent(referenceDate)

        assertNull(intent.reading.type)
        assertNull(intent.reading.paymentMethod)
    }

    @Test
    fun toIntent_unknownProvenanceString_dropsThatEntryOnly() {
        val dto = TransactionRawResponseDto(
            reading = RawReadingDto(date = "2026-06-04", name = "Alvo"),
            source = RawSourceDto(name = "GUESSED", date = "INFERRED"),
        )

        val intent = dto.toIntent(referenceDate)

        assertNull(intent.source[IntentField.NAME])
        assertEquals(ValueSource.INFERRED, intent.source[IntentField.DATE])
        assertEquals(1, intent.source.size)
    }

    @Test
    fun toIntent_unknownCardStatus_isNotApplicable() {
        val dto = TransactionRawResponseDto(
            reading = RawReadingDto(date = "2026-06-04"),
            card = RawCardDto(status = "PENDING_REVIEW"),
        )

        assertEquals(CardResolution.NOT_APPLICABLE, dto.toIntent(referenceDate).cardStatus)
    }

    /** Contract §3 trap: NOT_APPLICABLE with CREDIT is a legitimate cardless credit row. */
    @Test
    fun toIntent_notApplicableWithACreditMethod_keepsBoth() {
        val dto = TransactionRawResponseDto(
            reading = RawReadingDto(date = "2026-06-04", paymentMethod = "CREDIT"),
            card = RawCardDto(status = "NOT_APPLICABLE"),
        )

        val intent = dto.toIntent(referenceDate)

        assertEquals(CardResolution.NOT_APPLICABLE, intent.cardStatus)
        assertEquals(PaymentMethod.CREDIT, intent.reading.paymentMethod)
        assertNull(intent.resolvedCard)
    }

    @Test
    fun toIntent_unreadableDate_fallsBackToTheReferenceDateSent() {
        val dto = TransactionRawResponseDto(reading = RawReadingDto(date = "ontem"))

        assertEquals(referenceDate, dto.toIntent(referenceDate).reading.date)
    }

    @Test
    fun toIntent_absentDate_fallsBackToTheReferenceDateSent() {
        val dto = TransactionRawResponseDto(reading = RawReadingDto())

        assertEquals(referenceDate, dto.toIntent(referenceDate).reading.date)
    }

    /** The wire shape itself: a method name this build does not know must not fail the whole read. */
    @Test
    fun decode_wholeResponseWithAnUnknownMethodName_readsRatherThanThrows() {
        val body = """
            {"reading":{"type":"EXPENSE","amount":12.34,"date":"2026-06-04","name":"Alvo",
                        "paymentMethod":"BOLETO"},
             "source":{"type":"WRITTEN","amount":"WRITTEN","date":"INFERRED","name":"WRITTEN"},
             "card":{"status":"NOT_APPLICABLE","resolved":null,"candidates":[]},
             "tag":{"idTag":null,"idCategory":null},
             "missing":[],"aKeyThisBuildDoesNotKnow":"x"}
        """.trimIndent()

        val intent = json.decodeFromString<TransactionRawResponseDto>(body).toIntent(referenceDate)

        assertEquals(TransactionType.EXPENSE, intent.reading.type)
        assertEquals(BigDecimal("12.34"), intent.reading.amount)
        assertEquals(LocalDate.of(2026, 6, 4), intent.reading.date)
        assertEquals("Alvo", intent.reading.name)
        assertNull(intent.reading.paymentMethod)
        assertEquals(ValueSource.WRITTEN, intent.source[IntentField.AMOUNT])
        assertEquals(ValueSource.INFERRED, intent.source[IntentField.DATE])
        assertEquals(CardResolution.NOT_APPLICABLE, intent.cardStatus)
        assertEquals(emptyList<MissingField>(), intent.missing)
    }
}
