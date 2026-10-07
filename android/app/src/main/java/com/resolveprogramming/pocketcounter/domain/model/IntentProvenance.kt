package com.resolveprogramming.pocketcounter.domain.model

/** Where a value came from. The preview badges only *assumido* and *definido*; WRITTEN stays silent. */
enum class FieldProvenance { WRITTEN, INFERRED, DEFINED, ABSENT }

object IntentProvenance {
    /**
     * Precedence: [defined] (the user corrected or answered the row), then no value at all, then the
     * server's [source]. A field with a value and no [source] entry is INFERRED — this never degrades
     * to WRITTEN, which would claim the sentence carried a value it did not.
     */
    fun of(
        field: IntentField,
        source: Map<IntentField, ValueSource>,
        defined: Set<IntentField>,
        hasValue: Boolean,
    ): FieldProvenance {
        if (field in defined) return FieldProvenance.DEFINED
        if (!hasValue) return FieldProvenance.ABSENT
        return when (source[field]) {
            ValueSource.WRITTEN -> FieldProvenance.WRITTEN
            ValueSource.INFERRED -> FieldProvenance.INFERRED
            null -> FieldProvenance.INFERRED
        }
    }
}
