package com.resolveprogramming.pocketcounter.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class WizardDraft(
    val type: TransactionType? = null,
    val amount: BigDecimal? = null,
    val date: LocalDate? = null,
    val statusPayment: PaymentStatus = PaymentStatus.PAID,
    val paymentMethod: PaymentMethod? = null,
    val cardId: String? = null,
    val isFixo: Boolean = false,
    val recurrenceDay: Int? = null,
    val seriesId: String? = null,
    val tagIds: List<String> = emptyList(),
    val installments: Int? = null,
    val installmentValue: BigDecimal? = null,
    val merchant: String? = null,
    val name: String? = null,
    val learnRule: Boolean = false,
    /** Carried through edits so a manually-reordered row keeps its position; 0 for new rows. */
    val displayOrder: Int = 0,
) {
    fun isStep1Valid(): Boolean = type != null

    fun isStep2Valid(): Boolean =
        amount != null && amount > BigDecimal.ZERO &&
            (!isFixo || (recurrenceDay != null && recurrenceDay in 1..31))

    fun isStep3Valid(): Boolean = paymentMethod != PaymentMethod.CREDIT || cardId != null

    fun isStep4Valid(): Boolean = true

    fun withPaymentMethod(method: PaymentMethod?): WizardDraft {
        if (method == PaymentMethod.CREDIT && type == TransactionType.INCOME) return this
        return copy(
            paymentMethod = method,
            cardId = cardId.takeIf { method == PaymentMethod.CREDIT },
        )
    }

    fun withTagToggled(tagId: String): WizardDraft {
        if (tagId in tagIds) return copy(tagIds = tagIds - tagId)
        return copy(tagIds = tagIds + tagId)
    }

    /** Sets the type, dropping a credit payment that an income can't hold (mirrors [withPaymentMethod]). */
    fun withType(type: TransactionType): WizardDraft {
        if (type == TransactionType.INCOME && paymentMethod == PaymentMethod.CREDIT) {
            return copy(type = type, paymentMethod = null, cardId = null)
        }
        return copy(type = type)
    }

    companion object {
        fun fromNotification(
            notification: NotificationItem,
        ): WizardDraft {
            val base = WizardDraft(
                type = notification.parsed.type,
                amount = notification.parsed.amount,
                date = notification.parsed.date ?: LocalDate.now(),
                // isFixo is a user toggle ("Repete todo mês"); the classify suggestion
                // doesn't carry it, so a fresh draft always starts non-fixo.
                tagIds = notification.suggestions.tagIds,
                merchant = notification.parsed.merchantRaw,
                installments = notification.parsed.installments,
                installmentValue = notification.parsed.installmentValue,
            )
            // Route the suggested method through the credit guard so an income+CREDIT
            // suggestion can't yield a credit draft; keep cardId only when it survives.
            val withMethod = base.withPaymentMethod(notification.suggestions.paymentMethod)
            if (withMethod.paymentMethod == PaymentMethod.CREDIT) {
                return withMethod.copy(cardId = notification.suggestions.cardId)
            }
            return withMethod
        }
    }
}
