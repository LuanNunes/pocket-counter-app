package com.resolveprogramming.pocketcounter.ui.cards

import com.resolveprogramming.pocketcounter.data.repository.PurchaseClassifyOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class PurchaseClassifyOutcomeToastTest {

    @Test
    fun eachOutcomeHasItsOwnToast() {
        assertEquals("Compra classificada ✓", PurchaseClassifyOutcome.TagsOnly.toastMessage())
        assertEquals("Classificada ✓ + regra criada", PurchaseClassifyOutcome.RuleCreated.toastMessage())
        assertEquals("Classificada ✓ · regra já existia", PurchaseClassifyOutcome.RuleAlreadyExisted.toastMessage())
        assertEquals("Classificada ✓ (regra falhou)", PurchaseClassifyOutcome.RuleFailed.toastMessage())
    }
}
