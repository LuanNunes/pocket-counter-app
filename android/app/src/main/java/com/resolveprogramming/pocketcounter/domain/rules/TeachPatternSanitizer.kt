package com.resolveprogramming.pocketcounter.domain.rules

/**
 * Turns the merchant-ish candidates a teach can offer into the one CONTAINS pattern worth storing on
 * a rule, or nothing at all. A pattern that is too broad quietly absorbs unrelated merchants, since
 * the backend applies a single rule per notification.
 */
object TeachPatternSanitizer {

    private val TRAILING_PUNCTUATION = charArrayOf('.', ',', ';', ':', '-')

    /**
     * The first candidate that survives [clean] and occurs in [notificationText]. A rejected candidate
     * falls through to the next rather than aborting the teach; ordering is the caller's policy.
     *
     * [allowGatewayMarker] is for the IGNORE path only: an IGNORE rule carries no tags and is never a
     * teach target, so silencing a whole acquirer can't mis-tag anything.
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
     * Gateway prefixes are rejected, never shortened: stripping the '*' off "Ifd*" yields "Ifd", which
     * is broader still.
     *
     * Only TRAILING characters are removed — no interior edits, no case folding, no whitespace
     * collapsing — so the result stays a literal substring of the notification text, which is what the
     * backend's CONTAINS needs. The closing trim matters because the server's identity is
     * `lower(pattern)`: "Padaria " and "Padaria" would be two distinct rules the user cannot tell apart
     * in Regras.
     */
    internal fun clean(raw: String, allowGatewayMarker: Boolean = false): String? {
        val trimmed = raw.trim().trimEnd(*TRAILING_PUNCTUATION).trim()
        if (trimmed.isBlank()) return null
        if (!allowGatewayMarker && isGatewayMarker(trimmed)) return null
        return trimmed
    }

    /**
     * True when [raw] holds a '*' with no letter after its LAST occurrence — bare payment-gateway
     * markers ("Ifd*", "Dl *", "Rp3bank*"), which name the acquirer that routed the charge rather
     * than the merchant that took the money.
     *
     * Position-independent on purpose, not anchored to a prefix: anchoring would miss "Rp3bank*"
     * the way `BrNotificationParser.ACQUIRER_PREFIX_REGEX` does. Over-rejecting a lookalike
     * ("PAG*123456") only costs a learned rule; under-rejecting re-tags every merchant behind the gateway.
     */
    private fun isGatewayMarker(raw: String): Boolean {
        val lastStar = raw.lastIndexOf('*')
        return lastStar >= 0 && raw.drop(lastStar + 1).none { it.isLetter() }
    }
}
