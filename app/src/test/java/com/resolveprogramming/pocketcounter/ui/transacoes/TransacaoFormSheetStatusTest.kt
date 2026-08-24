package com.resolveprogramming.pocketcounter.ui.transacoes

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethodPreferences
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate

/** The sheet must open on the row's real status — a wrong one is committed by just pressing Salvar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class TransacaoFormSheetStatusTest {

    @get:Rule
    val compose = createComposeRule()

    private fun expense(status: PaymentStatus, method: PaymentMethod? = PaymentMethod.PIX) = HistoryItem(
        id = "tx-1",
        date = LocalDate.of(2026, 8, 20),
        amount = BigDecimal("-42.50"),
        type = TransactionType.EXPENSE,
        tagIds = null,
        statusPayment = status,
        paymentMethod = method,
        name = "Mercado",
    )

    private fun render(item: HistoryItem) {
        compose.setContent {
            PocketTheme {
                TransacaoFormSheet(
                    mode = FormMode.Edit(item.id),
                    initialItem = item,
                    initialType = null,
                    cards = emptyList(),
                    tags = emptyList(),
                    contexts = emptyList(),
                    enabledMethods = PaymentMethodPreferences.default,
                    onSave = {},
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun `editing a settled expense opens on Efetuada`() {
        render(expense(PaymentStatus.PAID))

        compose.onNodeWithText("Efetuada").assertIsSelected()
        compose.onNodeWithText("Pendente").assertIsNotSelected()
    }

    @Test
    fun `editing a settled credit expense opens on Efetuada`() {
        render(expense(PaymentStatus.PAID, method = PaymentMethod.CREDIT))

        compose.onNodeWithText("Efetuada").assertIsSelected()
    }

    @Test
    fun `editing an unpaid expense opens on Pendente`() {
        render(expense(PaymentStatus.PENDING))

        compose.onNodeWithText("Pendente").assertIsSelected()
        compose.onNodeWithText("Efetuada").assertIsNotSelected()
    }
}
