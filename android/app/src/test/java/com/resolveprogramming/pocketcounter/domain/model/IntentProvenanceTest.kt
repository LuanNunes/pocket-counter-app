package com.resolveprogramming.pocketcounter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class IntentProvenanceTest {

    @Test
    fun of_fieldTheUserDefined_isDefined() {
        val provenance = IntentProvenance.of(
            field = IntentField.AMOUNT,
            source = mapOf(IntentField.AMOUNT to ValueSource.WRITTEN),
            defined = setOf(IntentField.AMOUNT),
            hasValue = true,
        )

        assertEquals(FieldProvenance.DEFINED, provenance)
    }

    @Test
    fun of_writtenSource_isWritten() {
        val provenance = IntentProvenance.of(
            field = IntentField.AMOUNT,
            source = mapOf(IntentField.AMOUNT to ValueSource.WRITTEN),
            defined = emptySet(),
            hasValue = true,
        )

        assertEquals(FieldProvenance.WRITTEN, provenance)
    }

    @Test
    fun of_inferredSource_isInferred() {
        val provenance = IntentProvenance.of(
            field = IntentField.DATE,
            source = mapOf(IntentField.DATE to ValueSource.INFERRED),
            defined = emptySet(),
            hasValue = true,
        )

        assertEquals(FieldProvenance.INFERRED, provenance)
    }

    @Test
    fun of_fieldWithoutAValue_isAbsent() {
        val provenance = IntentProvenance.of(
            field = IntentField.CARD,
            source = mapOf(IntentField.CARD to ValueSource.WRITTEN),
            defined = emptySet(),
            hasValue = false,
        )

        assertEquals(FieldProvenance.ABSENT, provenance)
    }

    /** An answered ASK has a value and no `source` entry; badging it *da frase* would be a lie. */
    @Test
    fun of_valueWithoutASourceEntry_isInferredAndNeverWritten() {
        val provenance = IntentProvenance.of(
            field = IntentField.NAME,
            source = emptyMap(),
            defined = emptySet(),
            hasValue = true,
        )

        assertEquals(FieldProvenance.INFERRED, provenance)
    }

    @Test
    fun of_definedFieldWithoutASourceEntry_isDefined() {
        val provenance = IntentProvenance.of(
            field = IntentField.NAME,
            source = emptyMap(),
            defined = setOf(IntentField.NAME),
            hasValue = true,
        )

        assertEquals(FieldProvenance.DEFINED, provenance)
    }

    /** `defined` is checked before the value, so a cleared answer still reads *definido*. */
    @Test
    fun of_definedFieldTheUserThenCleared_isStillDefined() {
        val provenance = IntentProvenance.of(
            field = IntentField.NAME,
            source = emptyMap(),
            defined = setOf(IntentField.NAME),
            hasValue = false,
        )

        assertEquals(FieldProvenance.DEFINED, provenance)
    }
}
