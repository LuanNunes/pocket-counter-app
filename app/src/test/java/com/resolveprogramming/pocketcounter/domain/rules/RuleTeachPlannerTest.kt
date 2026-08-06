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
    // Update: a SUGGEST rule whose patterns match the notification text — pattern compacted
    // -------------------------------------------------------------------------

    @Test
    fun `plan_ruleMatchingNotification_returnsUpdate_withPatternCompacted`() {
        // "IFOOD CLUB" is subject-related to "IFOOD" (Fix 1's targeting requirement), but also
        // covered by it, so compact drops the taught pattern instead of appending it — "pattern
        // appended" is no longer reachable for a genuinely new merchant, only compaction is.
        val existingRule = makeRule(id = "rule-1", patterns = listOf("IFOOD"), tags = listOf(makeTag(id = "tag-1")))

        val result = RuleTeachPlanner.plan(
            existing = listOf(existingRule),
            notificationText = "Compra IFOOD CLUB aprovada R$ 49,90",
            pattern = "IFOOD CLUB",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(makeTag(id = "tag-2")),
        )

        assertTrue(result is TeachPlan.Update)
        val updated = (result as TeachPlan.Update).rule
        assertEquals("rule-1", updated.id)
        assertEquals(listOf("IFOOD"), updated.patterns)
    }

    @Test
    fun `plan_picksOldestMatchingRule_whenSeveralMatchTheNotification`() {
        // Both rules are subject-related to the taught pattern; among those ELIGIBLE targets, list
        // order (backend creation order, oldest first) decides — not text-match strength.
        val olderRule = makeRule(id = "rule-oldest", patterns = listOf("IFOOD"), tags = listOf(makeTag(id = "tag-1")))
        val newerRule = makeRule(id = "rule-newer", patterns = listOf("IFOOD CLUB"), tags = listOf(makeTag(id = "tag-1")))

        val result = RuleTeachPlanner.plan(
            existing = listOf(olderRule, newerRule),
            notificationText = "Compra IFOOD CLUB aprovada R$ 49,90",
            pattern = "IFOOD CLUB",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(makeTag(id = "tag-2")),
        )

        val updated = (result as TeachPlan.Update).rule
        assertEquals("rule-oldest", updated.id)
        assertEquals(listOf("IFOOD"), updated.patterns)
    }

    // -------------------------------------------------------------------------
    // Fix 1: a gateway-prefix pattern matching the notification TEXT is not enough — the matched
    // pattern must also be the same SUBJECT as the taught pattern. Otherwise teaching an unrelated
    // merchant behind the same payment gateway hijacks the gateway's rule and re-tags every merchant
    // that rule has ever matched.
    // -------------------------------------------------------------------------

    @Test
    fun `plan_gatewayPrefixMatchesNotificationText_butSubjectDiffers_returnsCreate_notUpdate`() {
        val ifoodRule = makeRule(id = "rule-ifood", patterns = listOf("Ifd*", "Ifood"))

        val result = RuleTeachPlanner.plan(
            existing = listOf(ifoodRule),
            notificationText = "Compra IFD*PADARIA DE TESTE aprovada R$ 49,90",
            pattern = "PADARIA DE TESTE",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(makeTag()),
        )

        assertTrue(result is TeachPlan.Create)
        val created = (result as TeachPlan.Create).rule
        assertEquals(listOf("PADARIA DE TESTE"), created.patterns)
    }

    @Test
    fun `plan_taughtPatternStillCarriesTheGatewayPrefix_returnsCreate_notUpdate`() {
        // The taught pattern is NOT pre-stripped here, and must not need to be. BrNotificationParser's
        // ACQUIRER_PREFIX_REGEX (^[\p{L}\p{N}]{1,6}\*) only strips alphanumerics glued straight to the
        // star, so "Mp *" survives into the taught pattern. Plain containment would then call "Mp *"
        // and "Mp *SHUKAI SUSHI" the same subject — a gateway prefix is a literal prefix of every
        // merchant behind it — re-electing the gateway rule, and compact would drop the narrow pattern
        // so ONLY the tags change: the production symptom, bit for bit.
        val gatewayRule = makeRule(id = "rule-gateway", patterns = listOf("Mp *"))

        val result = RuleTeachPlanner.plan(
            existing = listOf(gatewayRule),
            notificationText = "Compra Mp *SHUKAI SUSHI aprovada R$ 49,90",
            pattern = "Mp *SHUKAI SUSHI",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(makeTag()),
        )

        assertTrue(result is TeachPlan.Create)
        val created = (result as TeachPlan.Create).rule
        assertEquals(listOf("Mp *SHUKAI SUSHI"), created.patterns)
    }

    @Test
    fun `plan_conjunctionMustBeEvaluatedOnTheSamePattern_notAsTwoIndependentAnyChecks`() {
        // Regression pin: "Ifd*" matches the notification TEXT, and "PADARIA" (a different pattern on
        // the SAME rule) is subject-related to the taught pattern — but neither condition holds on the
        // SAME pattern. A wrongly split predicate,
        // `rule.patterns.any { matches } && rule.patterns.any { sameSubject }`, would treat those two
        // independent truths as sufficient and re-elect this rule, reopening the hijack this fix closes.
        val rule = makeRule(id = "rule-mixed", patterns = listOf("Ifd*", "PADARIA"))

        val result = RuleTeachPlanner.plan(
            existing = listOf(rule),
            notificationText = "Compra IFD*OUTRA LOJA aprovada R$ 49,90",
            pattern = "PADARIA DE TESTE",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(makeTag()),
        )

        assertTrue(result is TeachPlan.Create)
    }

    @Test
    fun `plan_oldestMatchingRuleIsSubjectUnrelated_newerSubjectRelatedRuleIsTargetedInstead`() {
        val olderRule = makeRule(id = "rule-oldest", patterns = listOf("Ifd*"))
        val newerRule = makeRule(id = "rule-newer", patterns = listOf("PADARIA"))

        val result = RuleTeachPlanner.plan(
            existing = listOf(olderRule, newerRule),
            notificationText = "Compra IFD*PADARIA DE TESTE aprovada R$ 49,90",
            pattern = "PADARIA DE TESTE",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(makeTag(id = "tag-2")),
        )

        assertTrue(result is TeachPlan.Update)
        val updated = (result as TeachPlan.Update).rule
        assertEquals("rule-newer", updated.id)
        // "PADARIA" already covers the taught pattern, so compact drops it: an Update keeps the
        // broadest pattern of the subject rather than growing the list.
        assertEquals(listOf("PADARIA"), updated.patterns)
    }

    @Test
    fun `plan_ordinaryPatternMatchesNotificationText_butSubjectDiffers_returnsCreate_notUpdate`() {
        // Non-gateway form of the hijack this branch fixes: "COMPRA" is a common word, not a bare
        // gateway marker, so isGatewayMarker never fires here — only the containment leg of
        // sameSubject stands between an ordinary common-word pattern and absorbing every teach behind
        // it. The rule's pattern DOES match the notification text; that must not be enough on its own.
        val commonWordRule = makeRule(id = "rule-compra", patterns = listOf("COMPRA"))

        val result = RuleTeachPlanner.plan(
            existing = listOf(commonWordRule),
            notificationText = "Compra RAPPI aprovada R$ 49,90",
            pattern = "RAPPI",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(makeTag()),
        )

        assertTrue(result is TeachPlan.Create)
        val created = (result as TeachPlan.Create).rule
        assertEquals(listOf("RAPPI"), created.patterns)
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
            notificationText = "Compra IFOOD CLUB aprovada R$ 49,90",
            pattern = "IFOOD CLUB",
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
            notificationText = "Compra IFOOD CLUB aprovada R$ 49,90",
            pattern = "IFOOD CLUB",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(makeTag(id = "tag-2")),
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
        // Subject-related to the taught pattern (IFOOD / IFOOD CLUB) on purpose: with unrelated
        // filler data (SPAM/IFOOD) this would return Create for the wrong reason (subject mismatch)
        // and prove nothing about the ACTION filter in isolation.
        val ignoreRule = makeRule(
            id = "ignore-rule",
            patterns = listOf("IFOOD"),
            action = RuleAction.IGNORE,
        )

        val result = RuleTeachPlanner.plan(
            existing = listOf(ignoreRule),
            notificationText = "Compra IFOOD CLUB aprovada R$ 49,90",
            pattern = "IFOOD CLUB",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(makeTag()),
        )

        assertTrue(result is TeachPlan.Create)
        val created = (result as TeachPlan.Create).rule
        assertEquals(listOf("IFOOD CLUB"), created.patterns)
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
            notificationText = "Compra IFOOD CLUB aprovada R$ 49,90",
            pattern = "IFOOD CLUB",
            type = TransactionType.EXPENSE,
            paymentMethod = null,
            cardId = null,
            tags = listOf(makeTag(id = "tag-2")),
        )

        assertEquals("rule-1", (result as TeachPlan.Update).rule.id)
    }

    // -------------------------------------------------------------------------
    // Root cause 4 regression: a rule whose patterns don't actually match the notification text must
    // NOT be chosen as target — even when it is subject-related to the taught pattern. Tightened to
    // isolate the text-match leg: "Uber Eats" and "Uber" ARE the same subject (sameSubject would say
    // true), so this can only pass because the text-match conjunct independently fails.
    // -------------------------------------------------------------------------

    @Test
    fun `plan_sameContext_butPatternsDontMatchNotification_returnsCreate_notUpdate`() {
        val foodTag = makeTag(id = "tag-food", idContext = "ctx-food")
        val existingRule = makeRule(id = "rule-uber-eats", patterns = listOf("Uber Eats"), tags = listOf(foodTag))

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
