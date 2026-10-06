package com.resolveprogramming.pocketcounter.ui.home.components

import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.ui.components.LedgerLookups
import com.resolveprogramming.pocketcounter.ui.components.LedgerRow
import com.resolveprogramming.pocketcounter.ui.components.ledgerMeta
import com.resolveprogramming.pocketcounter.ui.home.InvoicePaymentPrompt
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** The reason line, split so the card can bold the two names without re-parsing a rendered string. */
sealed interface InvoiceReasonLine {
    val spoken: String

    data class Plain(val text: String) : InvoiceReasonLine {
        override val spoken: String get() = text
    }

    /** The amount matched one card and the push named another; stating both makes silence impossible. */
    data class Conflict(val amountCard: String, val issuer: String) : InvoiceReasonLine {
        override val spoken: String get() = "O valor bate com a $amountCard, mas a notificação é do $issuer."
    }
}

/**
 * Everything `InvoicePaymentChoiceCard` draws. Free of `androidx` imports so it is JVM-testable.
 *
 * [amount] is separate from [metaLine] because it is the card's identifying fact — the user's task is
 * to recognise which payment this was — and [metaLine] renders in the smallest, dimmest style.
 */
data class InvoicePromptPresentation(
    val amount: BigDecimal?,
    val metaLine: String,
    val reason: InvoiceReasonLine,
    val canChoose: Boolean,
    val contentDescription: String,
)

fun invoicePromptPresentation(prompt: InvoicePaymentPrompt): InvoicePromptPresentation {
    val amount = prompt.notification.parsed.amount
    val meta = listOfNotNull(
        prompt.notification.app.takeIf { it.isNotBlank() },
        prompt.notification.time.takeIf { it.isNotBlank() },
    )
    val reason = reasonLine(prompt)
    val spokenFacts = listOfNotNull(amount?.let(::money)) + meta
    return InvoicePromptPresentation(
        amount = amount,
        metaLine = meta.joinToString(" · "),
        reason = reason,
        canChoose = prompt.candidates.isNotEmpty(),
        contentDescription = "Pagamento de fatura, ${spokenFacts.joinToString(", ")}. ${reason.spoken}",
    )
}

private fun reasonLine(prompt: InvoicePaymentPrompt): InvoiceReasonLine {
    val exact = exactMatches(prompt)
    if (exact.size > 1) return InvoiceReasonLine.Plain("Mais de uma fatura pendente tem esse valor.")
    exact.singleOrNull()?.let {
        return InvoiceReasonLine.Conflict(amountCard = it.displayTitle(), issuer = prompt.notification.app)
    }
    if (prompt.candidates.isNotEmpty()) return InvoiceReasonLine.Plain("Nenhuma fatura pendente com esse valor.")
    return InvoiceReasonLine.Plain("Nenhuma fatura pendente para marcar como paga.")
}

/** Mirrors `matchInvoicePayment` via [HistoryItem.matchesInvoiceAmount] — see its doc for why. */
private fun exactMatches(prompt: InvoicePaymentPrompt): List<HistoryItem> {
    val notified = prompt.notification.parsed.amount
    return prompt.candidates.filter { it.matchesInvoiceAmount(notified) }
}

data class InvoicePickerRow(
    val id: String,
    val title: String,
    val amount: BigDecimal,
    val meta: String,
    val statusLabel: String,
    val contentDescription: String,
)

data class InvoicePickerGroup(val header: String?, val rows: List<InvoicePickerRow>)

data class InvoicePickerPresentation(
    val title: String,
    val subtitle: String,
    /**
     * The amount the user is matching each row against, kept apart from [subtitle] so the sheet can
     * emphasise it without re-parsing a rendered string. [subtitle] stays the spoken form.
     */
    val referenceAmount: BigDecimal?,
    val issuer: String?,
    val groups: List<InvoicePickerGroup>,
)

/**
 * Two visible groups rather than a relevance sort: this sheet exists because a heuristic was not
 * trustworthy enough to act alone, so the grouping says out loud what the heuristic would hide.
 * Headers appear only when both groups have rows — one unlabelled list needs no header.
 */
fun invoicePickerPresentation(
    prompt: InvoicePaymentPrompt,
    lookups: LedgerLookups,
    today: LocalDate = LocalDate.now(),
): InvoicePickerPresentation {
    val exact = exactMatches(prompt).sortedByDescending { it.date }
    val others = (prompt.candidates - exact.toSet()).sortedByDescending { it.date }
    val labelled = exact.isNotEmpty() && others.isNotEmpty()
    val groups = listOf(
        InvoicePickerGroup("MESMO VALOR".takeIf { labelled }, exact.map { pickerRow(it, lookups, today) }),
        InvoicePickerGroup(
            "OUTRAS FATURAS PENDENTES".takeIf { labelled },
            others.map { pickerRow(it, lookups, today) },
        ),
    )
    val subtitle = listOfNotNull(
        prompt.notification.parsed.amount?.let { "Pagamento de ${money(it)}" },
        prompt.notification.app.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    return InvoicePickerPresentation(
        title = "Qual fatura foi paga?",
        subtitle = subtitle,
        referenceAmount = prompt.notification.parsed.amount,
        issuer = prompt.notification.app.takeIf { it.isNotBlank() },
        groups = groups.filter { it.rows.isNotEmpty() },
    )
}

private fun pickerRow(item: HistoryItem, lookups: LedgerLookups, today: LocalDate): InvoicePickerRow {
    val payment = ledgerMeta(
        row = LedgerRow(
            date = item.date,
            tagIds = item.tagIds.orEmpty(),
            paymentMethod = item.paymentMethod,
            cardId = item.cardId,
        ),
        lookups = lookups,
        today = today,
    ).payment
    val title = item.displayTitle()
    return InvoicePickerRow(
        id = item.id,
        title = title,
        amount = item.amount.abs(),
        meta = listOfNotNull(payment.takeIf { it.isNotBlank() }, "vence ${dueLabel(item.date)}").joinToString(" · "),
        statusLabel = "PENDENTE",
        contentDescription = listOfNotNull(
            title,
            money(item.amount),
            "pendente",
            "vence ${spokenDueLabel(item.date)}",
            payment.takeIf { it.isNotBlank() }?.lowercase(ptBr),
        ).joinToString(", "),
    )
}

/** §7.2: state the consequence, don't merely note the discrepancy — `markPaid` settles the whole row. */
data class InvoiceMismatchNote(val prefix: String, val emphasis: String?, val suffix: String) {
    val spoken: String get() = prefix + emphasis.orEmpty() + suffix
}

fun invoiceMismatchNote(notifiedAmount: BigDecimal?, invoice: HistoryItem): InvoiceMismatchNote? {
    if (notifiedAmount == null) return null
    val notified = notifiedAmount.abs()
    val due = invoice.amount.abs()
    if (notified.compareTo(due) == 0) return null
    val lead = "A notificação diz ${money(notified)} e a ${invoice.displayTitle()} é de ${money(due)}."
    if (notified < due) {
        return InvoiceMismatchNote(
            prefix = "$lead Se você pagou só uma parte, confirmar marca ",
            emphasis = "a fatura inteira como paga",
            suffix = ".",
        )
    }
    return InvoiceMismatchNote(prefix = "$lead Confirme só se for esta a fatura paga.", emphasis = null, suffix = "")
}

private val ptBr = Locale("pt", "BR")

private fun money(amount: BigDecimal): String =
    NumberFormat.getCurrencyInstance(ptBr).format(amount.abs())

/** Never relative: "vence Hoje" reads as a status, and the due date is here to identify a billing cycle. */
private fun dueLabel(date: LocalDate): String {
    val month = date.month.getDisplayName(TextStyle.SHORT, ptBr).trimEnd('.').lowercase(ptBr)
    return "%02d %s".format(date.dayOfMonth, month)
}

private fun spokenDueLabel(date: LocalDate): String {
    val month = date.month.getDisplayName(TextStyle.FULL, ptBr).lowercase(ptBr)
    return "${date.dayOfMonth} de $month"
}
