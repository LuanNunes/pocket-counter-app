package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import java.text.Normalizer

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
        val issuerKey = normalizeIssuer(app)
        learned[issuerKey]?.let { return it }
        matchByName(issuerKey, cards)?.let { return it }
        val leadingToken = text.trim().substringBefore(' ')
        return matchByName(normalizeIssuer(leadingToken), cards)
    }

    /**
     * The normalized key a manual pick from the invoice-payment picker should teach: whichever of
     * the app label or the text's leading token actually names a card on file, in the same priority
     * [resolve] uses for a name match. Necessary because for an SMS/aggregator delivery (app =
     * "Mensagens", text = "Nubank: …") [resolve] finds the card via the leading token, not the app
     * label — teaching the app label instead would make that one entry outrank the correct
     * leading-token match for every other issuer arriving through the same app. Falls back to the
     * app label when neither names a card, so there is still something to teach.
     */
    fun resolutionKey(app: String, text: String, cards: Collection<CreditCard>): String {
        val appKey = normalizeIssuer(app)
        if (matchByName(appKey, cards) != null) return appKey
        val tokenKey = normalizeIssuer(text.trim().substringBefore(' '))
        if (matchByName(tokenKey, cards) != null) return tokenKey
        return appKey
    }

    /** True when a taught mapping — not a name match, not the amount alone — resolves [app]. */
    fun isLearned(app: String, learned: Map<String, String>): Boolean =
        learned.containsKey(normalizeIssuer(app))

    private fun matchByName(normalizedIssuer: String, cards: Collection<CreditCard>): String? {
        val matches = cards.filter { namesOverlap(normalizedIssuer, normalizeIssuer(it.name)) }
        return matches.singleOrNull()?.id
    }

    /**
     * True when one normalized name contains the other in either direction ("Nubank" inside "Nubank
     * Ultravioleta", or a short card nickname inside a longer issuer label). The shorter side must be
     * at least [MIN_CONTAINMENT_LENGTH] chars, or nearly any two names would spuriously overlap.
     */
    private fun namesOverlap(a: String, b: String): Boolean {
        if (minOf(a.length, b.length) < MIN_CONTAINMENT_LENGTH) return a == b
        return a.contains(b) || b.contains(a)
    }

    fun normalizeIssuer(raw: String): String {
        val normalized = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
        val withoutDiacritics = DIACRITIC_REGEX.replace(normalized, "")
        return NON_ALPHANUMERIC_REGEX.replace(withoutDiacritics, "")
    }

    private val DIACRITIC_REGEX = Regex("\\p{Mn}+")
    private val NON_ALPHANUMERIC_REGEX = Regex("[^\\p{L}\\p{N}]")

    // Below this, containment is dropped for exact equality — otherwise a 1-2 char normalized
    // name (e.g. a card nicknamed "Nu") would spuriously overlap unrelated issuers.
    private const val MIN_CONTAINMENT_LENGTH = 3
}
