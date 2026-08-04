package com.resolveprogramming.pocketcounter.domain.rules

import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.RuleAction
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TransactionType

sealed interface TeachPlan {
    data object NoOp : TeachPlan
    data class Update(val rule: ClassificationRule) : TeachPlan
    data class Create(val rule: ClassificationRule) : TeachPlan
}

object RuleTeachPlanner {

    /**
     * Decides how teaching [pattern] out of [notificationText] should mutate the rule set.
     *
     * The target is the FIRST active SUGGEST rule in [existing] whose patterns match
     * [notificationText]. [existing] is assumed to be in backend creation order (oldest first), so
     * that rule is by definition the one classify applied to this notification — classify runs
     * exactly one rule and the oldest match wins — which makes it the only rule whose correction the
     * user will actually see next time. Rules that merely share the taught tags' context are not
     * targets: same category, different merchant. IGNORE and deactivated rules are never targets —
     * classify skips them, so teaching one would silently do nothing. No match → a fresh rule.
     *
     * Known consequence of overwriting tags instead of unioning them: teaching a SUB-MERCHANT of an
     * existing rule re-tags the whole merchant. Teaching "UBER EATS" while a rule matches "Uber"
     * targets that rule, [RulePatterns.compact] drops the narrower new pattern as already covered,
     * and the taught tags replace the old ones — so Uber rides start being tagged as Uber Eats. The
     * escape hatch is deleting the rule in Regras and teaching the two merchants separately; a union
     * would instead make a wrong tag impossible to remove through the wizard, which is how the rule
     * set drifted in the first place.
     */
    fun plan(
        existing: List<ClassificationRule>,
        notificationText: String,
        pattern: String,
        type: TransactionType?,
        paymentMethod: PaymentMethod?,
        cardId: String?,
        tags: List<Tag>,
    ): TeachPlan {
        val target = existing.firstOrNull { rule ->
            rule.action == RuleAction.SUGGEST &&
                rule.active != false &&
                rule.patterns.any { RulePatterns.matches(it, notificationText) }
        } ?: return TeachPlan.Create(
            ClassificationRule.learned(pattern, type, paymentMethod, cardId, tags),
        )

        val candidate = target.copy(
            patterns = RulePatterns.compact(target.patterns + pattern),
            // Never null out a type the rule already learned.
            transactionType = type ?: target.transactionType,
            paymentMethod = paymentMethod ?: target.paymentMethod,
            cardId = resolveCardId(paymentMethod, cardId, target.cardId),
            // Overwritten, not unioned: a union would make a wrong tag impossible to remove through
            // the wizard, which is exactly how the rule set drifted in the first place.
            tags = tags,
        )
        if (isNoOp(candidate, target)) return TeachPlan.NoOp
        return TeachPlan.Update(candidate)
    }

    /**
     * Tags compare on identity ([Tag.id] + [Tag.idContext]) instead of whole objects: a rule loaded
     * from the backend carries tag stubs (blank name, no color) while the taught tags come from the
     * tag list fully populated, so object equality would report a difference on every single save and
     * fire a redundant PUT for a rule nothing actually changed on.
     */
    private fun isNoOp(candidate: ClassificationRule, target: ClassificationRule): Boolean =
        candidate.copy(tags = target.tags) == target && tagIdentity(candidate.tags) == tagIdentity(target.tags)

    private fun tagIdentity(tags: List<Tag>): List<Pair<String, String?>> = tags.map { it.id to it.idContext }

    /**
     * Method and card move as a pair: a null taught method leaves both untouched (never pairing a new
     * method with a stale card), a concrete one overwrites both and keeps the card only for CREDIT.
     */
    private fun resolveCardId(method: PaymentMethod?, cardId: String?, current: String?): String? {
        if (method == null) return current
        return cardId.takeIf { method == PaymentMethod.CREDIT }
    }
}
