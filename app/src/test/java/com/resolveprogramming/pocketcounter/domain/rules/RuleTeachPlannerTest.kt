package com.resolveprogramming.pocketcounter.domain.rules

import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
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
        paymentMethod: PaymentMethod? = null,
        cardId: String? = null,
        transactionType: TransactionType? = TransactionType.EXPENSE,
    ) = ClassificationRule(
        id = id,
        patterns = patterns,
        matchType = "CONTAINS",
        active = true,
        appliedCount = 0,
        transactionType = transactionType,
        paymentMethod = paymentMethod,
        cardId = cardId,
        tags = tags,
        action = action,
    )

    // -------------------------------------------------------------------------
    // Create: no rule's patterns match the notification text
    // -------------------------------------------------------------------------

    @Test
    fun `plan_noRuleMatchesNotification_returnsCreate`() {
        val tags = listOf(makeTag(id = "tag-1", idContext = "ctx-food"))

        val result = RuleTeachPlanner.plan(
            existing = emptyList(),
            notificationText = "Compra IFOOD aprovada R$ 49,90",
            pattern = "IFOOD",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = tags,
        )

        assertTrue(result is TeachPlan.Create)
        val created = (result as TeachPlan.Create).rule
        assertEquals(listOf("IFOOD"), created.patterns)
        assertEquals(tags, created.tags)
        assertEquals(TransactionType.EXPENSE, created.transactionType)
        assertEquals(RuleAction.SUGGEST, created.action)
        assertNull(created.id)
    }

    @Test
    fun `plan_createRule_matchTypeIsContains`() {
        val result = RuleTeachPlanner.plan(
            existing = emptyList(),
            notificationText = "Compra IFOOD aprovada R$ 49,90",
            pattern = "IFOOD",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(makeTag()),
        )

        assertEquals("CONTAINS", (result as TeachPlan.Create).rule.matchType)
    }

    // -------------------------------------------------------------------------
    // Create: draft payment method / card carried on the new rule
    // -------------------------------------------------------------------------

    @Test
    fun `plan_create_carriesDraftPaymentMethodAndCard`() {
        val result = RuleTeachPlanner.plan(
            existing = emptyList(),
            notificationText = "Compra IFOOD aprovada R$ 49,90",
            pattern = "IFOOD",
            type = TransactionType.EXPENSE,
            paymentMethod = PaymentMethod.CREDIT,
            cardId = "card-x",
            tags = listOf(makeTag()),
        )

        val created = (result as TeachPlan.Create).rule
        assertEquals(PaymentMethod.CREDIT, created.paymentMethod)
        assertEquals("card-x", created.cardId)
    }

    @Test
    fun `plan_create_dropsCardId_whenMethodIsNotCredit`() {
        val result = RuleTeachPlanner.plan(
            existing = emptyList(),
            notificationText = "Compra IFOOD aprovada R$ 49,90",
            pattern = "IFOOD",
            type = TransactionType.EXPENSE,
            paymentMethod = PaymentMethod.PIX,
            cardId = "card-x",
            tags = listOf(makeTag()),
        )

        val created = (result as TeachPlan.Create).rule
        assertEquals(PaymentMethod.PIX, created.paymentMethod)
        assertNull(created.cardId)
    }

    // -------------------------------------------------------------------------
    // Update: a SUGGEST rule whose patterns match the notification text — pattern appended
    // -------------------------------------------------------------------------

    @Test
    fun `plan_ruleMatchingNotification_returnsUpdate_withPatternAppended`() {
        val existingRule = makeRule(id = "rule-1", patterns = listOf("IFOOD"))

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            notificationText = "Compra IFOOD aprovada R$ 49,90",
            pattern = "RAPPI",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(makeTag()),
        )

        assertTrue(result is TeachPlan.Update)
        val updated = (result as TeachPlan.Update).rule
        assertEquals("rule-1", updated.id)
        assertEquals(listOf("IFOOD", "RAPPI"), updated.patterns)
    }

    @Test
    fun `plan_picksOldestMatchingRule_whenSeveralMatchTheNotification`() {
        val olderRule = makeRule(id = "rule-oldest", patterns = listOf("IFOOD"))
        val newerRule = makeRule(id = "rule-newer", patterns = listOf("COMPRA"))

        val result = RuleTeachPlanner.plan(
            existing = listOf(olderRule, newerRule),
            notificationText = "Compra IFOOD aprovada R$ 49,90",
            pattern = "UBER EATS",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(makeTag()),
        )

        val updated = (result as TeachPlan.Update).rule
        assertEquals("rule-oldest", updated.id)
        assertEquals(listOf("IFOOD", "UBER EATS"), updated.patterns)
    }

    // -------------------------------------------------------------------------
    // Update: payment method / card overwritten as a pair, tags overwritten not unioned
    // -------------------------------------------------------------------------

    @Test
    fun `plan_update_overwritesPaymentMethodAndCard_whenDraftHasThem`() {
        val existingRule = makeRule(
            id = "rule-1",
            patterns = listOf("IFOOD"),
            paymentMethod = null,
            cardId = null,
        )

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            notificationText = "Compra IFOOD aprovada R$ 49,90",
            pattern = "RAPPI",
            type = TransactionType.EXPENSE,
            paymentMethod = PaymentMethod.CREDIT,
            cardId = "card-y",
            tags = listOf(makeTag()),
        )

        val updated = (result as TeachPlan.Update).rule
        assertEquals(PaymentMethod.CREDIT, updated.paymentMethod)
        assertEquals("card-y", updated.cardId)
    }

    @Test
    fun `plan_update_keepsExistingMethod_whenDraftMethodIsNull`() {
        val existingRule = makeRule(
            id = "rule-1",
            patterns = listOf("IFOOD"),
            paymentMethod = PaymentMethod.CREDIT,
            cardId = "card-old",
        )

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            notificationText = "Compra IFOOD aprovada R$ 49,90",
            pattern = "RAPPI",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(makeTag()),
        )

        val updated = (result as TeachPlan.Update).rule
        assertEquals(PaymentMethod.CREDIT, updated.paymentMethod)
        assertEquals("card-old", updated.cardId)
    }

    @Test
    fun `plan_update_overwritesTags_notUnioned`() {
        val originalTag = makeTag(id = "tag-old", idContext = "ctx-food")
        val taughtTag = makeTag(id = "tag-new", idContext = "ctx-food")
        val existingRule = makeRule(id = "rule-1", patterns = listOf("IFOOD"), tags = listOf(originalTag))

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            notificationText = "Compra IFOOD aprovada R$ 49,90",
            pattern = "IFOOD",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(taughtTag),
        )

        val updated = (result as TeachPlan.Update).rule
        assertEquals(listOf(taughtTag), updated.tags)
    }

    // -------------------------------------------------------------------------
    // IGNORE rules never chosen as target, even when they match the notification
    // -------------------------------------------------------------------------

    @Test
    fun `an IGNORE rule matching the notification is not a teach target`() {
        val ignoreRule = makeRule(
            id = "ignore-rule",
            patterns = listOf("SPAM"),
            action = RuleAction.IGNORE,
        )

        val result = RuleTeachPlanner.plan(
            existing = listOf(ignoreRule),
            notificationText = "Compra SPAM aprovada R$ 49,90",
            pattern = "IFOOD",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(makeTag()),
        )

        assertTrue(result is TeachPlan.Create)
        val created = (result as TeachPlan.Create).rule
        assertEquals(listOf("IFOOD"), created.patterns)
    }

    // -------------------------------------------------------------------------
    // A rule carrying a blank pattern is never a target: "" is contained by every text, so it would
    // absorb every teach and have its tags overwritten each time.
    // -------------------------------------------------------------------------

    @Test
    fun `plan_ruleWithBlankPattern_isNotATarget`() {
        val blankPatternRule = makeRule(id = "rule-blank", patterns = listOf(""))

        val result = RuleTeachPlanner.plan(
            existing = listOf(blankPatternRule),
            notificationText = "Compra IFOOD aprovada R$ 49,90",
            pattern = "IFOOD",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(makeTag()),
        )

        assertTrue(result is TeachPlan.Create)
        assertEquals(listOf("IFOOD"), (result as TeachPlan.Create).rule.patterns)
    }

    // -------------------------------------------------------------------------
    // A deactivated rule is never a target: classify skips it, so teaching it would silently
    // do nothing.
    // -------------------------------------------------------------------------

    @Test
    fun `plan_inactiveRuleMatchingNotification_isNotATarget`() {
        val inactiveRule = makeRule(id = "rule-off", patterns = listOf("IFOOD")).copy(active = false)

        val result = RuleTeachPlanner.plan(
            existing = listOf(inactiveRule),
            notificationText = "Compra IFOOD aprovada R$ 49,90",
            pattern = "IFOOD",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(makeTag()),
        )

        assertTrue(result is TeachPlan.Create)
    }

    @Test
    fun `plan_ruleWithUnknownActiveFlag_isStillATarget`() {
        val rule = makeRule(id = "rule-1", patterns = listOf("IFOOD")).copy(active = null)

        val result = RuleTeachPlanner.plan(
            existing = listOf(rule),
            notificationText = "Compra IFOOD aprovada R$ 49,90",
            pattern = "RAPPI",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(makeTag()),
        )

        assertEquals("rule-1", (result as TeachPlan.Update).rule.id)
    }

    // -------------------------------------------------------------------------
    // Root cause 4 regression: a rule sharing the taught tag's context must NOT be chosen as
    // target when its patterns don't actually match the notification text.
    // -------------------------------------------------------------------------

    @Test
    fun `plan_sameContext_butPatternsDontMatchNotification_returnsCreate_notUpdate`() {
        val foodTag = makeTag(id = "tag-food", idContext = "ctx-food")
        val existingRule = makeRule(id = "rule-ifood", patterns = listOf("IFOOD"), tags = listOf(foodTag))

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            notificationText = "UBER trip",
            pattern = "Uber",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(foodTag),
        )

        assertTrue(result is TeachPlan.Create)
    }

    // -------------------------------------------------------------------------
    // NoOp: pattern already covered AND method/tags/type identical
    // -------------------------------------------------------------------------

    @Test
    fun `plan_exactDuplicatePattern_returnsNoOp`() {
        val tag = makeTag()
        val existingRule = makeRule(patterns = listOf("IFOOD"), tags = listOf(tag))

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            notificationText = "Compra IFOOD aprovada R$ 49,90",
            pattern = "IFOOD",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(tag),
        )

        assertTrue(result is TeachPlan.NoOp)
    }

    @Test
    fun `plan_exactDuplicatePattern_caseInsensitive_returnsNoOp`() {
        val tag = makeTag()
        val existingRule = makeRule(patterns = listOf("ifood"), tags = listOf(tag))

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            notificationText = "Compra IFOOD aprovada R$ 49,90",
            pattern = "IFOOD",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(tag),
        )

        assertTrue(result is TeachPlan.NoOp)
    }

    @Test
    fun `plan_newPatternContainedByExistingSubstring_returnsNoOp`() {
        val tag = makeTag(idContext = "ctx-transport")
        val existingRule = makeRule(patterns = listOf("Uber"), tags = listOf(tag))

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            notificationText = "Compra UberRides aprovada R$ 49,90",
            pattern = "UberRides",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(tag),
        )

        assertTrue(result is TeachPlan.NoOp)
    }

    @Test
    fun `plan_newPatternContainsCoverage_caseInsensitive_returnsNoOp`() {
        val tag = makeTag(idContext = "ctx-transport")
        val existingRule = makeRule(patterns = listOf("uber"), tags = listOf(tag))

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            notificationText = "Compra UberRides aprovada R$ 49,90",
            pattern = "UBERRIDES",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(tag),
        )

        assertTrue(result is TeachPlan.NoOp)
    }

    @Test
    fun `plan_whitespaceVariantOfExistingPattern_returnsUpdate_keepingBothPatterns`() {
        // A differing run of spaces is a genuinely different literal to a whitespace-sensitive
        // CONTAINS, so the taught pattern is stored alongside the existing one instead of being
        // swallowed as "already covered" — compact must never delete a pattern on a guess.
        val tag = makeTag(idContext = "ctx-transport")
        val existingRule = makeRule(patterns = listOf("DL *UberRides"), tags = listOf(tag))

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            notificationText = "Compra DL *UberRides aprovada R$ 49,90",
            pattern = "DL     *UberRides",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(tag),
        )

        val updated = (result as TeachPlan.Update).rule
        assertEquals(listOf("DL *UberRides", "DL     *UberRides"), updated.patterns)
    }

    @Test
    fun `plan_caseVariantOfExistingPattern_returnsNoOp`() {
        val tag = makeTag(idContext = "ctx-transport")
        val existingRule = makeRule(patterns = listOf("DL *UberRides"), tags = listOf(tag))

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            notificationText = "Compra DL *UberRides aprovada R$ 49,90",
            pattern = "dl *uberrides",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(tag),
        )

        assertTrue(result is TeachPlan.NoOp)
    }

    @Test
    fun `plan_existingBroaderPatternCoversTaughtVariant_returnsNoOp`() {
        val tag = makeTag(idContext = "ctx-transport")
        val existingRule = makeRule(patterns = listOf("Uber"), tags = listOf(tag))

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            notificationText = "Compra DL*UberRides aprovada R$ 49,90",
            pattern = "DL*UberRides",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(tag),
        )

        assertTrue(result is TeachPlan.NoOp)
    }

    // -------------------------------------------------------------------------
    // NoOp compares tags on identity (id + context), not on whole Tag objects
    // -------------------------------------------------------------------------

    @Test
    fun `plan_sameTagIdentity_butDifferentTagNameAndColor_returnsNoOp`() {
        // Rules loaded from the backend carry tag stubs (blank name, no color); the taught tags come
        // from the tag list fully populated. Comparing whole Tag objects would make every save issue
        // a redundant PUT for a rule nothing changed on.
        val stub = Tag(id = "tag-1", name = "", kind = TransactionType.EXPENSE, idContext = "ctx-food")
        val populated = stub.copy(name = "Alimentação", color = 0xFF00FF00)
        val existingRule = makeRule(patterns = listOf("IFOOD"), tags = listOf(stub))

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            notificationText = "Compra IFOOD aprovada R$ 49,90",
            pattern = "IFOOD",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(populated),
        )

        assertTrue(result is TeachPlan.NoOp)
    }

    @Test
    fun `plan_differentTagId_returnsUpdate`() {
        val existingRule = makeRule(patterns = listOf("IFOOD"), tags = listOf(makeTag(id = "tag-1")))

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            notificationText = "Compra IFOOD aprovada R$ 49,90",
            pattern = "IFOOD",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(makeTag(id = "tag-2")),
        )

        assertTrue(result is TeachPlan.Update)
    }

    // -------------------------------------------------------------------------
    // Known consequence of overwrite-not-union: teaching a sub-merchant re-tags the whole merchant.
    // Pinned so a future change can't alter it silently.
    // -------------------------------------------------------------------------

    @Test
    fun `plan_teachingASubMerchantOfAnExistingRule_retagsTheWholeMerchant`() {
        val ridesTag = makeTag(id = "tag-rides", name = "Transporte", idContext = "ctx-transport")
        val eatsTag = makeTag(id = "tag-eats", name = "Alimentação", idContext = "ctx-food")
        val uberRule = makeRule(id = "rule-uber", patterns = listOf("Uber"), tags = listOf(ridesTag))

        val result = RuleTeachPlanner.plan(
            existing = listOf(uberRule),
            notificationText = "Compra UBER EATS aprovada R$ 49,90",
            pattern = "UBER EATS",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(eatsTag),
        )

        val updated = (result as TeachPlan.Update).rule
        assertEquals("rule-uber", updated.id)
        // The narrower pattern is dropped as already covered and the tags are replaced, so every
        // Uber ride is now tagged as Uber Eats. Escape hatch: delete the rule in Regras.
        assertEquals(listOf("Uber"), updated.patterns)
        assertEquals(listOf(eatsTag), updated.tags)
    }

    // -------------------------------------------------------------------------
    // Pattern covered but a non-pattern field differs → still an Update
    // -------------------------------------------------------------------------

    @Test
    fun `plan_patternCovered_butPaymentMethodDiffers_returnsUpdate`() {
        val tag = makeTag(idContext = "ctx-transport")
        val existingRule = makeRule(
            patterns = listOf("Uber"),
            tags = listOf(tag),
            paymentMethod = null,
            cardId = null,
        )

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            notificationText = "Compra UberRides aprovada R$ 49,90",
            pattern = "UberRides",
            type = TransactionType.EXPENSE,
            paymentMethod = PaymentMethod.CREDIT,
            cardId = "card-x",
            tags = listOf(tag),
        )

        assertTrue(result is TeachPlan.Update)
        val updated = (result as TeachPlan.Update).rule
        assertEquals(listOf("Uber"), updated.patterns)
        assertEquals(PaymentMethod.CREDIT, updated.paymentMethod)
        assertEquals("card-x", updated.cardId)
    }
}
