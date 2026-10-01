package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft

/** Everything [resolveDraftCard] needs to name a card from a notification's own evidence. */
data class CardEvidence(
    val last4Map: Map<String, String> = emptyMap(),
    val cards: List<CreditCard> = emptyList(),
    val learnedIssuers: Map<String, String> = emptyMap(),
)

data class ResolvedDraftCard(val draft: WizardDraft, val unknownLast4: String?)

/**
 * Names the draft's card from the notification itself — the "final NNNN" hint first, then the
 * issuer — overriding any card a matched classification rule pinned. The rule's card survives only
 * as the fallback, when the notification carries no evidence of its own.
 *
 * An unmatched 4-digit hint drops the rule's card and reports [ResolvedDraftCard.unknownLast4]:
 * better to ask which card than to file the charge on a card the push never named.
 */
fun resolveDraftCard(
    draft: WizardDraft,
    notification: NotificationItem,
    evidence: CardEvidence,
): ResolvedDraftCard {
    val last4 = CardLast4Matcher.extractLast4(notification.parsed.paymentHint)
        ?: return ResolvedDraftCard(withIssuerCard(draft, notification, evidence), null)
    val matched = CardLast4Matcher.matchCardId(last4, evidence.last4Map)
        ?: return ResolvedDraftCard(draft.withoutCard(), last4)
    return ResolvedDraftCard(draft.withCard(matched), null)
}

/**
 * Gated on CREDIT because [WizardDraft.withCard] forces it: an issuer *name* match alone would
 * otherwise flip a debit or PIX push from the same bank onto a credit card.
 */
private fun withIssuerCard(
    draft: WizardDraft,
    notification: NotificationItem,
    evidence: CardEvidence,
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
