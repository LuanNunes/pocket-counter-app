package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethodPreferences
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft

/** Everything [resolveDraftFromNotification] needs to read a notification's own evidence. */
data class NotificationEvidence(
    val last4Map: Map<String, String> = emptyMap(),
    val cards: List<CreditCard> = emptyList(),
    val learnedIssuers: Map<String, String> = emptyMap(),
    val paymentMethodDictionary: Map<String, PaymentMethod> = emptyMap(),
    val enabledMethods: Set<PaymentMethod> = PaymentMethodPreferences.default,
)

data class ResolvedDraft(val draft: WizardDraft, val unknownLast4: String?)

/**
 * The one way to turn a classified notification into a draft: parse, then resolve the payment
 * method, then name the card. Every step below is private so no call site can enter halfway and
 * save a charge with a method or card the notification itself contradicts.
 */
fun resolveDraftFromNotification(
    notification: NotificationItem,
    evidence: NotificationEvidence,
): ResolvedDraft {
    val draft = WizardDraft.fromNotification(notification)
        .withResolvedPaymentMethod(notification, evidence)
    return resolveDraftCard(draft, notification, evidence)
}

/**
 * Rebuilds the payment method from device-local evidence, in order: the learned dictionary and the
 * built-in word list over the whole text, then the server's own `paymentHint`, then the only method
 * the user left enabled. Routed through [WizardDraft.withPaymentMethod] so the credit guard holds.
 *
 * The chain exists because rules no longer carry a method. A merchant's method is a property of the
 * merchant, not of the message, and messages from Uber, PIX or débito carry no "final NNNN" hint to
 * derive it from — so without this the user re-fixed the method on every capture.
 */
private fun WizardDraft.withResolvedPaymentMethod(
    notification: NotificationItem,
    evidence: NotificationEvidence,
): WizardDraft {
    if (paymentMethod != null) return this
    val method = PaymentMethodResolver.resolve(notification.text, evidence.paymentMethodDictionary)
        ?: notification.parsed.paymentHint?.let(BrNotificationParser::parsePaymentMethod)
        ?: soleEnabledMethod(evidence.enabledMethods)
        ?: return this
    return withPaymentMethod(method)
}

/** With one method left enabled there is nothing to choose, so prefilling it cannot be wrong. */
private fun WizardDraft.soleEnabledMethod(enabled: Set<PaymentMethod>): PaymentMethod? =
    PaymentMethodPreferences.selectable(enabled, selected = null, type = type).singleOrNull()

/**
 * Names the draft's card from the notification itself: the "final NNNN" hint first, then the
 * issuer. Classification rules carry no card, so the notification's own evidence is all there is.
 *
 * An unmatched 4-digit hint leaves the draft without a card and reports [ResolvedDraft.unknownLast4]:
 * better to ask which card than to file the charge on a card the push never named.
 */
private fun resolveDraftCard(
    draft: WizardDraft,
    notification: NotificationItem,
    evidence: NotificationEvidence,
): ResolvedDraft {
    val last4 = CardLast4Matcher.extractLast4(notification.parsed.paymentHint)
        ?: return ResolvedDraft(withIssuerCard(draft, notification, evidence), null)
    val matched = CardLast4Matcher.matchCardId(last4, evidence.last4Map)
        ?: return ResolvedDraft(draft.withoutCard(), last4)
    return ResolvedDraft(draft.withCard(matched), null)
}

/**
 * Gated on CREDIT because [WizardDraft.withCard] forces it: an issuer *name* match alone would
 * otherwise flip a debit or PIX push from the same bank onto a credit card.
 */
private fun withIssuerCard(
    draft: WizardDraft,
    notification: NotificationItem,
    evidence: NotificationEvidence,
): WizardDraft {
    if (draft.paymentMethod != PaymentMethod.CREDIT) return draft
    val cardId = IssuerCardMatcher.resolve(
        app = notification.app,
        text = notification.text,
        cards = evidence.cards,
        learned = evidence.learnedIssuers,
    ) ?: return draft
    return draft.withCard(cardId)
}
