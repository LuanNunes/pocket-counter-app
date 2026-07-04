package com.resolveprogramming.pocketcounter.domain.rules

import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.RuleAction
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleDedupePlannerTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeTag(
        id: String,
        idContext: String?,
        kind: TransactionType = TransactionType.EXPENSE,
    ) = Tag(id = id, name = id, kind = kind, idContext = idContext)

    private fun makeRule(
        id: String,
        patterns: List<String>,
        tags: List<Tag>,
        action: RuleAction = RuleAction.SUGGEST,
    ) = ClassificationRule(
        id = id,
        patterns = patterns,
        matchType = "CONTAINS",
        active = true,
        appliedCount = 0,
        transactionType = TransactionType.EXPENSE,
        paymentMethod = null,
        cardId = null,
        tags = tags,
        action = action,
    )

    // -------------------------------------------------------------------------
    // Two same-category rules → merge oldest as canonical, delete newer
    // -------------------------------------------------------------------------

    @Test
    fun `plan_twoCategorySinglePatternRules_mergesAndDeletes`() {
        val tag1 = makeTag("tag-a", "ctx-food")
        val tag2 = makeTag("tag-b", "ctx-food")
        val oldest = makeRule("rule-1", listOf("IFOOD"), listOf(tag1))
        val newer = makeRule("rule-2", listOf("RAPPI"), listOf(tag2))

        val plan = RuleDedupePlanner.plan(listOf(oldest, newer))

        assertEquals(1, plan.updates.size)
        assertEquals(1, plan.deletes.size)
        val canonical = plan.updates[0]
        assertEquals("rule-1", canonical.id)
        assertEquals(listOf("IFOOD", "RAPPI"), canonical.patterns)
        assertEquals("rule-2", plan.deletes[0])
    }

    @Test
    fun `plan_threeCategoryRules_mergesAllPatternsIntoCanonical_deletesOthers`() {
        val tag = makeTag("tag-a", "ctx-food")
        val rule1 = makeRule("rule-1", listOf("IFOOD"), listOf(tag))
        val rule2 = makeRule("rule-2", listOf("RAPPI"), listOf(tag))
        val rule3 = makeRule("rule-3", listOf("BURGER KING"), listOf(tag))

        val plan = RuleDedupePlanner.plan(listOf(rule1, rule2, rule3))

        assertEquals(1, plan.updates.size)
        val canonical = plan.updates[0]
        assertEquals("rule-1", canonical.id)
        assertEquals(listOf("IFOOD", "RAPPI", "BURGER KING"), canonical.patterns)
        assertEquals(setOf("rule-2", "rule-3"), plan.deletes.toSet())
    }

    // -------------------------------------------------------------------------
    // IGNORE rules are never touched
    // -------------------------------------------------------------------------

    @Test
    fun `plan_ignoreRulesNotTouched_evenIfDuplicated`() {
        val tag = makeTag("tag-a", "ctx-food")
        val ignoreRule1 = makeRule("ignore-1", listOf("SPAM"), listOf(tag), action = RuleAction.IGNORE)
        val ignoreRule2 = makeRule("ignore-2", listOf("SPAM"), listOf(tag), action = RuleAction.IGNORE)

        val plan = RuleDedupePlanner.plan(listOf(ignoreRule1, ignoreRule2))

        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun `plan_mixedSuggestAndIgnore_onlyMergesSuggestRules`() {
        val tag = makeTag("tag-a", "ctx-food")
        val suggestRule1 = makeRule("suggest-1", listOf("IFOOD"), listOf(tag))
        val suggestRule2 = makeRule("suggest-2", listOf("RAPPI"), listOf(tag))
        val ignoreRule = makeRule("ignore-1", listOf("SPAM"), listOf(tag), action = RuleAction.IGNORE)

        val plan = RuleDedupePlanner.plan(listOf(suggestRule1, suggestRule2, ignoreRule))

        assertEquals(1, plan.updates.size)
        assertEquals("suggest-1", plan.updates[0].id)
        assertEquals(listOf("IFOOD", "RAPPI"), plan.updates[0].patterns)
        assertEquals(listOf("suggest-2"), plan.deletes)
    }

    // -------------------------------------------------------------------------
    // Single-rule category → nothing emitted
    // -------------------------------------------------------------------------

    @Test
    fun `plan_singleRuleCategory_nothingEmitted`() {
        val tag = makeTag("tag-a", "ctx-food")
        val rule = makeRule("rule-1", listOf("IFOOD"), listOf(tag))

        val plan = RuleDedupePlanner.plan(listOf(rule))

        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.deletes.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Multi-category rule left alone (safety)
    // -------------------------------------------------------------------------

    @Test
    fun `plan_multiCategoryRule_leftAlone`() {
        val tagFood = makeTag("tag-a", "ctx-food")
        val tagTransport = makeTag("tag-b", "ctx-transport")
        // This rule has two different idContexts → must not be grouped
        val multiRule = makeRule("rule-multi", listOf("AMBIGUOUS"), listOf(tagFood, tagTransport))

        val plan = RuleDedupePlanner.plan(listOf(multiRule))

        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun `plan_multiCategoryRule_notMergedWithSingleCategoryRule_ofSameCategory`() {
        val tagFood = makeTag("tag-a", "ctx-food")
        val tagTransport = makeTag("tag-b", "ctx-transport")
        val multiRule = makeRule("rule-multi", listOf("AMBIGUOUS"), listOf(tagFood, tagTransport))

        val tagFoodOnly = makeTag("tag-c", "ctx-food")
        val singleRule1 = makeRule("rule-1", listOf("IFOOD"), listOf(tagFoodOnly))
        val singleRule2 = makeRule("rule-2", listOf("RAPPI"), listOf(tagFoodOnly))

        val plan = RuleDedupePlanner.plan(listOf(multiRule, singleRule1, singleRule2))

        // multiRule is left alone; only singleRule1+singleRule2 are merged
        assertEquals(1, plan.updates.size)
        assertEquals("rule-1", plan.updates[0].id)
        assertEquals(listOf("IFOOD", "RAPPI"), plan.updates[0].patterns)
        assertEquals(listOf("rule-2"), plan.deletes)
        // multiRule must not appear in deletes
        assertTrue("rule-multi" !in plan.deletes)
    }

    // -------------------------------------------------------------------------
    // Zero-tag rule left alone (safety — no idContext to group by)
    // -------------------------------------------------------------------------

    @Test
    fun `plan_zeroTagRule_leftAlone`() {
        val rule = makeRule("rule-notag", listOf("UNKNOWN"), emptyList())

        val plan = RuleDedupePlanner.plan(listOf(rule))

        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.deletes.isEmpty())
    }

    // -------------------------------------------------------------------------
    // No-op when patterns already canonical/unique (no actual change)
    // -------------------------------------------------------------------------

    @Test
    fun `plan_noUpdateEmitted_whenPatternsAlreadyCanonical`() {
        // Two rules same category, but canonical already has both patterns
        val tag = makeTag("tag-a", "ctx-food")
        val canonical = makeRule("rule-1", listOf("IFOOD", "RAPPI"), listOf(tag))
        val redundant = makeRule("rule-2", listOf("IFOOD"), listOf(tag))

        val plan = RuleDedupePlanner.plan(listOf(canonical, redundant))

        // Canonical already has the union; no update needed for canonical
        // But the redundant rule is still deleted
        // The canonical's patterns after merging "IFOOD" (case-insensitive dup) would be same → no update
        assertEquals(0, plan.updates.size)
        assertEquals(listOf("rule-2"), plan.deletes)
    }

    // -------------------------------------------------------------------------
    // Case-insensitive pattern de-duplication
    // -------------------------------------------------------------------------

    @Test
    fun `plan_caseInsensitivePatternDedup_preservesCanonicalCase`() {
        val tag = makeTag("tag-a", "ctx-food")
        val rule1 = makeRule("rule-1", listOf("IFOOD"), listOf(tag))
        val rule2 = makeRule("rule-2", listOf("ifood"), listOf(tag))

        val plan = RuleDedupePlanner.plan(listOf(rule1, rule2))

        // canonical's patterns: IFOOD. rule-2's ifood is a case-insensitive dup → not added
        // So canonical patterns stay ["IFOOD"] → no change → no update emitted
        assertEquals(0, plan.updates.size)
        // rule-2 is still deleted
        assertEquals(listOf("rule-2"), plan.deletes)
    }

    @Test
    fun `plan_caseInsensitivePatternDedup_newPatternAdded_whenNotDuplicate`() {
        val tag = makeTag("tag-a", "ctx-food")
        val rule1 = makeRule("rule-1", listOf("IFOOD"), listOf(tag))
        val rule2 = makeRule("rule-2", listOf("RAPPI", "ifood"), listOf(tag))

        val plan = RuleDedupePlanner.plan(listOf(rule1, rule2))

        // Union: IFOOD + RAPPI + ifood (dup) → ["IFOOD", "RAPPI"]
        assertEquals(1, plan.updates.size)
        assertEquals("rule-1", plan.updates[0].id)
        assertEquals(listOf("IFOOD", "RAPPI"), plan.updates[0].patterns)
        assertEquals(listOf("rule-2"), plan.deletes)
    }

    // -------------------------------------------------------------------------
    // Empty input
    // -------------------------------------------------------------------------

    @Test
    fun `plan_emptyList_returnsEmptyPlan`() {
        val plan = RuleDedupePlanner.plan(emptyList())

        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.deletes.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Canonical's own patterns come first in merged output
    // -------------------------------------------------------------------------

    @Test
    fun `plan_canonicalPatterns_comeFirstInMergedOutput`() {
        val tag = makeTag("tag-a", "ctx-food")
        val oldest = makeRule("rule-1", listOf("AAA", "BBB"), listOf(tag))
        val newer = makeRule("rule-2", listOf("CCC", "DDD"), listOf(tag))

        val plan = RuleDedupePlanner.plan(listOf(oldest, newer))

        assertEquals(listOf("AAA", "BBB", "CCC", "DDD"), plan.updates[0].patterns)
    }
}
