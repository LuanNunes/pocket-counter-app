package com.resolveprogramming.pocketcounter.domain.notification

/**
 * Pure Kotlin utility for deriving a credit-card identity from the "final NNNN" payment hint
 * produced by [BrNotificationParser].
 *
 * No Android, Retrofit, or Room imports — lives in the domain layer.
 */
object CardLast4Matcher {

    // Matches "final " followed by exactly 4 digits that are NOT succeeded by another digit.
    // The negative lookahead (?!\d) prevents matching the first 4 chars of a 5-digit run.
    private val LAST4_REGEX = Regex("""final\s+(\d{4})(?!\d)""", RegexOption.IGNORE_CASE)

    /**
     * Extracts the 4-digit suffix from a "final NNNN" payment hint string.
     *
     * Returns null when:
     * - [paymentHint] is null
     * - [paymentHint] is "cartão", "conta", or any string without a "final NNNN" pattern
     * - The digits after "final" are fewer or more than exactly 4
     */
    fun extractLast4(paymentHint: String?): String? {
        paymentHint ?: return null
        return LAST4_REGEX.find(paymentHint)?.groupValues?.get(1)
    }

    /**
     * Returns the single cardId from [last4ByCardId] whose value equals [last4].
     *
     * Returns null when:
     * - No entry in the map has the given last4 (unrecognised card)
     * - More than one entry matches (ambiguous — caller treats as unknown)
     */
    fun matchCardId(last4: String, last4ByCardId: Map<String, String>): String? {
        val matches = last4ByCardId.entries.filter { it.value == last4 }
        return if (matches.size == 1) matches.first().key else null
    }
}
