package com.resolveprogramming.pocketcounter.domain.rules

import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
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
     * Decides how teaching [pattern] under [categoryId] should mutate the rule set.
     *
     * When several SUGGEST rules share the category, the target is the FIRST in [existing] —
     * assumed to be the oldest (backend creation order), which is the rule classify actually applies
     * (oldest wins). If the backend order were reversed, a taught pattern could land on a rule that
     * classify doesn't use until a later dedupe consolidates the group. See [RuleDedupePlanner].
     */
    fun plan(
        existing: List<ClassificationRule>,
        categoryId: String,
        tags: List<Tag>,
        type: TransactionType?,
        pattern: String,
    ): TeachPlan {
        val target = existing.firstOrNull { rule ->
            rule.action == RuleAction.SUGGEST &&
                rule.tags.any { it.idContext == categoryId }
        } ?: return TeachPlan.Create(
            ClassificationRule(
                id = null,
                patterns = listOf(pattern),
                matchType = "CONTAINS",
                active = true,
                appliedCount = 0,
                transactionType = type,
                paymentMethod = null,
                cardId = null,
                tags = tags,
                action = RuleAction.SUGGEST,
            ),
        )

        if (isCovered(target.patterns, pattern)) return TeachPlan.NoOp

        return TeachPlan.Update(target.copy(patterns = target.patterns + pattern))
    }

    /**
     * Returns true when [pattern] is already covered by [existing] patterns.
     * Covered means: an existing pattern P where P equals pattern (case-insensitive)
     * OR pattern contains P (existing is a broader substring that matches any text
     * containing the new pattern).
     */
    private fun isCovered(existingPatterns: List<String>, pattern: String): Boolean =
        existingPatterns.any { p ->
            p.equals(pattern, ignoreCase = true) ||
                pattern.contains(p, ignoreCase = true)
        }
}
