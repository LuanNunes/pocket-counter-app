package com.resolveprogramming.pocketcounter.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class WizardDraft(
    val type: TransactionType? = null,
    val amount: BigDecimal? = null,
    val date: LocalDate? = null,
    val statusPayment: PaymentStatus = PaymentStatus.PAID,
    val idPaymentSource: String? = null,
    val idSource: String? = null,
    val tagIds: List<String> = emptyList(),
    val installments: Int? = null,
    val installmentValue: BigDecimal? = null,
    val merchant: String? = null,
    val learnRule: Boolean = false,
    /** Carried through edits so a manually-reordered row keeps its position; 0 for new rows. */
    val displayOrder: Int = 0,
) {
    fun isStep1Valid(): Boolean = type != null

    fun isStep2Valid(): Boolean = amount != null && amount > BigDecimal.ZERO

    fun isStep3Valid(): Boolean = idPaymentSource != null

    fun isStep4Valid(): Boolean = idSource != null

    fun withPaymentSourceReset(newPaymentSourceId: String): WizardDraft =
        if (idPaymentSource == newPaymentSourceId) this
        else copy(idPaymentSource = newPaymentSourceId, idSource = null)

    fun withTagToggled(tagId: String): WizardDraft =
        if (tagId in tagIds) copy(tagIds = tagIds - tagId)
        else copy(tagIds = tagIds + tagId)

    companion object {
        fun fromNotification(
            notification: NotificationItem,
        ): WizardDraft = WizardDraft(
            type = notification.parsed.type,
            amount = notification.parsed.amount,
            date = notification.parsed.date ?: LocalDate.now(),
            idPaymentSource = notification.suggestions.idPaymentSource,
            idSource = notification.suggestions.idSource,
            tagIds = notification.suggestions.tagIds,
            merchant = notification.parsed.merchantRaw,
            installments = notification.parsed.installments,
            installmentValue = notification.parsed.installmentValue,
        )
    }
}
