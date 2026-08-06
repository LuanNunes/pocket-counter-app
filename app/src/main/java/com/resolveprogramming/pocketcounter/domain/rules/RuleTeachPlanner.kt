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
     * The target is the FIRST active SUGGEST rule in [existing] carrying a SINGLE pattern that both
     * [RulePatterns.matches] [notificationText] and is the same subject ([RulePatterns.sameSubject])
     * as [pattern]. Both conditions on the same pattern: the live corpus stores payment-gateway
     * prefixes ("Ifd*", "Dl *", "Mp *") as patterns, which match every notification behind that
     * gateway, so a text match alone would let any merchant billed through iFood absorb itself into
     * the iFood rule and re-tag everything that rule ever applied to. Rules that merely share the
     * taught tags' context are not targets either: same category, different merchant. IGNORE and
     * deactivated rules are never targets — classify skips them, so teaching one would silently do
     * nothing. No target → a fresh rule.
     *
     * [existing] is assumed to be in backend creation order (oldest first), which orders the ELIGIBLE
     * targets only; it no longer identifies the rule classify applied. When the rule classify applied
     * matched via an unrelated pattern, teaching now creates a fresh rule that classify will NOT apply
     * — the older gateway-prefix rule still wins the one-rule-per-notification race until it is
     * narrowed or deleted in Regras. Those "born dead" rules are backend-owned (precedence lives
     * there); the app must not strip patterns off another user's rule to make room, and there is
     * deliberately no `TeachPlan.Split`.
     *
     * Known consequence of overwriting tags instead of unioning them: an Update can still re-tag more
     * than the taught merchant. With the subject constraint it can only keep the broadest pattern of
     * ONE subject — teaching "UBER EATS" onto a rule matching "Uber" targets it, [RulePatterns.compact]
     * drops the narrower new pattern as already covered, and the taught tags replace the old ones, so
     * Uber rides start being tagged as Uber Eats — or add a whitespace variant of a pattern already
     * there. Genuinely new merchants now arrive via [TeachPlan.Create] instead. The escape hatch is
     * still deleting the rule in Regras and teaching the two merchants separately; a union would
     * instead make a wrong tag impossible to remove through the wizard, which is how the rule set
     * drifted in the first place.
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
                rule.patterns.any {
                    RulePatterns.matches(it, notificationText) && RulePatterns.sameSubject(it, pattern)
                }
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
