package com.resolveprogramming.pocketcounter.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulePatternsTest {

    // -------------------------------------------------------------------------
    // normalize
    // -------------------------------------------------------------------------

    @Test
    fun `normalize_lowercases`() {
        assertEquals("uber", RulePatterns.normalize("Uber"))
    }

    @Test
    fun `normalize_trimsLeadingAndTrailingWhitespace`() {
        assertEquals("uber", RulePatterns.normalize("  Uber  "))
    }

    @Test
    fun `normalize_collapsesWhitespaceRunsToASingleSpace`() {
        assertEquals("dl *uberrides", RulePatterns.normalize("DL     *UberRides"))
    }

    // -------------------------------------------------------------------------
    // matches
    // -------------------------------------------------------------------------

    @Test
    fun `matches_returnsTrue_whenNormalizedTextContainsNormalizedPattern`() {
        assertTrue(RulePatterns.matches("Uber", "Compra UBER trip aprovada"))
    }

    @Test
    fun `matches_returnsFalse_whenNormalizedTextDoesNotContainNormalizedPattern`() {
        assertFalse(RulePatterns.matches("Rappi", "Compra UBER trip aprovada"))
    }

    @Test
    fun `matches_returnsFalse_forABlankPattern`() {
        // Literally every text contains "", which would make one rule carrying a blank pattern the
        // target of every teach.
        assertFalse(RulePatterns.matches("", "Compra UBER trip aprovada"))
        assertFalse(RulePatterns.matches("   ", "Compra UBER trip aprovada"))
    }

    @Test
    fun `matches_ignoresWhitespaceRunDifferences`() {
        assertTrue(RulePatterns.matches("DL     *UberRides", "Compra DL *UberRides aprovada"))
    }

    // -------------------------------------------------------------------------
    // covers
    // -------------------------------------------------------------------------

    @Test
    fun `covers_returnsTrue_whenNarrowContainsBroadAfterNormalizing`() {
        assertTrue(RulePatterns.covers("Uber", "UberRides"))
    }

    @Test
    fun `covers_returnsFalse_whenNarrowDoesNotContainBroad`() {
        assertFalse(RulePatterns.covers("UberRides", "Uber"))
    }

    @Test
    fun `covers_isReflexive`() {
        assertTrue(RulePatterns.covers("Uber", "Uber"))
    }

    @Test
    fun `covers_isCaseInsensitive`() {
        assertTrue(RulePatterns.covers("uber", "UberRides"))
    }

    @Test
    fun `covers_returnsFalse_forDifferingWhitespaceRuns`() {
        // covers authorises deletion, so it is strict where matches is loose: a differing run of
        // spaces is a different literal to a whitespace-sensitive CONTAINS.
        assertFalse(RulePatterns.covers("DL *UberRides", "DL     *UberRides"))
        assertFalse(RulePatterns.covers("DL     *UberRides", "DL *UberRides"))
    }

    // -------------------------------------------------------------------------
    // isCovered
    // -------------------------------------------------------------------------

    @Test
    fun `isCovered_returnsTrue_whenAnyPatternCoversCandidate`() {
        assertTrue(RulePatterns.isCovered(listOf("IFOOD", "Uber"), "UberRides"))
    }

    @Test
    fun `isCovered_returnsFalse_whenNoPatternCoversCandidate`() {
        assertFalse(RulePatterns.isCovered(listOf("IFOOD", "Rappi"), "UberRides"))
    }

    // -------------------------------------------------------------------------
    // overlap
    // -------------------------------------------------------------------------

    @Test
    fun `overlap_returnsTrue_whenAPatternInACoversAPatternInB`() {
        assertTrue(RulePatterns.overlap(listOf("Uber"), listOf("UberRides")))
    }

    @Test
    fun `overlap_returnsTrue_whenAPatternInBCoversAPatternInA`() {
        assertTrue(RulePatterns.overlap(listOf("UberRides"), listOf("Uber")))
    }

    @Test
    fun `overlap_returnsFalse_whenNoPairCovers`() {
        assertFalse(RulePatterns.overlap(listOf("IFOOD"), listOf("RAPPI")))
    }

    // -------------------------------------------------------------------------
    // compact
    // -------------------------------------------------------------------------

    @Test
    fun `compact_returnsOriginalVerbatimStrings_notNormalized`() {
        val result = RulePatterns.compact(listOf("Uber"))

        assertEquals(listOf("Uber"), result)
    }

    @Test
    fun `compact_dropsPattern_coveredByABroaderSurvivor`() {
        val result = RulePatterns.compact(listOf("Uber", "UberRides"))

        assertEquals(listOf("Uber"), result)
    }

    @Test
    fun `compact_caseOnlyDuplicates_earliestOccurrenceWins`() {
        val result = RulePatterns.compact(listOf("IFOOD", "ifood"))

        // Equivalent once case-folded; the earliest occurrence (index 0) must survive, not mutually
        // eliminate with the later one.
        assertEquals(listOf("IFOOD"), result)
    }

    @Test
    fun `compact_whitespaceRunVariants_bothSurvive`() {
        val result = RulePatterns.compact(listOf("DL *UberRides", "DL     *UberRides"))

        // compact deletes patterns, so it compares whitespace verbatim: a differing run of spaces is
        // a different literal to a whitespace-sensitive CONTAINS, and dropping either one could stop
        // the rule from firing on the text only that one matches.
        assertEquals(listOf("DL *UberRides", "DL     *UberRides"), result)
    }

    @Test
    fun `compact_whitespaceVariantsWithAndWithoutASpace_bothSurvive`() {
        val result = RulePatterns.compact(listOf("DL*UberRides", "DL *UberRides"))

        assertEquals(listOf("DL*UberRides", "DL *UberRides"), result)
    }

    @Test
    fun `compact_preservesFirstOccurrenceOrderOfSurvivors`() {
        val result = RulePatterns.compact(listOf("IFOOD", "Uber", "RAPPI"))

        assertEquals(listOf("IFOOD", "Uber", "RAPPI"), result)
    }

    @Test
    fun `compact_dropsBlankEntries`() {
        val result = RulePatterns.compact(listOf("Uber", "", "  "))

        assertEquals(listOf("Uber"), result)
    }

    @Test
    fun `compact_neverReturnsEmptyList_forNonBlankInput`() {
        val result = RulePatterns.compact(listOf("Uber", "Uber", "Uber"))

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `compact_realUberFixture_collapsesToASinglePattern`() {
        val patterns = listOf(
            "Uber",
            "UberRides",
            "DL *UberRides",
            "DL*UberRides",
            "DL     *UberRides",
            "Uber UBER *TRIP HELP.U",
            "UBER *ONE MEMBERSHIP U",
        )

        val result = RulePatterns.compact(patterns)

        assertEquals(listOf("Uber"), result)
    }
}
