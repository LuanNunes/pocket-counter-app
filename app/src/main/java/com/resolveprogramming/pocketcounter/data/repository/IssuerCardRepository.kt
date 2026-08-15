package com.resolveprogramming.pocketcounter.data.repository

/**
 * Local-only map from a push issuer (the notification's app label or leading token, normalized) to
 * the credit card whose invoice it bills. Taught by Home's invoice-payment picker, read back by
 * `IssuerCardMatcher`.
 *
 * One key per issuer: two Nubank cards collapse to `nubank` and the last one taught wins. A stale
 * entry can narrow a same-amount ambiguity to the wrong row (see `matchInvoicePayment`); re-teaching
 * from the picker corrects it going forward.
 */
interface IssuerCardRepository {
    /** Returns the full issuerKey → cardId map, keyed as `IssuerCardMatcher.normalizeIssuer` keys it. */
    suspend fun getMap(): Map<String, String>

    /** Associates [issuerKey] (raw or already normalized) with [cardId], replacing any prior value. */
    suspend fun associate(issuerKey: String, cardId: String)

    /** Removes the entry for [issuerKey] if one exists. */
    suspend fun clear(issuerKey: String)
}
