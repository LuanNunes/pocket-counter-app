package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.CreditCard

/**
 * Pure Kotlin utility for resolving a push notification's issuer (the app label, e.g. "Nubank")
 * to a [CreditCard] the user already has on file. Modeled on [CardLast4Matcher]: any ambiguity
 * — two or more candidate cards — resolves to null rather than guessing.
 *
 * No Android, Retrofit, or Room imports — lives in the domain layer.
 */
object IssuerCardMatcher {

    fun resolve(
        app: String,
        text: String,
        cards: Collection<CreditCard>,
        learned: Map<String, String>,
    ): String? {
        val token = leadingToken(text)
        learned[normalizeIssuer(app)]?.let { return it }
        learned[normalizeIssuer(token)]?.let { return it }
        matchByName(app, cards)?.let { return it }
        return matchByName(token, cards)
    }

    /**
     * The normalized key a manual pick from the invoice-payment picker should teach: the app label
     * or the leading token, whichever names at least one card, in [resolve]'s own priority. Null
     * when neither does — a shared delivery app (e.g. an SMS aggregator) would otherwise poison
     * every other issuer that arrives through it.
     */
    fun resolutionKey(app: String, text: String, cards: Collection<CreditCard>): String? {
        if (namesAnyCard(app, cards)) return normalizeIssuer(app)
        val token = leadingToken(text)
        if (namesAnyCard(token, cards)) return normalizeIssuer(token)
        return null
    }

    /**
     * True when a taught mapping — not a name match, not the amount alone — resolves [app]/[text].
     * Checks the same two keys [resolve] does, in the same order, so this can never disagree with
     * what actually produced the match.
     */
    fun isLearned(app: String, text: String, learned: Map<String, String>): Boolean =
        learned.containsKey(normalizeIssuer(app)) || learned.containsKey(normalizeIssuer(leadingToken(text)))

    private fun leadingToken(text: String): String = text.trim().substringBefore(' ')

    private fun matchByName(issuerLabel: String, cards: Collection<CreditCard>): String? {
        val matches = cards.filter { namesOverlap(issuerLabel, it.name) }
        return matches.singleOrNull()?.id
    }

    private fun namesAnyCard(issuerLabel: String, cards: Collection<CreditCard>): Boolean =
        cards.any { namesOverlap(issuerLabel, it.name) }

    /**
     * True when the shorter name equals the longer one, or equals one of the longer one's
     * whitespace-separated tokens ("Nubank" inside "Nubank Ultravioleta"). Deliberately whole-token,
     * not a substring check on a space-deleted string — that collapsed a card nicknamed "Ame" into a
     * false match against "Pagamento" ("ame" ⊂ "pagamento"). The shorter side must normalize to at
     * least [MIN_CONTAINMENT_LENGTH] chars, or nearly any short nickname would land on some stray
     * token in the issuer's boilerplate.
     */
    private fun namesOverlap(rawA: String, rawB: String): Boolean {
        val a = normalizeIssuer(rawA)
        val b = normalizeIssuer(rawB)
        if (a == b) return true
        if (minOf(a.length, b.length) < MIN_CONTAINMENT_LENGTH) return false
        val (shorter, longerRaw) = shorterThenLongerRaw(a, b, rawA, rawB)
        return tokenize(longerRaw).any { normalizeIssuer(it) == shorter }
    }

    private fun shorterThenLongerRaw(a: String, b: String, rawA: String, rawB: String): Pair<String, String> {
        (a to rawB).takeIf { a.length <= b.length }?.let { return it }
        return b to rawA
    }

    private fun tokenize(raw: String): List<String> = raw.trim().split(WHITESPACE_REGEX).filter { it.isNotBlank() }

    fun normalizeIssuer(raw: String): String = SourceKey.normalize(raw)

    private val WHITESPACE_REGEX = Regex("\\s+")

    // Below this, containment is dropped for exact equality — otherwise a 1-2 char normalized
    // name (e.g. a card nicknamed "Nu") would spuriously overlap unrelated issuers.
    private const val MIN_CONTAINMENT_LENGTH = 3
}
