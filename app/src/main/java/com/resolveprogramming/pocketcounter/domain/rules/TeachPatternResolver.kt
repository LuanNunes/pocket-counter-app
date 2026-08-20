package com.resolveprogramming.pocketcounter.domain.rules

import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.domain.notification.PaymentMethodResolver

/**
 * Resolves the CONTAINS pattern a learned classification rule should carry. Candidate order: the
 * merchant the user edited beats the parsed one; [TeachPatternSanitizer] decides what survives.
 *
 * The payment hint never names a merchant, so it is offered on the IGNORE path only: a SUGGEST
 * rule keyed on it would claim and mis-tag every notification of that card.
 */
object TeachPatternResolver {

    /** Normalized card-hint words that must never be learned/keyed as a payment-method or pattern token. */
    val CARD_HINT_WORDS = setOf("cartão", "cartao", "conta", "final")

    fun resolve(
        draft: WizardDraft,
        notification: NotificationItem,
        forIgnoreRule: Boolean,
    ): String? =
        TeachPatternSanitizer.choose(
            candidates = listOf(
                draft.merchant,
                notification.parsed.merchantRaw,
                identifyingPaymentHint(notification).takeIf { forIgnoreRule },
            ),
            notificationText = notification.text,
            allowGatewayMarker = forIgnoreRule,
        )

    /**
     * The parsed payment hint, minus the bare card words of [CARD_HINT_WORDS]. "conta" is why: the
     * parser emits it for the INCOME phrase "crédito em conta", so an IGNORE rule keyed on it would
     * swallow incoming-money notifications.
     */
    private fun identifyingPaymentHint(notification: NotificationItem): String? =
        notification.parsed.paymentHint
            ?.takeIf { PaymentMethodResolver.normalizeKey(it) !in CARD_HINT_WORDS }
}
