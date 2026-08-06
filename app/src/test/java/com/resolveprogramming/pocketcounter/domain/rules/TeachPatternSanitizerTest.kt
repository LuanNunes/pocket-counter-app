package com.resolveprogramming.pocketcounter.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeachPatternSanitizerTest {

    // -------------------------------------------------------------------------
    // clean — corpus gateway prefixes are rejected: a trailing '*' with no letter after it
    // -------------------------------------------------------------------------

    @Test
    fun `clean_rejects_Ifd_star`() {
        assertNull(TeachPatternSanitizer.clean("Ifd*"))
    }

    @Test
    fun `clean_rejects_Dl_space_star`() {
        assertNull(TeachPatternSanitizer.clean("Dl *"))
    }

    @Test
    fun `clean_rejects_Mp_space_star`() {
        assertNull(TeachPatternSanitizer.clean("Mp *"))
    }

    @Test
    fun `clean_rejects_Pg2_star`() {
        assertNull(TeachPatternSanitizer.clean("Pg2*"))
    }

    @Test
    fun `clean_rejects_Ebn_space_star`() {
        assertNull(TeachPatternSanitizer.clean("Ebn *"))
    }

    @Test
    fun `clean_rejects_Rp3bank_star`() {
        assertNull(TeachPatternSanitizer.clean("Rp3bank*"))
    }

    @Test
    fun `clean_rejects_Cappta_space_star`() {
        assertNull(TeachPatternSanitizer.clean("Cappta *"))
    }

    @Test
    fun `clean_rejects_Pagali_space_star`() {
        assertNull(TeachPatternSanitizer.clean("Pagali *"))
    }

    // -------------------------------------------------------------------------
    // clean — trailing punctuation is stripped
    // -------------------------------------------------------------------------

    @Test
    fun `clean_stripsTrailingComma_PostoDe`() {
        assertEquals("PostoDe", TeachPatternSanitizer.clean("PostoDe,"))
    }

    @Test
    fun `clean_stripsTrailingComma_AUTO_AVENIDA`() {
        assertEquals("AUTO AVENIDA", TeachPatternSanitizer.clean("AUTO AVENIDA,"))
    }

    @Test
    fun `clean_stripsTrailingComma_DL_star_UberRides`() {
        assertEquals("DL*UberRides", TeachPatternSanitizer.clean("DL*UberRides,"))
    }

    @Test
    fun `clean_stripsTheSpaceLeftBehindByTrailingPunctuation`() {
        // RulePatterns.covers is whitespace-strict, so "Padaria " and "Padaria" would never compact
        // into each other and the rule would accumulate near-duplicate patterns forever.
        assertEquals("Padaria", TeachPatternSanitizer.clean("Padaria -"))
    }

    // -------------------------------------------------------------------------
    // clean — the IGNORE path opts out of gateway rejection
    // -------------------------------------------------------------------------

    @Test
    fun `clean_allowGatewayMarker_keepsTheBarePrefix`() {
        assertEquals("Ifd*", TeachPatternSanitizer.clean("Ifd*", allowGatewayMarker = true))
        assertEquals("Mp *", TeachPatternSanitizer.clean("Mp *", allowGatewayMarker = true))
    }

    // -------------------------------------------------------------------------
    // clean — a '*' with letters after the last one is a real merchant pattern, kept intact
    // -------------------------------------------------------------------------

    @Test
    fun `clean_keepsIFD_star_PADARIA_DE_TESTE_intact`() {
        assertEquals("IFD*PADARIA DE TESTE", TeachPatternSanitizer.clean("IFD*PADARIA DE TESTE"))
    }

    // -------------------------------------------------------------------------
    // choose — candidate ordering, fall-through and literal-substring guarantee
    // -------------------------------------------------------------------------

    @Test
    fun `choose_rejectedCandidate_fallsThroughToTheNextCandidate`() {
        val result = TeachPatternSanitizer.choose(
            candidates = listOf("Ifd*", "PADARIA DE TESTE"),
            notificationText = "Compra IFD*PADARIA DE TESTE aprovada R$ 49,90",
        )

        assertEquals("PADARIA DE TESTE", result)
    }

    @Test
    fun `choose_candidateNotContainedInText_fallsThroughToTheNextCandidate`() {
        val result = TeachPatternSanitizer.choose(
            candidates = listOf("RAPPI", "PADARIA DE TESTE"),
            notificationText = "Compra IFD*PADARIA DE TESTE aprovada R$ 49,90",
        )

        assertEquals("PADARIA DE TESTE", result)
    }

    @Test
    fun `choose_matchesCaseInsensitively_andStoresTheCandidateVerbatim`() {
        // Deliberate semantic: containment is case-INsensitive (matching the rest of RulePatterns,
        // which folds case), and the survivor is stored exactly as the candidate spelled it — never
        // rewritten to the slice of text it matched.
        val result = TeachPatternSanitizer.choose(
            candidates = listOf("padaria de teste"),
            notificationText = "Compra IFD*PADARIA DE TESTE aprovada R$ 49,90",
        )

        assertEquals("padaria de teste", result)
    }

    @Test
    fun `choose_resultIsAlwaysASubstringOfTheNotificationText_upToCase`() {
        val notificationText = "Compra IFD*PADARIA DE TESTE aprovada R$ 49,90"

        val result = TeachPatternSanitizer.choose(
            candidates = listOf("Ifd*", "PADARIA DE TESTE"),
            notificationText = notificationText,
        )

        assertNotNull(result)
        assertTrue(notificationText.contains(result!!, ignoreCase = true))
    }

    @Test
    fun `choose_neverStripsATrailingStarToProduceAShorterPattern`() {
        val result = TeachPatternSanitizer.choose(
            candidates = listOf("Ifd*"),
            notificationText = "Compra IFD*PADARIA DE TESTE aprovada R$ 49,90",
        )

        assertNull(result)
    }
}
