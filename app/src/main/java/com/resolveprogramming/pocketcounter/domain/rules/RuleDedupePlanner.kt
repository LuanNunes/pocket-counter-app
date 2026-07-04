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
        val suggestRules = all.filter { it.action == RuleAction.SUGGEST }

        // Group by single distinct idContext across the rule's tags.
        // A rule with 0 or >1 distinct contexts is left alone (safety).
        val groups = suggestRules
            .groupBy { rule ->
                val contexts = rule.tags.mapNotNull { it.idContext }.distinct()
                if (contexts.size == 1) contexts[0] else null
            }

        val updates = mutableListOf<ClassificationRule>()
        val deletes = mutableListOf<String>()

        for ((context, group) in groups) {
            // null key = rules that are ungroupable (zero-tag or multi-category) — skip entirely
            if (context == null) continue
            if (group.size < 2) continue

            // Canonical = the first rule in `all` order (oldest wins at classify time)
            val canonical = group[0]
            val nonCanonicals = group.drop(1)

            // Merge all patterns — union, de-duplicated case-insensitively, canonical's first
            val merged = mergePatterns(canonical.patterns, nonCanonicals.flatMap { it.patterns })

            if (merged != canonical.patterns) {
                updates += canonical.copy(patterns = merged)
            }

            nonCanonicals.forEach { rule ->
                rule.id?.let { deletes += it }
            }
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
