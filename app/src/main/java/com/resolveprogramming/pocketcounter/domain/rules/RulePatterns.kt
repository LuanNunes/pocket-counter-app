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
 *
 * [isGatewayMarker] is the odd one out: a lexical classification rather than a comparison. It gates
 * [sameSubject] and backs [TeachPatternSanitizer], and neither the loose nor the strict rationale
 * applies to it.
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
     * True when [raw] contains a '*' and no letter follows its LAST occurrence.
     *
     * That is the whole predicate — deliberately position-independent, not anchored to a prefix. It
     * catches the bare payment-gateway markers the corpus carries ("Ifd*", "Dl *", "Rp3bank*"), which
     * name the acquirer that routed the charge rather than the merchant that took the money, and are
     * therefore the same "subject" as every merchant behind them. Anchoring it to a prefix would miss
     * "Rp3bank*" exactly the way `BrNotificationParser.ACQUIRER_PREFIX_REGEX` does — see [sameSubject].
     *
     * It also fires on strings that merely look like that: a merchant ending in '*', or an acquirer
     * descriptor with a numeric merchant id ("PAG*123456"). Both over-rejections are inert — the
     * candidate is dropped and at worst no rule is learned, or the rule stops being a teach target and
     * a fresh one is created instead. The false negative it prevents is destructive re-tagging, so the
     * asymmetry points the right way on purpose.
     */
    fun isGatewayMarker(raw: String): Boolean {
        val lastStar = raw.lastIndexOf('*')
        return lastStar >= 0 && raw.drop(lastStar + 1).none { it.isLetter() }
    }

    /**
     * True when [a] and [b] name the same thing: either normalized form contains the other, and
     * neither is a bare gateway marker. Blank on either side is never a subject.
     *
     * Built on the loose [normalize], like [matches] and unlike [covers]: this only narrows which rule
     * a teach targets, and "DL *UberRides" / "DL     *UberRides" have to read as one subject even
     * though neither is a strict substring of the other.
     *
     * The [isGatewayMarker] guard is what separates a merchant from the acquirer in front of it, and it
     * has to live HERE rather than rely on the taught pattern arriving pre-stripped. Containment alone
     * says "Dl *" and "Dl *UberRides" are one subject — a gateway prefix is a literal prefix of every
     * merchant behind it — so a pattern that kept its prefix would re-elect the gateway's rule and
     * re-tag every merchant it ever matched. `BrNotificationParser` strips only SOME of these
     * (alphanumerics glued directly to the star, six or fewer), leaving "Dl *", "Mp *" and "Rp3bank*"
     * intact, and correctness of the rule set must not hinge on that regex's exact bounds.
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
