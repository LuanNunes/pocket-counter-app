package com.resolveprogramming.pocketcounter.domain.rules

import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.RuleAction

data class DedupePlan(
    val updates: List<ClassificationRule>,
    val deletes: List<String>,
)

object RuleDedupePlanner {

    /**
     * Plans a dedupe of [all].
     *
     * Assumes [all] preserves the backend's creation order (oldest first), matching the classify
     * contract where the oldest matching rule wins; the canonical kept per category is `group[0]`.
     * `ClassificationRuleRepository.getAll()` returns the backend response verbatim and the model
     * has no `createdAt` to sort by, so this order is a contract dependency. After a merge only one
     * rule per category remains, so an unexpected order would change *which* rule id survives, not
     * classification correctness (patterns are unioned before any delete).
     */
    fun plan(all: List<ClassificationRule>): DedupePlan {
        // Group by the rule's single tag-context. A rule with 0 or >1 distinct contexts has a
        // null key; dropping those leaves only genuine same-category groups, and keeping only
        // groups of 2+ leaves only the ones that actually have duplicates to consolidate.
        val duplicateGroups = all
            .filter { it.action == RuleAction.SUGGEST }
            .groupBy { rule -> rule.tags.mapNotNull { it.idContext }.distinct().singleOrNull() }
            .filterKeys { it != null }
            .filterValues { it.size >= 2 }

        val updates = mutableListOf<ClassificationRule>()
        val deletes = mutableListOf<String>()

        for (group in duplicateGroups.values) {
            // Canonical = the first rule in `all` order (oldest wins at classify time).
            val canonical = group.first()
            val nonCanonicals = group.drop(1)

            // Union the patterns (de-duplicated case-insensitively, canonical's first).
            val merged = mergePatterns(canonical.patterns, nonCanonicals.flatMap { it.patterns })
            if (merged != canonical.patterns) {
                updates += canonical.copy(patterns = merged)
            }
            deletes += nonCanonicals.mapNotNull { it.id }
        }

        return DedupePlan(updates = updates, deletes = deletes)
    }

    /**
     * Unions [canonical] patterns with [additional] patterns, de-duplicating case-insensitively.
     * Canonical patterns come first; order is preserved within each source list.
     */
    private fun mergePatterns(canonical: List<String>, additional: List<String>): List<String> {
        val seen = canonical.map { it.lowercase() }.toMutableSet()
        val result = canonical.toMutableList()
        for (p in additional) {
            if (seen.add(p.lowercase())) {
                result += p
            }
        }
        return result
    }
}
