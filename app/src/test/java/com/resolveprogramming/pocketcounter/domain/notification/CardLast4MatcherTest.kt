package com.resolveprogramming.pocketcounter.domain.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [CardLast4Matcher].
 *
 * extractLast4 mirrors the FINAL_REGEX semantics in BrNotificationParser:
 *   FINAL_REGEX = Regex("final\s+\d{4}")
 * The parser matches exactly 4 digits after "final"; the matcher then guards that no
 * further digit follows (so a synthetic "final 12345" string never yields a last4).
 */
class CardLast4MatcherTest {

    // -------------------------------------------------------------------------
    // extractLast4 — happy path
    // -------------------------------------------------------------------------

    @Test
    fun `extractLast4 with final 3685 hint returns 3685`() {
        assertEquals("3685", CardLast4Matcher.extractLast4("final 3685"))
    }

    // -------------------------------------------------------------------------
    // extractLast4 — null cases
    // -------------------------------------------------------------------------

    @Test
    fun `extractLast4 with null hint returns null`() {
        assertNull(CardLast4Matcher.extractLast4(null))
    }

    @Test
    fun `extractLast4 with cartao hint returns null`() {
        assertNull(CardLast4Matcher.extractLast4("cartão"))
    }

    @Test
    fun `extractLast4 with conta hint returns null`() {
        assertNull(CardLast4Matcher.extractLast4("conta"))
    }

    // -------------------------------------------------------------------------
    // extractLast4 — boundary: fewer than 4 digits
    // -------------------------------------------------------------------------

    @Test
    fun `extractLast4 with final 12 hint returns null`() {
        // "final 12" has only 2 digits — BrNotificationParser's FINAL_REGEX (final\s+\d{4})
        // would not produce this as a paymentHint; defensive guard for synthetic inputs.
        assertNull(CardLast4Matcher.extractLast4("final 12"))
    }

    // -------------------------------------------------------------------------
    // extractLast4 — boundary: more than 4 digits
    // -------------------------------------------------------------------------

    @Test
    fun `extractLast4 with final 12345 hint returns null`() {
        // A 5-digit string passed directly; the negative lookahead (?!\d) ensures the
        // 4-digit match is not followed by another digit, so this returns null.
        assertNull(CardLast4Matcher.extractLast4("final 12345"))
    }

    // -------------------------------------------------------------------------
    // matchCardId — happy path
    // -------------------------------------------------------------------------

    @Test
    fun `matchCardId single entry matching last4 returns that cardId`() {
        val map = mapOf("card-a" to "3685")

        assertEquals("card-a", CardLast4Matcher.matchCardId("3685", map))
    }

    // -------------------------------------------------------------------------
    // matchCardId — no match
    // -------------------------------------------------------------------------

    @Test
    fun `matchCardId no entry with matching last4 returns null`() {
        val map = mapOf("card-a" to "1234")

        assertNull(CardLast4Matcher.matchCardId("9999", map))
    }

    @Test
    fun `matchCardId empty map returns null`() {
        assertNull(CardLast4Matcher.matchCardId("3685", emptyMap()))
    }

    // -------------------------------------------------------------------------
    // matchCardId — ambiguous: more than one card shares the same last4
    // -------------------------------------------------------------------------

    @Test
    fun `matchCardId two cards with same last4 returns null`() {
        // Ambiguous — caller treats as unknown rather than guessing the wrong card.
        val map = mapOf("card-a" to "3685", "card-b" to "3685")

        assertNull(CardLast4Matcher.matchCardId("3685", map))
    }
}
