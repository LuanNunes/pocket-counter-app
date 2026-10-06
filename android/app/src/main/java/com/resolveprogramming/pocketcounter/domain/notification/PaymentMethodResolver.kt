package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod

/**
 * Pure, Android-free resolver that augments [BrNotificationParser] with a device-local
 * learned dictionary.
 *
 * Resolution order (first match wins):
 *  1. The LEARNED dictionary — any whitespace-delimited token whose normalized form is a key.
 *  2. [BrNotificationParser.parsePaymentMethod] — the built-in word list.
 */
object PaymentMethodResolver {

    private val STRIP_PUNCTUATION = Regex("""^[,\.;:]+|[,\.;:]+$""")

    /**
     * Normalizes a raw token text so keys written at learn-time and keys derived from incoming
     * tokens always agree:
     *  - trim whitespace
     *  - lowercase (handles accented chars via [String.lowercase])
     *  - strip leading/trailing punctuation (`,`, `.`, `;`, `:`)
     */
    fun normalizeKey(raw: String): String =
        STRIP_PUNCTUATION.replace(raw.trim().lowercase(), "")

    /**
     * Resolves the payment method for a notification [text]:
     *  1. Split on whitespace, normalize each token via [normalizeKey].
     *  2. Return the method for the FIRST token whose normalized form is in [learned].
     *  3. Fall back to [BrNotificationParser.parsePaymentMethod].
     */
    fun resolve(text: String, learned: Map<String, PaymentMethod>): PaymentMethod? {
        if (learned.isNotEmpty()) {
            for (raw in text.split(Regex("""\s+"""))) {
                val key = normalizeKey(raw)
                val method = learned[key]
                if (method != null) return method
            }
        }
        return BrNotificationParser.parsePaymentMethod(text)
    }
}
