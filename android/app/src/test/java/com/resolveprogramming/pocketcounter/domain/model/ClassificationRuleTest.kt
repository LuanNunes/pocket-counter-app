package com.resolveprogramming.pocketcounter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClassificationRuleTest {

    @Test
    fun suggest_buildsActiveUnsavedSuggestRuleWithItsTag() {
        val rule = ClassificationRule.suggest("Padaria", "tag-1")

        assertNull(rule.id)
        assertEquals("Padaria", rule.pattern)
        assertEquals("tag-1", rule.idTag)
        assertEquals(RuleAction.SUGGEST, rule.action)
        assertEquals(true, rule.active)
        assertEquals(0, rule.appliedCount)
    }

    @Test
    fun ignore_buildsActiveUnsavedIgnoreRuleWithoutTag() {
        val rule = ClassificationRule.ignore("Spam")

        assertEquals(RuleAction.IGNORE, rule.action)
        assertNull(rule.idTag)
        assertEquals(true, rule.active)
    }

    @Test
    fun writeBlocker_validSuggest_isNull() {
        assertNull(ClassificationRule.suggest("Padaria", "t").writeBlocker(TransactionType.EXPENSE))
    }

    @Test
    fun writeBlocker_validIgnore_isNull() {
        assertNull(ClassificationRule.ignore("Spam").writeBlocker(null))
    }

    @Test
    fun writeBlocker_suggestWithoutTag_requiresTag() {
        val rule = ClassificationRule.suggest("Padaria", "t").copy(idTag = null)

        assertEquals(RuleWriteBlocker.SUGGEST_REQUIRES_TAG, rule.writeBlocker(null))
    }

    @Test
    fun writeBlocker_ignoreWithTag_forbidsTag() {
        val rule = ClassificationRule.ignore("Spam").copy(idTag = "t")

        assertEquals(RuleWriteBlocker.IGNORE_FORBIDS_TAG, rule.writeBlocker(null))
    }

    @Test
    fun writeBlocker_incomeTag_mustBeExpense() {
        val rule = ClassificationRule.suggest("Salário", "t")

        assertEquals(RuleWriteBlocker.TAG_MUST_BE_EXPENSE, rule.writeBlocker(TransactionType.INCOME))
    }

    @Test
    fun writeBlocker_unknownTagKind_isNotChecked() {
        assertNull(ClassificationRule.suggest("Salário", "t").writeBlocker(null))
    }

    @Test
    fun writeBlocker_patternWithoutLetterOrDigit_hasNoSignal() {
        val rule = ClassificationRule.suggest("  *- ", "t")

        assertEquals(RuleWriteBlocker.PATTERN_WITHOUT_SIGNAL, rule.writeBlocker(TransactionType.EXPENSE))
    }

    @Test
    fun writeBlocker_blankPatternOnIgnore_hasNoSignal() {
        assertEquals(RuleWriteBlocker.PATTERN_WITHOUT_SIGNAL, ClassificationRule.ignore("").writeBlocker(null))
    }

    @Test
    fun writeBlocker_digitOnlyPattern_hasSignal() {
        assertNull(ClassificationRule.ignore("*123").writeBlocker(null))
    }

    @Test
    fun writeBlocker_patternAtTheLimit_isNull() {
        val atLimit = "x".repeat(ClassificationRule.MAX_PATTERN_LENGTH)

        assertNull(ClassificationRule.suggest(atLimit, "t").writeBlocker(TransactionType.EXPENSE))
    }

    @Test
    fun writeBlocker_patternOverTheLimit_isTooLong() {
        val tooLong = "x".repeat(ClassificationRule.MAX_PATTERN_LENGTH + 1)

        assertEquals(
            RuleWriteBlocker.PATTERN_TOO_LONG,
            ClassificationRule.suggest(tooLong, "t").writeBlocker(TransactionType.EXPENSE),
        )
    }

    @Test
    fun writeBlocker_lengthIsMeasuredOnTheTrimmedPattern() {
        val padded = " " + "x".repeat(ClassificationRule.MAX_PATTERN_LENGTH) + " "

        assertNull(ClassificationRule.suggest(padded, "t").writeBlocker(TransactionType.EXPENSE))
    }

    @Test
    fun writeBlocker_reportsPatternBlockersBeforeTagBlockers() {
        val rule = ClassificationRule.suggest("---", "t").copy(idTag = null)

        assertEquals(RuleWriteBlocker.PATTERN_WITHOUT_SIGNAL, rule.writeBlocker(TransactionType.INCOME))
    }
}
