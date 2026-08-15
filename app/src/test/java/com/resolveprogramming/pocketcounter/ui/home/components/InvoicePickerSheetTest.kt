package com.resolveprogramming.pocketcounter.ui.home.components

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.components.LedgerLookups
import com.resolveprogramming.pocketcounter.ui.home.InvoicePaymentPrompt
import com.resolveprogramming.pocketcounter.ui.home.InvoicePickerState
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Select first, commit second — the mismatch warning has to land between the two.
 *
 * Taps go through the OnClick semantics action: Robolectric does not deliver injected pointer
 * events to the sheet's dialog window, so `performClick` silently does nothing here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class InvoicePickerSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 8, 5)

    private val lookups = LedgerLookups(
        cards = mapOf("card-nu" to CreditCard("card-nu", "Nubank", "MASTER", "1234", 0L, 0L, BigDecimal.ZERO, 10)),
        tags = emptyMap(),
        contexts = emptyMap(),
    )

    private fun invoice(id: String, amount: String, name: String) = HistoryItem(
        id = id,
        date = LocalDate.of(2026, 8, 10),
        amount = BigDecimal(amount),
        type = TransactionType.EXPENSE,
        tagIds = null,
        statusPayment = PaymentStatus.PENDING,
        paymentMethod = PaymentMethod.CREDIT,
        cardId = "card-nu",
        name = name,
        isInvoice = true,
    )

    private fun render(vararg candidates: HistoryItem, onConfirm: (HistoryItem) -> Unit = {}) {
        val prompt = InvoicePaymentPrompt(
            notification = NotificationItem(
                id = "n1",
                app = "Nubank",
                channel = NotificationChannel.PUSH,
                time = "agora",
                received = "2026-08-05T13:25:00Z",
                text = "Nubank Recebemos seu pagamento no valor de R$ 4.000,00. Obrigado!",
                status = NotificationStatus.NEEDS_REVIEW,
                parsed = ParsedNotification(
                    type = null,
                    amount = BigDecimal("4000.00"),
                    date = today,
                    merchantRaw = null,
                    paymentHint = null,
                ),
                suggestions = ClassificationSuggestion(tagIds = emptyList()),
                tokens = emptyList(),
            ),
            candidates = candidates.toList(),
        )
        compose.setContent {
            PocketTheme {
                InvoicePickerSheet(
                    picker = InvoicePickerState(prompt = prompt),
                    lookups = lookups,
                    onConfirm = onConfirm,
                    onDismiss = {},
                )
            }
        }
    }

    private fun selectFaturaNubank() = compose
        .onNodeWithContentDescription("Fatura Nubank", substring = true)
        .performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun `the CTA stays disabled until a row is selected`() {
        render(invoice("i1", "-8886.92", "Fatura Nubank"))

        compose.onNodeWithText("Confirmar pagamento").assertIsNotEnabled()
    }

    @Test
    fun `selecting a row surfaces the mismatch consequence before the write`() {
        render(invoice("i1", "-8886.92", "Fatura Nubank"))

        selectFaturaNubank()

        compose.onNodeWithContentDescription("Fatura Nubank", substring = true).assertIsSelected()
        compose
            .onNodeWithText("a fatura inteira como paga", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun `confirming reports the selected row`() {
        var confirmed: HistoryItem? = null
        render(invoice("i1", "-8886.92", "Fatura Nubank"), onConfirm = { confirmed = it })

        selectFaturaNubank()
        compose.onNodeWithText("Confirmar pagamento").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("i1", confirmed?.id)
    }

    @Test
    fun `an empty candidate list explains the absence instead of showing a bare list`() {
        render()

        compose.onNodeWithText("Nenhuma fatura pendente.", useUnmergedTree = true).assertIsDisplayed()
        compose
            .onNodeWithText("Faturas aparecem aqui quando um cartão fecha.", useUnmergedTree = true)
            .assertIsDisplayed()
    }
}
