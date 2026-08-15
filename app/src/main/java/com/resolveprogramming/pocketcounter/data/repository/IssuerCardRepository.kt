package com.resolveprogramming.pocketcounter.data.repository

/**
 * Local-only map from a push issuer (the notification's app label, normalized) to the credit card
 * whose invoice it bills. Taught by Home's invoice-payment picker, read back by `IssuerCardMatcher`.
 *
 * One key per issuer: two Nubank cards collapse to `nubank` and the last one taught wins. That is
 * mostly acceptable because the amount is the primary match key and the issuer only narrows it, so
 * a stale entry degrades to the picker instead of mis-matching — but only when exactly one pending
 * row shares the push's amount. With two or more same-amount candidates, `resolveByAmount` narrows
 * by the stale cardId and can return a single (wrong) row as `Matched` instead of falling through to
 * `NeedsChoice`. Re-teaching from the picker corrects a stale entry going forward.
 */
interface IssuerCardRepository {
    /** Returns the full issuerKey → cardId map, keyed as `IssuerCardMatcher.normalizeIssuer` keys it. */
    suspend fun getMap(): Map<String, String>

    /** Associates [issuerKey] (raw or already normalized) with [cardId], replacing any prior value. */
    suspend fun associate(issuerKey: String, cardId: String)

    /** Removes the entry for [issuerKey] if one exists. */
    suspend fun clear(issuerKey: String)
}
