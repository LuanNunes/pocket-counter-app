package com.resolveprogramming.pocketcounter.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.ConfirmReadyItem
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.ui.components.LedgerLookups
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate

/** The matched card must be titled with the row the user will see change, not with the push. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConfirmReadyCardInvoiceTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 8, 5)

    private val invoice = HistoryItem(
        id = "inv-1",
        date = LocalDate.of(2026, 8, 10),
        amount = BigDecimal("-8886.92"),
        type = TransactionType.EXPENSE,
        tagIds = null,
        statusPayment = PaymentStatus.PENDING,
        paymentMethod = PaymentMethod.CREDIT,
        cardId = "card-nu",
        name = "Fatura Nubank",
        isInvoice = true,
    )

    private val item = ConfirmReadyItem(
        notificationId = "n1",
        // No merchant and no type: without pendingMatch this card would fall back to "Lançamento".
        draft = WizardDraft(type = null, amount = BigDecimal("8866.19"), name = null, tagIds = emptyList()),
        pendingTransactionId = invoice.id,
        notification = NotificationItem(
            id = "n1",
            app = "Nubank",
            channel = NotificationChannel.PUSH,
            time = "agora",
            received = "2026-08-05T13:25:00Z",
            text = "Nubank Recebemos seu pagamento no valor de R$ 8.866,19. Obrigado!",
            status = NotificationStatus.NEEDS_REVIEW,
            parsed = ParsedNotification(
                type = null,
                amount = BigDecimal("8866.19"),
                date = today,
                merchantRaw = null,
                paymentHint = null,
            ),
            suggestions = ClassificationSuggestion(tagIds = emptyList()),
            tokens = emptyList(),
        ),
        pendingMatch = invoice,
    )

    private fun render(onConfirm: () -> Unit = {}) {
        compose.setContent {
            PocketTheme {
                ConfirmReadyCard(
                    item = item,
                    presentation = confirmReadyPresentation(
                        item,
                        LedgerLookups(cards = emptyMap(), tags = emptyMap(), contexts = emptyMap()),
                        today,
                    ),
                    isConfirming = false,
                    onConfirm = onConfirm,
                    onReview = {},
                    onIgnore = {},
                )
            }
        }
    }

    @Test
    fun `renders the invoice name, not the neutral fallback`() {
        render()

        compose.onNodeWithText("Fatura Nubank", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Lançamento", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `renders the invoice due date, its status pill and the by-item tag label`() {
        render()

        compose.onNodeWithText("10 ago", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("PENDENTE", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("por item", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("sem categoria", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the primary action names the invoice and still fires`() {
        var confirmed = 0
        render(onConfirm = { confirmed++ })

        compose.onNodeWithContentDescription("Confirmar pagamento de Fatura Nubank").performClick()

        assertEquals(1, confirmed)
    }

    @Test
    fun `a matched invoice card has no Revisar affordance`() {
        // The wizard resolves its pending match from the backend only; routing there for a
        // client-resolved match would create a duplicate transaction.
        render()

        compose.onNodeWithText("Revisar", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `an ordinary confirm-ready card still has a Revisar affordance`() {
        val ordinary = ConfirmReadyItem(
            notificationId = "n2",
            draft = WizardDraft(
                type = TransactionType.EXPENSE,
                amount = BigDecimal("50.00"),
                name = "Mercado",
                tagIds = emptyList(),
            ),
            pendingTransactionId = null,
            notification = item.notification,
        )
        compose.setContent {
            PocketTheme {
                ConfirmReadyCard(
                    item = ordinary,
                    presentation = confirmReadyPresentation(
                        ordinary,
                        LedgerLookups(cards = emptyMap(), tags = emptyMap(), contexts = emptyMap()),
                        today,
                    ),
                    isConfirming = false,
                    onConfirm = {},
                    onReview = {},
                    onIgnore = {},
                )
            }
        }

        compose.onNodeWithText("Revisar", useUnmergedTree = true).assertIsDisplayed()
    }
}
