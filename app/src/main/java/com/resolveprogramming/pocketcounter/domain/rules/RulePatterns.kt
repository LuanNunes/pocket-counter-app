package com.resolveprogramming.pocketcounter.domain.rules

/**
 * Pattern algebra used by [RuleTeachPlanner], modelling the backend's `matchType: "CONTAINS"`
 * semantics: a rule fires when the notification text contains one of its patterns.
 *
 * Two comparisons live here on purpose, and the difference is about consequences, not taste:
 *  - [normalize] is loose (whitespace runs collapsed) and backs [matches], which only picks which
 *    rule a teach edits. Being loose there can at worst target a rule the user didn't expect.
 *  - [foldCase] is strict (whitespace preserved verbatim) and backs [covers]/[compact], which
 *    authorise DELETING a stored pattern from a rule being taught. Being loose there drops patterns
 *    whose reach is not actually subsumed, and a dropped pattern can stop a rule from firing at all.
 *
 * [isCovered] and [overlap] have no caller yet — kept for reporting that one rule subsumes another.
 */
object RulePatterns {

    private val WHITESPACE_RUN = Regex("\\s+")

    /**
     * Loose comparison: trimmed, lowercased, whitespace runs collapsed to one space.
     *
     * Collapsing whitespace assumes the backend's CONTAINS is whitespace-insensitive, which we can't
     * verify from here. Only [matches] may rely on that assumption, because a wrong answer there
     * changes which rule is taught, never what is stored.
     */
    fun normalize(raw: String): String = raw.trim().lowercase().replace(WHITESPACE_RUN, " ")

    /** Strict comparison: trimmed and lowercased, whitespace runs kept verbatim. */
    private fun foldCase(raw: String): String = raw.trim().lowercase()

    /**
     * True when [text] contains [pattern] — the client-side model of the backend's CONTAINS match.
     *
     * A blank pattern matches nothing. Literally it would match everything (`"x".contains("")`),
     * which would make a single rule carrying a blank pattern the target of every teach.
     */
    fun matches(pattern: String, text: String): Boolean {
        if (pattern.isBlank()) return false
        return normalize(text).contains(normalize(pattern))
    }

    /**
     * True when [broad] fires on every text [narrow] fires on, i.e. [narrow] contains [broad].
     * Reflexive: a pattern covers itself.
     *
     * Built on the strict [foldCase], not on [normalize]: [compact] deletes a pattern only because
     * this says another one subsumes its reach, so differing whitespace has to count as a difference.
     * "DL *UberRides" and "DL     *UberRides" are exactly as distinct to a whitespace-sensitive
     * CONTAINS as "DL*UberRides" and "DL *UberRides" are, and all of them have to survive.
     */
    fun covers(broad: String, narrow: String): Boolean =
        foldCase(narrow).contains(foldCase(broad))

    /** True when [candidate] adds no reach: some pattern in [patterns] already covers it. */
    fun isCovered(patterns: List<String>, candidate: String): Boolean =
        patterns.any { covers(it, candidate) }

    /** True when any pattern of [a] and any pattern of [b] cover each other in either direction. */
    fun overlap(a: List<String>, b: List<String>): Boolean =
        a.any { pa -> b.any { pb -> covers(pa, pb) || covers(pb, pa) } }

    /**
     * Drops every pattern already covered by another one, keeping the survivors verbatim and in
     * first-occurrence order. Blanks are dropped; a non-blank input never compacts to nothing.
     *
     * Survivors are returned as the original strings, never folded text: the backend does the
     * CONTAINS matching and we don't know whether it is whitespace-sensitive, so a stored pattern
     * must stay a literal substring of real notification text.
     */
    fun compact(patterns: List<String>): List<String> {
        val kept = patterns.filter { it.isNotBlank() }
        val folded = kept.map { foldCase(it) }
        return kept.filterIndexed { i, _ ->
            folded.indices.none { j -> j != i && dominates(folded, j, i) }
        }
    }

    /**
     * True when pattern [j] makes pattern [i] redundant: it covers it and is either strictly broader,
     * or the same pattern written differently but occurring earlier. The tie-break is what keeps two
     * equivalent patterns from eliminating each other and compacting the list down to nothing.
     *
     * Dominance is transitive and acyclic (it strictly decreases folded length, then index), so
     * "covered by anything" and "covered by a survivor" are the same test.
     */
    private fun dominates(folded: List<String>, j: Int, i: Int): Boolean {
        val broad = folded[j]
        val narrow = folded[i]
        if (!narrow.contains(broad)) return false
        if (broad != narrow) return true
        return j < i
    }
}
