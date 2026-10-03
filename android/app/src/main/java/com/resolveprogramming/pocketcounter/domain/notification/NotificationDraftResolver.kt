package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft

/** Everything [resolveDraftFromNotification] needs to read a notification's own evidence. */
data class NotificationEvidence(
    val last4Map: Map<String, String> = emptyMap(),
    val cards: List<CreditCard> = emptyList(),
    val learnedIssuers: Map<String, String> = emptyMap(),
    val paymentMethodDictionary: Map<String, PaymentMethod> = emptyMap(),
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
        .withResolvedPaymentMethod(notification, evidence.paymentMethodDictionary)
    return resolveDraftCard(draft, notification, evidence)
}

/**
 * Resolves the payment method from the learned dictionary and, as a fallback, the built-in
 * word list ([BrNotificationParser.parsePaymentMethod]). No-op when [paymentMethod] is already
 * set. Routed through [WizardDraft.withPaymentMethod] so the credit guard still holds.
 */
private fun WizardDraft.withResolvedPaymentMethod(
    notification: NotificationItem,
    learnedMap: Map<String, PaymentMethod>,
): WizardDraft {
    if (paymentMethod != null) return this
    val method = PaymentMethodResolver.resolve(notification.text, learnedMap) ?: return this
    return withPaymentMethod(method)
}

/**
 * Names the draft's card from the notification itself — the "final NNNN" hint first, then the
 * issuer — overriding any card a matched classification rule pinned. The rule's card survives only
 * as the fallback, when the notification carries no evidence of its own.
 *
 * An unmatched 4-digit hint drops the rule's card and reports [ResolvedDraft.unknownLast4]:
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
