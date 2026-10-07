package com.resolveprogramming.pocketcounter.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class WizardDraft(
    val type: TransactionType? = null,
    /**
     * Sign is not guaranteed: only the wizard-save path runs [isStep2Valid]. Take the magnitude and
     * apply the direction from [type].
     */
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

    /**
     * Whether [name] — the only title `toDto` persists — will read as one. A blank or letterless
     * name saves a row that renders as [HistoryItem.displayTitle]'s `"—"`.
     *
     * Deliberately outside every `isStepNValid`: Descrição lives on step 2, and step 1 is shared
     * with the manual form.
     */
    fun hasUsableName(): Boolean = name?.let { it.isNotBlank() && it.any(Char::isLetter) } == true

    fun withPaymentMethod(method: PaymentMethod?): WizardDraft {
        if (method == PaymentMethod.CREDIT && type == TransactionType.INCOME) return this
        return copy(
            paymentMethod = method,
            cardId = cardId.takeIf { method == PaymentMethod.CREDIT },
        )
    }

    /**
     * Applies CREDIT + [cardId], keeping the card only when the credit guard holds — an income
     * draft must not carry a card id.
     */
    fun withCard(cardId: String): WizardDraft {
        val withMethod = withPaymentMethod(PaymentMethod.CREDIT)
        if (withMethod.paymentMethod != PaymentMethod.CREDIT) return withMethod
        return withMethod.copy(cardId = cardId)
    }

    /**
     * Drops tag ids absent from [tags]. A read can suggest a tag the user no longer has, and the write
     * rejects the whole row for it while the preview already renders "sem categoria".
     */
    fun withTagsKnownIn(tags: List<Tag>): WizardDraft {
        val known = tags.mapTo(HashSet()) { it.id }
        val kept = tagIds.filter { it in known }
        if (kept.size == tagIds.size) return this
        return copy(tagIds = kept)
    }

    fun withoutCard(): WizardDraft = copy(cardId = null)

    fun withTagToggled(tagId: String): WizardDraft {
        if (tagId in tagIds) return copy(tagIds = tagIds - tagId)
        return copy(tagIds = tagIds + tagId)
    }

    /** Idempotent select: unlike [withTagToggled], re-applying never deselects. */
    fun withTagSelected(tagId: String): WizardDraft {
        if (tagId in tagIds) return this
        return copy(tagIds = tagIds + tagId)
    }

    /**
     * The tag a taught rule would carry: the first one the user picked ([tagIds] is append-ordered)
     * that is an expense tag, since rules cannot reference income tags. Resolved through [tags] by id,
     * never by filtering the catalog, which would re-sort the pick into catalog order.
     */
    fun teachableTag(tags: List<Tag>): Tag? {
        if (type == TransactionType.INCOME) return null
        val byId = tags.associateBy { it.id }
        return tagIds.firstNotNullOfOrNull { id -> byId[id]?.takeIf { it.kind == TransactionType.EXPENSE } }
    }

    /** Sets the type, dropping a credit payment that an income can't hold (mirrors [withPaymentMethod]). */
    fun withType(type: TransactionType): WizardDraft {
        if (type == TransactionType.INCOME && paymentMethod == PaymentMethod.CREDIT) {
            return copy(type = type, paymentMethod = null, cardId = null)
        }
        return copy(type = type)
    }

    companion object {
        fun fromIntent(intent: TransactionIntent): WizardDraft {
            val seeded = WizardDraft(
                type = intent.reading.type,
                amount = intent.reading.amount?.abs(),
                date = intent.reading.date,
                // statusPayment stays at the default: a sentence carries no reading of it, and the
                // Situação row's choice is what reaches the wire.
                tagIds = listOfNotNull(intent.idTag),
                name = intent.reading.name,
                merchant = intent.reading.name,
            )
            val cardId = intent.resolvedCard?.id
            if (intent.reading.paymentMethod == PaymentMethod.CREDIT && cardId != null) {
                return seeded.withCard(cardId)
            }
            return seeded.withPaymentMethod(intent.reading.paymentMethod)
        }

        fun fromNotification(
            notification: NotificationItem,
        ): WizardDraft {
            // The type comes from the text alone: the classifier no longer suggests one, so a
            // notification that does not reveal income/expense opens without a type.
            return WizardDraft(
                type = notification.parsed.type,
                amount = notification.parsed.amount,
                date = notification.parsed.date ?: LocalDate.now(),
                // isFixo is a user toggle ("Repete todo mês"); the classify suggestion
                // doesn't carry it, so a fresh draft always starts non-fixo.
                tagIds = listOfNotNull(notification.suggestions.idTag),
                // Seed the persisted title (name) and the series/hint fallback (merchant) from the
                // parsed merchant so the wizard's "Descrição" field opens pre-filled and editable.
                name = notification.parsed.merchantRaw,
                merchant = notification.parsed.merchantRaw,
                installments = notification.parsed.installments,
                installmentValue = notification.parsed.installmentValue,
            )
        }
    }
}

/**
 * Whether [draft] now holds what the server reported missing, i.e. the ask can move on. CARD is
 * always satisfied: that ask is skippable, so it never holds the queue.
 */
fun MissingField.isSatisfiedBy(draft: WizardDraft): Boolean = when (this) {
    MissingField.AMOUNT -> draft.amount != null && draft.amount > BigDecimal.ZERO
    MissingField.DESCRIPTION -> draft.hasUsableName()
    MissingField.TYPE -> draft.type != null
    MissingField.CARD -> true
}
