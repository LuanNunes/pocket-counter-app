package com.resolveprogramming.pocketcounter.ui.components

import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.ui.wizard.label
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The secondary line shared by every ledger-shaped row (Transações rows, the Home confirm card):
 * a date, a leading tag pill, a "+N" badge for the tags that didn't fit, and a payment label.
 *
 * Deliberately free of `androidx` imports so the resolution rules stay unit-testable on the JVM.
 */
data class LedgerMeta(
    val date: String,
    val tagName: String?,
    val tagColor: Long?,
    val extraTags: Int,
    val payment: String,
)

private val ledgerDateLocale = Locale("pt", "BR")

/**
 * Short row date: "Hoje" / "Ontem" / "04 jun".
 *
 * [today] is a parameter rather than an internal `LocalDate.now()` so the relative branches can be
 * exercised without freezing the system clock.
 */
fun formatLedgerDate(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    if (date == today) return "Hoje"
    if (date == today.minusDays(1)) return "Ontem"
    val month = date.month
        .getDisplayName(TextStyle.SHORT, ledgerDateLocale)
        .trimEnd('.')
        .lowercase(ledgerDateLocale)
    return "%02d %s".format(date.dayOfMonth, month)
}

fun ledgerMeta(
    date: LocalDate,
    tagIds: List<String>,
    paymentMethod: PaymentMethod?,
    cardId: String?,
    cards: Map<String, CreditCard>,
    tags: Map<String, Tag>,
    contextsById: Map<String, TagContext>,
    today: LocalDate = LocalDate.now(),
): LedgerMeta {
    val first = tagIds.firstOrNull()?.let { tags[it] }
    // Tags rarely carry their own color; fall back to their context (category) color so the
    // pill dot is colored like the design instead of grey.
    val tagColor = first?.color ?: first?.idContext?.let { contextsById[it]?.color }
    return LedgerMeta(
        date = formatLedgerDate(date, today),
        tagName = first?.name,
        tagColor = tagColor,
        extraTags = (tagIds.size - 1).coerceAtLeast(0),
        payment = paymentLabel(paymentMethod, cardId, cards),
    )
}

/** "Crédito Nubank", "Crédito" when the card is unknown, "Pix" otherwise. Only CREDIT names a card. */
private fun paymentLabel(
    method: PaymentMethod?,
    cardId: String?,
    cards: Map<String, CreditCard>,
): String {
    val label = method?.label() ?: return ""
    if (method != PaymentMethod.CREDIT) return label
    val cardName = cardId?.let { cards[it]?.name } ?: return label
    return "$label $cardName"
}
