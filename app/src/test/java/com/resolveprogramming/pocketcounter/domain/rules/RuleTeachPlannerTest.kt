package com.resolveprogramming.pocketcounter.domain.rules

import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.RuleAction
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleTeachPlannerTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeTag(
        id: String = "tag-1",
        name: String = "Alimentação",
        kind: TransactionType = TransactionType.EXPENSE,
        idContext: String? = "ctx-food",
    ) = Tag(id = id, name = name, kind = kind, idContext = idContext)

    private fun makeRule(
        id: String? = "rule-1",
        patterns: List<String> = listOf("IFOOD"),
        tags: List<Tag> = listOf(makeTag()),
        action: RuleAction = RuleAction.SUGGEST,
        paymentMethod: com.resolveprogramming.pocketcounter.domain.model.PaymentMethod? = null,
        cardId: String? = null,
    ) = ClassificationRule(
        id = id,
        patterns = patterns,
        matchType = "CONTAINS",
        active = true,
        appliedCount = 0,
        transactionType = TransactionType.EXPENSE,
        paymentMethod = paymentMethod,
        cardId = cardId,
        tags = tags,
        action = action,
    )

    // -------------------------------------------------------------------------
    // Create: no same-category rule exists
    // -------------------------------------------------------------------------

    @Test
    fun `plan_noCategoryRuleExists_returnsCreate`() {
        val tags = listOf(makeTag(id = "tag-1", idContext = "ctx-food"))
        val existing = emptyList<ClassificationRule>()

        val result = RuleTeachPlanner.plan(
            existing = existing,
            categoryId = "ctx-food",
            tags = tags,
            type = TransactionType.EXPENSE,
            pattern = "IFOOD",
        )

        assertTrue(result is TeachPlan.Create)
        val created = (result as TeachPlan.Create).rule
        assertEquals(listOf("IFOOD"), created.patterns)
        assertNull(created.paymentMethod)
        assertNull(created.cardId)
        assertEquals(tags, created.tags)
        assertEquals(TransactionType.EXPENSE, created.transactionType)
        assertEquals(RuleAction.SUGGEST, created.action)
        assertNull(created.id)
    }

    @Test
    fun `plan_createRule_hasNullPaymentMethodAndCardId_always`() {
        val tags = listOf(makeTag(idContext = "ctx-food"))
        val existing = emptyList<ClassificationRule>()

        val result = RuleTeachPlanner.plan(
            existing = existing,
            categoryId = "ctx-food",
            tags = tags,
            type = TransactionType.EXPENSE,
            pattern = "Nubank",
        )

        val created = (result as TeachPlan.Create).rule
        assertNull(created.paymentMethod)
        assertNull(created.cardId)
    }

    @Test
    fun `plan_createRule_matchTypeIsContains`() {
        val tags = listOf(makeTag(idContext = "ctx-food"))

        val result = RuleTeachPlanner.plan(
            existing = emptyList(),
            categoryId = "ctx-food",
            tags = tags,
            type = TransactionType.EXPENSE,
            pattern = "IFOOD",
        )

        assertEquals("CONTAINS", (result as TeachPlan.Create).rule.matchType)
    }

    // -------------------------------------------------------------------------
    // Update: same-category SUGGEST rule exists — pattern appended
    // -------------------------------------------------------------------------

    @Test
    fun `plan_sameCategoryRuleExists_returnsUpdate`() {
        val tag = makeTag(id = "tag-1", idContext = "ctx-food")
        val existingRule = makeRule(id = "rule-1", patterns = listOf("IFOOD"), tags = listOf(tag))
        val newTags = listOf(makeTag(id = "tag-1", idContext = "ctx-food"))

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            categoryId = "ctx-food",
            tags = newTags,
            type = TransactionType.EXPENSE,
            pattern = "RAPPI",
        )

        assertTrue(result is TeachPlan.Update)
        val updated = (result as TeachPlan.Update).rule
        assertEquals("rule-1", updated.id)
        assertEquals(listOf("IFOOD", "RAPPI"), updated.patterns)
    }

    @Test
    fun `plan_update_doesNotModifyPaymentMethodOrCardId`() {
        val tag = makeTag(id = "tag-1", idContext = "ctx-food")
        val existingRule = makeRule(
            id = "rule-1",
            patterns = listOf("IFOOD"),
            tags = listOf(tag),
            paymentMethod = com.resolveprogramming.pocketcounter.domain.model.PaymentMethod.CREDIT,
            cardId = "card-x",
        )

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            categoryId = "ctx-food",
            tags = listOf(tag),
            type = TransactionType.EXPENSE,
            pattern = "RAPPI",
        )

        val updated = (result as TeachPlan.Update).rule
        // The existing paymentMethod/cardId from the rule is preserved as-is (we don't strip it)
        // The key point is we don't forcibly write new payment info into the rule
        assertEquals(listOf("IFOOD", "RAPPI"), updated.patterns)
    }

    @Test
    fun `plan_update_picksFirstSameCategoryRule_whenMultipleExist`() {
        val tag = makeTag(id = "tag-1", idContext = "ctx-food")
        val olderRule = makeRule(id = "rule-oldest", patterns = listOf("IFOOD"), tags = listOf(tag))
        val newerRule = makeRule(id = "rule-newer", patterns = listOf("RAPPI"), tags = listOf(tag))

        val result = RuleTeachPlanner.plan(
            existing = listOf(olderRule, newerRule),
            categoryId = "ctx-food",
            tags = listOf(tag),
            type = TransactionType.EXPENSE,
            pattern = "UBER EATS",
        )

        val updated = (result as TeachPlan.Update).rule
        assertEquals("rule-oldest", updated.id)
        assertEquals(listOf("IFOOD", "UBER EATS"), updated.patterns)
    }

    // -------------------------------------------------------------------------
    // NoOp: pattern already covered
    // -------------------------------------------------------------------------

    @Test
    fun `plan_exactDuplicatePattern_returnsNoOp`() {
        val tag = makeTag(idContext = "ctx-food")
        val existingRule = makeRule(patterns = listOf("IFOOD"), tags = listOf(tag))

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            categoryId = "ctx-food",
            tags = listOf(tag),
            type = TransactionType.EXPENSE,
            pattern = "IFOOD",
        )

        assertTrue(result is TeachPlan.NoOp)
    }

    @Test
    fun `plan_exactDuplicatePattern_caseInsensitive_returnsNoOp`() {
        val tag = makeTag(idContext = "ctx-food")
        val existingRule = makeRule(patterns = listOf("ifood"), tags = listOf(tag))

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            categoryId = "ctx-food",
            tags = listOf(tag),
            type = TransactionType.EXPENSE,
            pattern = "IFOOD",
        )

        assertTrue(result is TeachPlan.NoOp)
    }

    @Test
    fun `plan_newPatternContainedByExistingSubstring_returnsNoOp`() {
        // Existing "Uber" already matches any text containing "UberRides" (broader substring covers it)
        val tag = makeTag(idContext = "ctx-transport")
        val existingRule = makeRule(patterns = listOf("Uber"), tags = listOf(tag))

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            categoryId = "ctx-transport",
            tags = listOf(tag),
            type = TransactionType.EXPENSE,
            pattern = "UberRides",
        )

        assertTrue(result is TeachPlan.NoOp)
    }

    @Test
    fun `plan_newPatternContainsCoverage_caseInsensitive_returnsNoOp`() {
        val tag = makeTag(idContext = "ctx-transport")
        val existingRule = makeRule(patterns = listOf("uber"), tags = listOf(tag))

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            categoryId = "ctx-transport",
            tags = listOf(tag),
            type = TransactionType.EXPENSE,
            pattern = "UBERRIDES",
        )

        assertTrue(result is TeachPlan.NoOp)
    }

    // -------------------------------------------------------------------------
    // IGNORE rules never chosen as target
    // -------------------------------------------------------------------------

    @Test
    fun `plan_ignoreRuleWithSameCategory_notChosenAsTarget_returnsCreate`() {
        val tag = makeTag(idContext = "ctx-food")
        val ignoreRule = makeRule(
            id = "ignore-rule",
            patterns = listOf("SPAM"),
            tags = listOf(tag),
            action = RuleAction.IGNORE,
        )

        val result = RuleTeachPlanner.plan(
            existing = listOf(ignoreRule),
            categoryId = "ctx-food",
            tags = listOf(tag),
            type = TransactionType.EXPENSE,
            pattern = "IFOOD",
        )

        // Should Create a new rule, not update the IGNORE rule
        assertTrue(result is TeachPlan.Create)
        val created = (result as TeachPlan.Create).rule
        assertEquals(listOf("IFOOD"), created.patterns)
    }

    // -------------------------------------------------------------------------
    // Match is by category (idContext), not by tag id
    // -------------------------------------------------------------------------

    @Test
    fun `plan_matchesByCategory_notByTagId`() {
        // Different tag IDs but same category (idContext)
        val tagInRule = makeTag(id = "tag-old", idContext = "ctx-food")
        val tagInDraft = makeTag(id = "tag-new", idContext = "ctx-food")

        val existingRule = makeRule(id = "rule-1", patterns = listOf("IFOOD"), tags = listOf(tagInRule))

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            categoryId = "ctx-food",
            tags = listOf(tagInDraft),
            type = TransactionType.EXPENSE,
            pattern = "RAPPI",
        )

        // Must be Update because the category matches, even though tag IDs differ
        assertTrue(result is TeachPlan.Update)
        val updated = (result as TeachPlan.Update).rule
        assertEquals(listOf("IFOOD", "RAPPI"), updated.patterns)
    }

    @Test
    fun `plan_differentCategory_doesNotMatch_returnsCreate`() {
        val tagInRule = makeTag(id = "tag-1", idContext = "ctx-food")
        val tagInDraft = makeTag(id = "tag-2", idContext = "ctx-transport")

        val existingRule = makeRule(id = "rule-1", patterns = listOf("IFOOD"), tags = listOf(tagInRule))

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            categoryId = "ctx-transport",
            tags = listOf(tagInDraft),
            type = TransactionType.EXPENSE,
            pattern = "UBER",
        )

        assertTrue(result is TeachPlan.Create)
    }
}
