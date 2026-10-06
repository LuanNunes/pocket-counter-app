package com.resolveprogramming.pocketcounter.ui.cards

import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class LearnRuleHintTest {

    private val food = Tag("food", "Alimentação", TransactionType.EXPENSE)
    private val fun_ = Tag("fun", "Lazer", TransactionType.EXPENSE)
    private val salary = Tag("sal", "Salário", TransactionType.INCOME)

    @Test
    fun oneTag_promisesAnyCardAndNamesTheTag() {
        assertEquals(
            "Próximas compras contendo \"Padaria\" recebem a tag Alimentação automaticamente, em qualquer cartão.",
            learnRuleHint("Padaria", listOf(food)),
        )
    }

    @Test
    fun severalTags_namesTheFirstPickedAndSaysTheOthersAreOneOff() {
        assertEquals(
            "Próximas compras contendo \"Padaria\" recebem a tag Lazer. As outras valem só para esta compra.",
            learnRuleHint("Padaria", listOf(fun_, food)),
        )
    }

    @Test
    fun theTaughtTagIsTheFirstExpenseOne() {
        assertEquals(
            "Próximas compras contendo \"Padaria\" recebem a tag Lazer. As outras valem só para esta compra.",
            learnRuleHint("Padaria", listOf(salary, fun_)),
        )
    }

    @Test
    fun noExpenseTag_asksForOne() {
        assertEquals(
            "Escolha uma tag de despesa para aprender o padrão.",
            learnRuleHint("Padaria", listOf(salary)),
        )
    }

    @Test
    fun aGatewayMarkerName_promisesNoRule_becauseTheWriteWouldBeRefused() {
        assertEquals(
            "Não é possível aprender um padrão a partir de \"Ifd*\".",
            learnRuleHint("Ifd*", listOf(food)),
        )
    }

    @Test
    fun theNameIsSanitizedBeforeBeingPromised() {
        assertEquals(
            "Próximas compras contendo \"Padaria\" recebem a tag Alimentação automaticamente, em qualquer cartão.",
            learnRuleHint("Padaria -", listOf(food)),
        )
    }
}
