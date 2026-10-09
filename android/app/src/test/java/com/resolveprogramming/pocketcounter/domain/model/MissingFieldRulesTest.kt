package com.resolveprogramming.pocketcounter.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MissingFieldRulesTest {

    @Test
    fun isSatisfiedBy_amount_null_isNotSatisfied() {
        assertFalse(MissingField.AMOUNT.isSatisfiedBy(WizardDraft()))
    }

    @Test
    fun isSatisfiedBy_amount_positive_isSatisfied() {
        val draft = WizardDraft(amount = BigDecimal("0.01"))

        assertTrue(MissingField.AMOUNT.isSatisfiedBy(draft))
    }

    @Test
    fun isSatisfiedBy_amount_zero_isNotSatisfied() {
        val draft = WizardDraft(amount = BigDecimal.ZERO)

        assertFalse(MissingField.AMOUNT.isSatisfiedBy(draft))
    }

    @Test
    fun isSatisfiedBy_description_usableName_isSatisfied() {
        val draft = WizardDraft(name = "Item")

        assertTrue(MissingField.DESCRIPTION.isSatisfiedBy(draft))
    }

    @Test
    fun isSatisfiedBy_description_blankName_isNotSatisfied() {
        val draft = WizardDraft(name = "   ")

        assertFalse(MissingField.DESCRIPTION.isSatisfiedBy(draft))
    }

    @Test
    fun isSatisfiedBy_type_set_isSatisfied() {
        val draft = WizardDraft(type = TransactionType.EXPENSE)

        assertTrue(MissingField.TYPE.isSatisfiedBy(draft))
    }

    @Test
    fun isSatisfiedBy_type_null_isNotSatisfied() {
        assertFalse(MissingField.TYPE.isSatisfiedBy(WizardDraft()))
    }

    /** The card ask is skippable, so it never holds the queue. */
    @Test
    fun isSatisfiedBy_card_withoutACardId_isStillSatisfied() {
        val draft = WizardDraft(paymentMethod = PaymentMethod.CREDIT)

        assertTrue(MissingField.CARD.isSatisfiedBy(draft))
    }
}
