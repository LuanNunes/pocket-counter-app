package com.resolveprogramming.pocketcounter.ui.wizard

import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnPatternHintTest {

    private val food = Tag("food", "Alimentação", TransactionType.EXPENSE)
    private val fun_ = Tag("fun", "Lazer", TransactionType.EXPENSE)
    private val salary = Tag("sal", "Salário", TransactionType.INCOME)

    private fun state(type: TransactionType?, vararg picked: String) = WizardUiState(
        draft = WizardDraft(type = type, tagIds = picked.toList()),
        allTags = listOf(food, fun_, salary),
    )

    @Test
    fun oneTag_namesItAndPromisesAutomaticTagging() {
        assertEquals(
            "Próximas notificações com \"Padaria\" vão receber a tag Alimentação automaticamente.",
            learnPatternHint(state(TransactionType.EXPENSE, "food"), "Padaria"),
        )
    }

    @Test
    fun severalTags_namesTheFirstPickedAndSaysTheOthersAreOneOff() {
        assertEquals(
            "Próximas notificações com \"Padaria\" vão receber a tag Lazer. " +
                "As outras tags valem só para este lançamento.",
            learnPatternHint(state(TransactionType.EXPENSE, "fun", "food"), "Padaria"),
        )
    }

    @Test
    fun noTags_asksForOne() {
        assertEquals(
            "Escolha uma tag para aprender o padrão.",
            learnPatternHint(state(TransactionType.EXPENSE), "Padaria"),
        )
    }

    @Test
    fun anIncomeTagPickedFirst_doesNotBlockALaterExpenseTag() {
        assertEquals(
            "Próximas notificações com \"Padaria\" vão receber a tag Alimentação. " +
                "As outras tags valem só para este lançamento.",
            learnPatternHint(state(TransactionType.EXPENSE, "sal", "food"), "Padaria"),
        )
        assertTrue(state(TransactionType.EXPENSE, "sal", "food").canTeachRule)
    }

    @Test
    fun income_saysRulesAreExpenseOnly() {
        assertEquals(
            "Regras valem só para despesas.",
            learnPatternHint(state(TransactionType.INCOME, "sal"), "Padaria"),
        )
    }

    @Test
    fun canTeachRule_isFalseForIncomeAndWithoutTags_trueForAnExpenseTag() {
        assertFalse(state(TransactionType.INCOME, "sal").canTeachRule)
        assertFalse(state(TransactionType.EXPENSE).canTeachRule)
        assertTrue(state(TransactionType.EXPENSE, "food").canTeachRule)
    }
}
