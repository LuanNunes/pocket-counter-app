package com.resolveprogramming.pocketcounter.domain.rules

/**
 * Turns the merchant-ish candidates a wizard save can offer into the one CONTAINS pattern worth
 * storing on a rule, or nothing at all.
 *
 * The bar is deliberately high because a stored pattern is permanent leverage: it decides, for every
 * future notification, which single rule the backend applies. A pattern that is too broad quietly
 * absorbs unrelated merchants, and the corpus already carries the scars — payment-gateway prefixes
 * like "Ifd*" or "Mp *" match everything billed through that gateway.
 */
object TeachPatternSanitizer {

    private val TRAILING_PUNCTUATION = charArrayOf('.', ',', ';', ':', '-')

    /**
     * The first candidate that survives [clean] and appears — case-insensitively — in
     * [notificationText]. Nulls and rejected candidates fall through to the next candidate rather than
     * aborting the teach, so an over-broad edited merchant still lets the parsed one through.
     *
     * Ordering is the caller's policy — this only filters.
     *
     * [allowGatewayMarker] opts out of the gateway rejection for the IGNORE path only, where matching a
     * whole acquirer is the user's actual intent: an IGNORE rule carries no tags and is never a teach
     * target, so its breadth silences notifications instead of mis-tagging transactions.
     */
    fun choose(
        candidates: List<String?>,
        notificationText: String,
        allowGatewayMarker: Boolean = false,
    ): String? =
        candidates.filterNotNull()
            .mapNotNull { clean(it, allowGatewayMarker) }
            .firstOrNull { notificationText.contains(it, ignoreCase = true) }

    /**
     * Trims, drops trailing punctuation, and — unless [allowGatewayMarker] — rejects bare payment-
     * gateway prefixes via [RulePatterns.isGatewayMarker].
     *
     * Rejected rather than cleaned. Stripping the '*' off "Ifd*" yields "Ifd", which is BROADER — still
     * every iFood notification, now also anything else containing those letters. There is no shorter
     * form of a gateway prefix that means the merchant.
     *
     * Only TRAILING characters are removed: no interior edits, no case folding, no whitespace
     * collapsing. Per [RulePatterns], a stored pattern has to stay a substring of real notification
     * text, since we can't verify how whitespace-sensitive the backend's CONTAINS is. The closing
     * [String.trim] matters for the same reason [RulePatterns.covers] is whitespace-strict: "Padaria "
     * and "Padaria" never compact into each other, so a stray trailing space accumulates near-duplicate
     * patterns on the rule forever.
     */
    internal fun clean(raw: String, allowGatewayMarker: Boolean = false): String? {
        val trimmed = raw.trim().trimEnd(*TRAILING_PUNCTUATION).trim()
        if (trimmed.isBlank()) return null
        if (!allowGatewayMarker && RulePatterns.isGatewayMarker(trimmed)) return null
        return trimmed
    }
}
