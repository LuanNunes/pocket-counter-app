package com.resolveprogramming.pocketcounter.ui.home.components

import com.resolveprogramming.pocketcounter.domain.model.ConfirmReadyItem
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.ui.components.LedgerMeta
import com.resolveprogramming.pocketcounter.ui.components.ledgerMeta
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Everything [ConfirmReadyCard] draws, resolved up front so the composable renders and decides
 * nothing. Free of `androidx` imports, which is what lets the fallbacks below be unit-tested.
 */
data class ConfirmReadyPresentation(
    val title: String,
    val signedAmount: BigDecimal?,
    val meta: LedgerMeta,
    val installmentsLabel: String?,
    val confirmLabel: String,
    val contentDescription: String,
)

fun confirmReadyPresentation(
    item: ConfirmReadyItem,
    cards: Map<String, CreditCard>,
    tags: Map<String, Tag>,
    contextsById: Map<String, TagContext>,
    today: LocalDate = LocalDate.now(),
): ConfirmReadyPresentation {
    val draft = item.draft
    val date = draft.date ?: today
    val meta = ledgerMeta(
        date = date,
        tagIds = draft.tagIds,
        paymentMethod = draft.paymentMethod,
        cardId = draft.cardId,
        cards = cards,
        tags = tags,
        contextsById = contextsById,
        today = today,
    )
    val title = resolveTitle(draft, item.notification.parsed.merchantRaw, tags)
    val installmentsLabel = draft.installments?.takeIf { it > 1 }?.let { "$it×" }
    return ConfirmReadyPresentation(
        title = title,
        signedAmount = signedAmount(draft),
        meta = meta,
        installmentsLabel = installmentsLabel,
        // A matched pending charge is being settled, not created; saying so keeps the one-tap
        // write honest about which of the two things it does.
        confirmLabel = "Confirmar pagamento".takeIf { item.pendingTransactionId != null } ?: "Confirmar",
        contentDescription = contentDescription(draft, title, date, meta, today),
    )
}

/**
 * [WizardDraft.amount] is normally unsigned while AmountText derives its +/− from the value's sign,
 * so an expense has to be negated here or the card renders "+ R$ 129,90" in expense red.
 *
 * Normalised through [BigDecimal.abs] rather than trusting the sign that arrives, because the sign
 * is NOT guaranteed on the path this card serves: `isConfirmReady` short-circuits to true for a
 * pending-transaction match without ever calling `isStep2Valid`, and the amount reaches the draft
 * straight off the wire. A negative one would otherwise be negated back into a positive and render
 * "+ R$ 50,00" in expense red — the same bug this function exists to fix, mirrored.
 */
private fun signedAmount(draft: WizardDraft): BigDecimal? {
    val magnitude = draft.amount?.abs() ?: return null
    if (draft.type != TransactionType.EXPENSE) return magnitude
    return magnitude.negate()
}

/**
 * A title has to read as a name. A stored merchant with no letters ("29", from an old pre-fix
 * parse) is junk — fall back to the recognized tag, then a neutral label, so the card that
 * authorizes a ledger write never shows nonsense.
 */
private fun resolveTitle(draft: WizardDraft, merchantRaw: String?, tags: Map<String, Tag>): String =
    listOfNotNull(draft.name, merchantRaw)
        .firstOrNull { text -> text.isNotBlank() && text.any(Char::isLetter) }
        ?: draft.tagIds.firstNotNullOfOrNull { tags[it]?.name }
        ?: "Lançamento"

private val spokenLocale = Locale("pt", "BR")

/**
 * The spoken form of the card, kept separate from the visual one: TalkBack needs "14 de junho"
 * rather than "14 jun", and its amount is built from [NumberFormat] directly so the U+2212 minus
 * AmountText prints — which screen readers voice inconsistently — never reaches it.
 */
private fun contentDescription(
    draft: WizardDraft,
    title: String,
    date: LocalDate,
    meta: LedgerMeta,
    today: LocalDate,
): String {
    val typeWord = when (draft.type) {
        TransactionType.EXPENSE -> "despesa"
        TransactionType.INCOME -> "receita"
        null -> null
    }
    val money = draft.amount?.let { NumberFormat.getCurrencyInstance(spokenLocale).format(it.abs()) }
    val amountPhrase = run {
        if (typeWord != null && money != null) return@run "$typeWord de $money"
        typeWord ?: money
    }
    return listOfNotNull(
        amountPhrase,
        title,
        spokenDate(date, today),
        meta.tagName ?: "sem categoria",
        meta.payment.takeIf { it.isNotBlank() },
        draft.installments?.takeIf { it > 1 }?.let { "em $it vezes" },
    ).joinToString(", ")
}

private fun spokenDate(date: LocalDate, today: LocalDate): String {
    if (date == today) return "hoje"
    if (date == today.minusDays(1)) return "ontem"
    val month = date.month.getDisplayName(TextStyle.FULL, spokenLocale).lowercase(spokenLocale)
    return "${date.dayOfMonth} de $month"
}
