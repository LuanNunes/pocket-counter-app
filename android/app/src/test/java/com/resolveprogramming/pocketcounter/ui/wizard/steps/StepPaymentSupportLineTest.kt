package com.resolveprogramming.pocketcounter.ui.wizard.steps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The support line is the only thing standing in for the method a rule used to carry. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class StepPaymentSupportLineTest {

    @get:Rule
    val compose = createComposeRule()

    private val supportLine =
        "Marque a palavra da forma de pagamento no texto acima para o Pocket aprender."

    private fun render(paymentPrefilled: Boolean, selectedMethod: PaymentMethod? = null) {
        compose.setContent {
            PocketTheme {
                StepPayment(
                    type = TransactionType.EXPENSE,
                    cards = emptyList(),
                    selectedMethod = selectedMethod,
                    selectedCardId = null,
                    enabledMethods = setOf(PaymentMethod.PIX, PaymentMethod.CASH),
                    paymentPrefilled = paymentPrefilled,
                    onSelectMethod = {},
                    onSelectCard = {},
                )
            }
        }
    }

    @Test
    fun `nothing prefilled shows the support line`() {
        render(paymentPrefilled = false)

        compose.onNodeWithText(supportLine).assertIsDisplayed()
    }

    @Test
    fun `a prefilled method hides the support line`() {
        render(paymentPrefilled = true, selectedMethod = PaymentMethod.PIX)

        compose.onNodeWithText(supportLine).assertDoesNotExist()
    }
}
