package com.resolveprogramming.pocketcounter.domain.rules

import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.RuleAction

data class DedupePlan(
    val updates: List<ClassificationRule>,
    val deletes: List<String>,
)

object RuleDedupePlanner {

    /**
     * Patterns shorter than this are ignored when deciding whether two rules overlap. Merging deletes
     * rules irreversibly, and a single pathological one- or two-character pattern would otherwise
     * chain every unrelated rule into one component.
     */
    private const val MIN_ADJACENCY_PATTERN_LENGTH = 3

    /**
     * Plans a dedupe of [all], grouping rules that compete for the same notifications — connected
     * components of pattern overlap — rather than rules that happen to share a tag context. Two
     * merchants in one category are not duplicates; two rules whose patterns match the same text are,
     * because classify only ever applies one of them.
     *
     * Assumes [all] preserves the backend's creation order (oldest first), matching the classify
     * contract where the oldest matching rule wins; the canonical kept per group is `group[0]`.
     * `ClassificationRuleRepository.getAll()` returns the backend response verbatim and the model has
     * no `createdAt` to sort by, so this order is a contract dependency. After a merge only one rule
     * per group remains, so an unexpected order would change *which* rule id survives, not
     * classification correctness (patterns are consolidated before any delete).
     */
    fun plan(all: List<ClassificationRule>): DedupePlan {
        val candidates = all.filter { rule ->
            rule.action == RuleAction.SUGGEST && rule.patterns.any { it.isNotBlank() }
        }

        val updates = mutableListOf<ClassificationRule>()
        val deletes = mutableListOf<String>()

        for (group in overlapComponents(candidates)) {
            val canonical = group.first()
            val merged = merge(canonical, group)
            if (merged != canonical) {
                updates += merged
            }
            deletes += group.drop(1).mapNotNull { it.id }
        }

        return DedupePlan(updates = updates, deletes = deletes)
    }

    /**
     * Connected components of the "patterns overlap" graph, each ordered ascending by position in
     * [rules] and the components themselves ordered by their smallest position. That makes `group[0]`
     * the oldest rule — the one classify uses — and the whole output deterministic.
     */
    private fun overlapComponents(rules: List<ClassificationRule>): List<List<ClassificationRule>> {
        val adjacency = rules.map { rule -> adjacencyPatterns(rule) }
        val visited = BooleanArray(rules.size)
        val components = mutableListOf<List<ClassificationRule>>()

        for (start in rules.indices) {
            if (visited[start]) continue
            val component = mutableListOf<Int>()
            // Iterative on purpose: a component can span the whole rule set.
            val pending = ArrayDeque<Int>()
            visited[start] = true
            pending += start
            while (pending.isNotEmpty()) {
                val current = pending.removeFirst()
                component += current
                for (next in rules.indices) {
                    if (visited[next]) continue
                    if (!RulePatterns.overlap(adjacency[current], adjacency[next])) continue
                    visited[next] = true
                    pending += next
                }
            }
            components += component.sorted().map { rules[it] }
        }

        return components
    }

    private fun adjacencyPatterns(rule: ClassificationRule): List<String> =
        rule.patterns.filter { RulePatterns.normalize(it).length >= MIN_ADJACENCY_PATTERN_LENGTH }

    /**
     * Folds [group] onto [canonical] with newer-wins field resolution: a value set on a newer
     * duplicate is the edit the user just made and has to survive the merge. Nothing is unioned —
     * a union would revive tags the user deleted and mint multi-context rules.
     *
     * Identity fields (id, appliedCount, active, matchType, action) stay on the canonical, so its
     * application history survives.
     */
    private fun merge(
        canonical: ClassificationRule,
        group: List<ClassificationRule>,
    ): ClassificationRule {
        val methodOwner = group.lastOrNull { it.paymentMethod != null }
        val paymentMethod = methodOwner?.paymentMethod
        return canonical.copy(
            patterns = RulePatterns.compact(group.flatMap { it.patterns }),
            transactionType = group.lastOrNull { it.transactionType != null }?.transactionType,
            paymentMethod = paymentMethod,
            cardId = methodOwner?.cardId?.takeIf { paymentMethod == PaymentMethod.CREDIT },
            tags = group.lastOrNull { it.tags.isNotEmpty() }?.tags.orEmpty(),
        )
    }
}
