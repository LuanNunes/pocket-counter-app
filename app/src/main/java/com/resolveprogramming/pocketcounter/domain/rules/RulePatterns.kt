package com.resolveprogramming.pocketcounter.domain.rules

/**
 * Pattern algebra shared by [RuleTeachPlanner], [RuleDedupePlanner] and [TeachPatternSanitizer],
 * modelling the backend's `matchType: "CONTAINS"` semantics: a rule fires when the notification text
 * contains one of its patterns.
 *
 * Two comparisons live here on purpose, and the difference is about consequences, not taste:
 *  - [normalize] is loose (whitespace runs collapsed) and backs [matches] and [sameSubject], which
 *    together only pick which rule a teach edits. Being loose there can at worst target a rule the
 *    user didn't expect.
 *  - [foldCase] is strict (whitespace preserved verbatim) and backs [covers]/[compact], which
 *    authorise DELETING a stored pattern. Being loose there drops patterns whose reach is not
 *    actually subsumed, and a dropped pattern can stop a rule from firing at all.
 */
object RulePatterns {

    private val WHITESPACE_RUN = Regex("\\s+")

    /**
     * Loose comparison: trimmed, lowercased, whitespace runs collapsed to one space.
     *
     * Collapsing whitespace assumes the backend's CONTAINS is whitespace-insensitive, which we can't
     * verify from here. Only [matches] and [sameSubject] may rely on that assumption, because a wrong
     * answer there changes which rule is taught, never what is stored.
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
     * True when [raw] holds a '*' with no letter after its LAST occurrence — bare payment-gateway
     * markers ("Ifd*", "Dl *", "Rp3bank*"), which name the acquirer that routed the charge rather
     * than the merchant that took the money.
     *
     * Position-independent on purpose, not anchored to a prefix: anchoring would miss "Rp3bank*" the
     * way `BrNotificationParser.ACQUIRER_PREFIX_REGEX` does. Over-rejecting a lookalike ("PAG*123456")
     * only costs a learned rule; under-rejecting re-tags every merchant behind the gateway.
     */
    fun isGatewayMarker(raw: String): Boolean {
        val lastStar = raw.lastIndexOf('*')
        return lastStar >= 0 && raw.drop(lastStar + 1).none { it.isLetter() }
    }

    /**
     * True when [a] and [b] name the same thing: either normalized form contains the other, and
     * neither is a bare gateway marker. Built on the loose [normalize], like [matches].
     *
     * The [isGatewayMarker] guard has to live here rather than assume the taught pattern arrived
     * pre-stripped: `BrNotificationParser` leaves "Dl *", "Mp *" and "Rp3bank*" intact, and
     * containment alone makes a gateway prefix the same subject as every merchant behind it.
     */
    fun sameSubject(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        if (isGatewayMarker(a) || isGatewayMarker(b)) return false
        val left = normalize(a)
        val right = normalize(b)
        return left.contains(right) || right.contains(left)
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
