package com.resolveprogramming.pocketcounter.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

/**
 * The hard constraint: neither unresolved state may offer a route that ends in creating a
 * transaction. "Revisar" is the dangerous one — it looks conservative while opening the wizard.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InvoicePaymentChoiceCardTest {

    @get:Rule
    val compose = createComposeRule()

    private fun presentation(canChoose: Boolean, reason: InvoiceReasonLine) = InvoicePromptPresentation(
        amount = BigDecimal("8866.19"),
        metaLine = "Nubank · agora",
        reason = reason,
        canChoose = canChoose,
        contentDescription = "Pagamento de fatura, R$ 8.866,19, Nubank. ${reason.spoken}",
    )

    private fun render(
        canChoose: Boolean,
        reason: InvoiceReasonLine = InvoiceReasonLine.Plain("Mais de uma fatura pendente tem esse valor."),
        onChoose: () -> Unit = {},
        onIgnore: () -> Unit = {},
    ) {
        compose.setContent {
            PocketTheme {
                InvoicePaymentChoiceCard(presentation(canChoose, reason), onChoose, onIgnore)
            }
        }
    }

    @Test
    fun `offers no Confirmar and no Revisar`() {
        render(canChoose = true)

        compose.onNodeWithText("Confirmar", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Revisar", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `offers the picker while candidates exist`() {
        var chosen = 0
        render(canChoose = true, onChoose = { chosen++ })

        compose.onNodeWithText("Escolher a fatura", useUnmergedTree = true).assertIsDisplayed().performClick()

        assertEquals(1, chosen)
    }

    @Test
    fun `drops the picker and promotes a labelled dismissal when nothing is pending`() {
        var ignored = 0
        render(
            canChoose = false,
            reason = InvoiceReasonLine.Plain("Nenhuma fatura pendente para marcar como paga."),
            onIgnore = { ignored++ },
        )

        compose.onNodeWithText("Escolher a fatura", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Ignorar", useUnmergedTree = true).assertIsDisplayed().performClick()

        assertEquals(1, ignored)
    }

    @Test
    fun `the conflict reason names both cards`() {
        render(canChoose = true, reason = InvoiceReasonLine.Conflict("Fatura Itaú", "Nubank"))

        compose
            .onNodeWithText("O valor bate com a Fatura Itaú, mas a notificação é do Nubank.", useUnmergedTree = true)
            .assertIsDisplayed()
    }
}
