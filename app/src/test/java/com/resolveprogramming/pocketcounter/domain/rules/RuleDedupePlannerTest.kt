package com.resolveprogramming.pocketcounter.domain.rules

import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
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
        tags: List<Tag> = emptyList(),
        action: RuleAction = RuleAction.SUGGEST,
        paymentMethod: PaymentMethod? = null,
        cardId: String? = null,
        transactionType: TransactionType? = TransactionType.EXPENSE,
        appliedCount: Int = 0,
    ) = ClassificationRule(
        id = id,
        patterns = patterns,
        matchType = "CONTAINS",
        active = true,
        appliedCount = appliedCount,
        transactionType = transactionType,
        paymentMethod = paymentMethod,
        cardId = cardId,
        tags = tags,
        action = action,
    )

    // -------------------------------------------------------------------------
    // Root cause 4 regression: rules sharing a tag context but no pattern overlap must NOT
    // be merged just because they share a category — they're distinct merchants.
    // -------------------------------------------------------------------------

    @Test
    fun `plan_twoCategorySinglePatternRules_notMerged`() {
        val tag1 = makeTag("tag-a", "ctx-food")
        val tag2 = makeTag("tag-b", "ctx-food")
        val oldest = makeRule("rule-1", listOf("IFOOD"), listOf(tag1))
        val newer = makeRule("rule-2", listOf("RAPPI"), listOf(tag2))

        val plan = RuleDedupePlanner.plan(listOf(oldest, newer))

        // "IFOOD" and "RAPPI" don't overlap — sharing a tag context is not enough to merge.
        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun `plan_threeCategoryRules_notMerged`() {
        val tag = makeTag("tag-a", "ctx-food")
        val rule1 = makeRule("rule-1", listOf("IFOOD"), listOf(tag))
        val rule2 = makeRule("rule-2", listOf("RAPPI"), listOf(tag))
        val rule3 = makeRule("rule-3", listOf("BURGER KING"), listOf(tag))

        val plan = RuleDedupePlanner.plan(listOf(rule1, rule2, rule3))

        // None of these merchant patterns overlap with each other.
        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun `plan_sameContextDifferentMerchants_notMerged`() {
        val tag = makeTag("tag-a", "ctx-subscriptions")
        val netflix = makeRule("rule-netflix", listOf("NETFLIX"), listOf(tag))
        val spotify = makeRule("rule-spotify", listOf("SPOTIFY"), listOf(tag))

        val plan = RuleDedupePlanner.plan(listOf(netflix, spotify))

        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.deletes.isEmpty())
    }

    // -------------------------------------------------------------------------
    // IGNORE rules are never touched, even when their patterns would otherwise overlap
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
        val tag = makeTag("tag-a", "ctx-transport")
        val suggestRule1 = makeRule("suggest-1", listOf("Uber"), listOf(tag))
        val suggestRule2 = makeRule("suggest-2", listOf("UberRides"), listOf(tag))
        val ignoreRule = makeRule("ignore-1", listOf("Uber"), listOf(tag), action = RuleAction.IGNORE)

        val plan = RuleDedupePlanner.plan(listOf(suggestRule1, suggestRule2, ignoreRule))

        // "UberRides" compacts away against the canonical's own "Uber", so merged == canonical:
        // no update is emitted, only the redundant rule is deleted.
        assertTrue(plan.updates.isEmpty())
        assertEquals(listOf("suggest-2"), plan.deletes)
        assertTrue("ignore-1" !in plan.deletes)
    }

    // -------------------------------------------------------------------------
    // Zero-pattern rules are left alone
    // -------------------------------------------------------------------------

    @Test
    fun `plan_zeroPatternRule_leftAlone`() {
        val rule = makeRule("rule-nopattern", emptyList())

        val plan = RuleDedupePlanner.plan(listOf(rule))

        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.deletes.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Overlapping patterns → merge oldest as canonical, delete the rest
    // -------------------------------------------------------------------------

    @Test
    fun `plan_canonicalPatterns_comeFirstInMergedOutput`() {
        val tag = makeTag("tag-a", "ctx-transport")
        val oldest = makeRule("rule-1", listOf("AAA", "Uber"), listOf(tag))
        val newer = makeRule("rule-2", listOf("UberRides", "DDD"), listOf(tag))

        val plan = RuleDedupePlanner.plan(listOf(oldest, newer))

        assertEquals(1, plan.updates.size)
        assertEquals("rule-1", plan.updates[0].id)
        // "UberRides" is covered by "Uber" and dropped; canonical's own patterns lead.
        assertEquals(listOf("AAA", "Uber", "DDD"), plan.updates[0].patterns)
        assertEquals(listOf("rule-2"), plan.deletes)
    }

    // -------------------------------------------------------------------------
    // Single-rule "category" → nothing emitted, unless its own patterns are redundant
    // -------------------------------------------------------------------------

    @Test
    fun `plan_singleRuleCategory_nothingEmitted`() {
        val tag = makeTag("tag-a", "ctx-food")
        val rule = makeRule("rule-1", listOf("IFOOD"), listOf(tag))

        val plan = RuleDedupePlanner.plan(listOf(rule))

        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun `plan_singleRuleWithRedundantPatterns_emitsUpdate_andZeroDeletes`() {
        val tag = makeTag("tag-a", "ctx-transport")
        val rule = makeRule("rule-1", listOf("Uber", "UberRides"), listOf(tag))

        val plan = RuleDedupePlanner.plan(listOf(rule))

        assertEquals(1, plan.updates.size)
        assertEquals("rule-1", plan.updates[0].id)
        assertEquals(listOf("Uber"), plan.updates[0].patterns)
        assertTrue(plan.deletes.isEmpty())
    }

    // -------------------------------------------------------------------------
    // No-op when patterns already canonical (no actual change) — the redundant rule is
    // still deleted since it's part of the merged group.
    // -------------------------------------------------------------------------

    @Test
    fun `plan_noUpdateEmitted_whenPatternsAlreadyCanonical`() {
        val tag = makeTag("tag-a", "ctx-food")
        val canonical = makeRule("rule-1", listOf("IFOOD", "RAPPI"), listOf(tag))
        val redundant = makeRule("rule-2", listOf("IFOOD"), listOf(tag))

        val plan = RuleDedupePlanner.plan(listOf(canonical, redundant))

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

        assertEquals(0, plan.updates.size)
        assertEquals(listOf("rule-2"), plan.deletes)
    }

    @Test
    fun `plan_caseInsensitivePatternDedup_newPatternAdded_whenNotDuplicate`() {
        val tag = makeTag("tag-a", "ctx-food")
        val rule1 = makeRule("rule-1", listOf("IFOOD"), listOf(tag))
        val rule2 = makeRule("rule-2", listOf("RAPPI", "ifood"), listOf(tag))

        val plan = RuleDedupePlanner.plan(listOf(rule1, rule2))

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
    // Transitive overlap: connected components, not just pairwise-adjacent rules
    // -------------------------------------------------------------------------

    @Test
    fun `plan_transitiveOverlap_groupsThreeRulesIntoOneComponent`() {
        val tag = makeTag("tag-a", "ctx-transport")
        val ruleA = makeRule("rule-a", listOf("Uber"), listOf(tag))
        val ruleB = makeRule("rule-b", listOf("UberRides", "Foo"), listOf(tag))
        val ruleC = makeRule("rule-c", listOf("Foo Extended"), listOf(tag))

        // ruleA overlaps ruleB via Uber/UberRides; ruleB overlaps ruleC via Foo/Foo Extended;
        // ruleA does NOT directly overlap ruleC — but all three must land in one component.
        val plan = RuleDedupePlanner.plan(listOf(ruleA, ruleB, ruleC))

        assertEquals(1, plan.updates.size)
        assertEquals("rule-a", plan.updates[0].id)
        assertEquals(listOf("Uber", "Foo"), plan.updates[0].patterns)
        assertEquals(setOf("rule-b", "rule-c"), plan.deletes.toSet())
    }

    // -------------------------------------------------------------------------
    // Merged payment method / card / type / tags: taken from the newest member that carries
    // them, not unioned — and the canonical's own id/appliedCount always survive.
    // -------------------------------------------------------------------------

    @Test
    fun `plan_mergedPaymentMethodAndCard_takenFromLastNonNullMember`() {
        val tag = makeTag("tag-a", "ctx-transport")
        val oldest = makeRule("rule-1", listOf("Uber"), listOf(tag), paymentMethod = null, cardId = null)
        val newer = makeRule(
            "rule-2",
            listOf("UberRides"),
            listOf(tag),
            paymentMethod = PaymentMethod.CREDIT,
            cardId = "card-z",
        )

        val plan = RuleDedupePlanner.plan(listOf(oldest, newer))

        assertEquals(PaymentMethod.CREDIT, plan.updates[0].paymentMethod)
        assertEquals("card-z", plan.updates[0].cardId)
    }

    @Test
    fun `plan_mergedCardId_nulledWhenWinningMethodIsNotCredit`() {
        val tag = makeTag("tag-a", "ctx-transport")
        val oldest = makeRule(
            "rule-1",
            listOf("Uber"),
            listOf(tag),
            paymentMethod = PaymentMethod.CREDIT,
            cardId = "card-old",
        )
        val newer = makeRule(
            "rule-2",
            listOf("UberRides"),
            listOf(tag),
            paymentMethod = PaymentMethod.PIX,
            cardId = "should-not-survive",
        )

        val plan = RuleDedupePlanner.plan(listOf(oldest, newer))

        assertEquals(PaymentMethod.PIX, plan.updates[0].paymentMethod)
        assertEquals(null, plan.updates[0].cardId)
    }

    @Test
    fun `plan_mergedTags_takenFromLastNonEmptyMember_notUnioned`() {
        val tagOld = makeTag("tag-old", "ctx-transport")
        val tagNew = makeTag("tag-new", "ctx-transport")
        val oldest = makeRule("rule-1", listOf("Uber"), listOf(tagOld))
        val newer = makeRule("rule-2", listOf("UberRides"), listOf(tagNew))

        val plan = RuleDedupePlanner.plan(listOf(oldest, newer))

        assertEquals(listOf(tagNew), plan.updates[0].tags)
    }

    @Test
    fun `plan_mergedRule_keepsCanonicalIdAndAppliedCount`() {
        val tag = makeTag("tag-a", "ctx-transport")
        val oldest = makeRule("rule-1", listOf("Uber"), listOf(tag), appliedCount = 224)
        val newer = makeRule(
            "rule-2",
            listOf("UberRides"),
            listOf(tag),
            paymentMethod = PaymentMethod.CREDIT,
            cardId = "card-z",
            appliedCount = 5,
        )

        val plan = RuleDedupePlanner.plan(listOf(oldest, newer))

        assertEquals("rule-1", plan.updates[0].id)
        assertEquals(224, plan.updates[0].appliedCount)
        assertEquals(PaymentMethod.CREDIT, plan.updates[0].paymentMethod)
    }

    // -------------------------------------------------------------------------
    // Short-pattern adjacency guard: a pattern under 3 normalized chars must not chain
    // otherwise-unrelated rules together.
    // -------------------------------------------------------------------------

    @Test
    fun `plan_shortPatternGuard_doesNotChainUnrelatedRules`() {
        val tag = makeTag("tag-a", "ctx-misc")
        val ruleX = makeRule("rule-x", listOf("Xyz", "ab"), listOf(tag))
        val ruleY = makeRule("rule-y", listOf("ab", "Qrs"), listOf(tag))

        val plan = RuleDedupePlanner.plan(listOf(ruleX, ruleY))

        // The only shared pattern is "ab" (normalized length 2) — too short to count as adjacency.
        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.deletes.isEmpty())
    }

    // -------------------------------------------------------------------------
    // The headline case: the user's real Uber data. Three rules sharing tag Uber, one with
    // seven redundant merchant-name variants and two later duplicates each carrying a payment
    // method/card the older rule never learned. Must collapse to exactly one rule.
    // -------------------------------------------------------------------------

    @Test
    fun `plan_realUberData_collapsesToOneRule_keepingOldestIdAndAppliedCount_andNewestPaymentMethod`() {
        val uberTag = makeTag("tag-uber", "ctx-transport")
        val r1 = makeRule(
            id = "rule-uber-r1",
            patterns = listOf(
                "Uber",
                "UberRides",
                "DL *UberRides",
                "DL*UberRides",
                "DL     *UberRides",
                "Uber UBER *TRIP HELP.U",
                "UBER *ONE MEMBERSHIP U",
            ),
            tags = listOf(uberTag),
            appliedCount = 224,
        )
        val r2 = makeRule(
            id = "rule-uber-r2",
            patterns = listOf("DL*UberRides"),
            tags = listOf(uberTag),
            paymentMethod = PaymentMethod.CREDIT,
            cardId = "itau",
        )
        val r3 = makeRule(
            id = "rule-uber-r3",
            patterns = listOf("DL*UberRides"),
            tags = listOf(uberTag),
            paymentMethod = PaymentMethod.CREDIT,
            cardId = "itau",
        )

        val plan = RuleDedupePlanner.plan(listOf(r1, r2, r3))

        assertEquals(1, plan.updates.size)
        val survivor = plan.updates[0]
        assertEquals("rule-uber-r1", survivor.id)
        assertEquals(224, survivor.appliedCount)
        assertEquals(listOf("Uber"), survivor.patterns)
        assertEquals(TransactionType.EXPENSE, survivor.transactionType)
        assertEquals(PaymentMethod.CREDIT, survivor.paymentMethod)
        assertEquals("itau", survivor.cardId)
        assertEquals(listOf(uberTag), survivor.tags)
        assertEquals(setOf("rule-uber-r2", "rule-uber-r3"), plan.deletes.toSet())
    }
}
