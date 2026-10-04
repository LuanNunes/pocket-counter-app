package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.ClassifiedNotification
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ConfirmReadyTest {

    private fun notification(
        status: NotificationStatus,
        type: TransactionType? = TransactionType.EXPENSE,
        amount: BigDecimal? = BigDecimal("26.74"),
        paymentMethod: PaymentMethod? = PaymentMethod.PIX,
        idTag: String? = "tag-1",
        paymentHint: String? = null,
        text: String = "Compra aprovada DL*UberRides",
        merchant: String? = "DL*UberRides",
        app: String = "App",
    ) = NotificationItem(
        id = "n1",
        app = app,
        channel = NotificationChannel.PUSH,
        time = "agora",
        received = "2026-06-30T13:25:00Z",
        // The method now comes from the notification's own wording, never from a rule.
        text = text + methodWording(paymentMethod),
        status = status,
        parsed = ParsedNotification(
            type = type,
            amount = amount,
            date = LocalDate.of(2026, 6, 29),
            merchantRaw = merchant,
            paymentHint = paymentHint,
        ),
        suggestions = ClassificationSuggestion(idTag = idTag),
        tokens = emptyList(),
    )

    private val methodWordings = mapOf(
        PaymentMethod.PIX to " via pix",
        PaymentMethod.CREDIT to " no crédito",
    )

    /** The parser reads the method off the text, so a method only reaches a draft if it is worded in. */
    private fun methodWording(method: PaymentMethod?): String = methodWordings[method].orEmpty()

    @Test
    fun `AUTO notification with a saveable draft is confirm-ready`() {
        val item = confirmReadyItemOf(
            ClassifiedNotification(notification(NotificationStatus.AUTO), pendingTransactionId = null),
            NotificationEvidence(),
        )

        assertNotNull(item)
        assertEquals("n1", item!!.notificationId)
        assertNull(item.pendingTransactionId)
        assertEquals(TransactionType.EXPENSE, item.draft.type)
    }

    @Test
    fun `AUTO with CREDIT method but no card is not confirm-ready`() {
        // determineStatus can return AUTO without a cardId; one-tap must not create an invalid tx.
        val item = confirmReadyItemOf(
            ClassifiedNotification(
                notification(NotificationStatus.AUTO, paymentMethod = PaymentMethod.CREDIT),
                pendingTransactionId = null,
            ),
            NotificationEvidence(),
        )

        assertNull(item)
    }

    @Test
    fun `AUTO with CREDIT method and a card is confirm-ready`() {
        val item = confirmReadyItemOf(
            ClassifiedNotification(
                notification(
                    NotificationStatus.AUTO,
                    paymentMethod = PaymentMethod.CREDIT,
                    paymentHint = "final 3685",
                ),
                pendingTransactionId = null,
            ),
            NotificationEvidence(last4Map = mapOf("card-1" to "3685")),
        )

        assertNotNull(item)
        assertEquals("card-1", item!!.draft.cardId)
    }

    @Test
    fun `NEEDS_TAGS notification is not confirm-ready`() {
        val item = confirmReadyItemOf(
            ClassifiedNotification(notification(NotificationStatus.NEEDS_TAGS), pendingTransactionId = null),
            NotificationEvidence(),
        )

        assertNull(item)
    }

    @Test
    fun `NEEDS_REVIEW notification is not confirm-ready`() {
        val item = confirmReadyItemOf(
            ClassifiedNotification(notification(NotificationStatus.NEEDS_REVIEW), pendingTransactionId = null),
            NotificationEvidence(),
        )

        assertNull(item)
    }

    @Test
    fun `pending-transaction match is confirm-ready even when status needs review`() {
        val item = confirmReadyItemOf(
            ClassifiedNotification(
                notification(NotificationStatus.NEEDS_REVIEW),
                pendingTransactionId = "tx-99",
            ),
            NotificationEvidence(),
        )

        assertNotNull(item)
        assertEquals("tx-99", item!!.pendingTransactionId)
    }

    @Test
    fun `AUTO with no parsed type is not confirm-ready`() {
        val item = confirmReadyItemOf(
            ClassifiedNotification(
                notification(NotificationStatus.AUTO, type = null),
                pendingTransactionId = null,
            ),
            NotificationEvidence(),
        )

        assertNull(item)
    }

    @Test
    fun `AUTO invoice-payment confirmation with a null parsed type is never confirm-ready`() {
        // A fresh parse only: once RemoteMappers.toClassified rebuilds `parsed` from server storage,
        // an already-captured push carries type = EXPENSE instead of null, guarded separately in
        // HomeViewModel.classifyOne (see HomeViewModelTest).
        val parsed = BrNotificationParser.parse(
            "Nubank Recebemos seu pagamento no valor de R\$ 8.866,19. Obrigado!",
        ).parsed

        val item = confirmReadyItemOf(
            ClassifiedNotification(
                notification(NotificationStatus.AUTO, type = parsed.type, amount = parsed.amount),
                pendingTransactionId = null,
            ),
            NotificationEvidence(),
        )

        assertNull(item)
    }

    @Test
    fun `the card comes from the notification's last4 evidence`() {
        val item = confirmReadyItemOf(
            ClassifiedNotification(
                notification(
                    NotificationStatus.AUTO,
                    paymentMethod = PaymentMethod.CREDIT,
                    paymentHint = "final 3685",
                ),
                pendingTransactionId = null,
            ),
            NotificationEvidence(last4Map = mapOf("card-itau" to "3685")),
        )

        assertNotNull(item)
        assertEquals("card-itau", item!!.draft.cardId)
    }

    @Test
    fun `an unmapped last4 demotes an otherwise AUTO item`() {
        val item = confirmReadyItemOf(
            ClassifiedNotification(
                notification(
                    NotificationStatus.AUTO,
                    paymentMethod = PaymentMethod.CREDIT,
                    paymentHint = "final 9999",
                ),
                pendingTransactionId = null,
            ),
            NotificationEvidence(last4Map = mapOf("card-itau" to "3685")),
        )

        assertNull(item)
    }

    private fun classified(
        status: NotificationStatus = NotificationStatus.AUTO,
        pendingTransactionId: String? = null,
        paymentMethod: PaymentMethod? = null,
        text: String = "Compra no crédito R$ 26,74",
        merchant: String? = "DL*UberRides",
        paymentHint: String? = null,
        app: String = "App",
    ) = ClassifiedNotification(
        notification(
            status,
            paymentMethod = paymentMethod,
            text = text,
            merchant = merchant,
            paymentHint = paymentHint,
            app = app,
        ),
        pendingTransactionId,
    )

    private val itau = CreditCard(
        id = "card-itau",
        name = "Itaú",
        brand = "Mastercard",
        last4 = "0000",
        gradientStart = 0xFF000000L,
        gradientEnd = 0xFF000000L,
        limit = BigDecimal("1000.00"),
        billDay = 10,
    )

    @Test
    fun `a text-derived credit with no card is not confirm-ready`() {
        assertNull(confirmReadyItemOf(classified(), NotificationEvidence()))
    }

    @Test
    fun `a text-derived credit with a matching issuer card is confirm-ready`() {
        val item = confirmReadyItemOf(
            classified(app = "Banco Itaú"),
            NotificationEvidence(cards = listOf(itau)),
        )

        assertNotNull(item)
        assertEquals(PaymentMethod.CREDIT, item!!.draft.paymentMethod)
        assertEquals("card-itau", item.draft.cardId)
    }

    @Test
    fun `a text-derived credit with an unmapped last4 is not confirm-ready`() {
        val item = confirmReadyItemOf(
            classified(paymentHint = "final 9999", app = "Banco Itaú"),
            NotificationEvidence(last4Map = mapOf("card-itau" to "3685"), cards = listOf(itau)),
        )

        assertNull(item)
    }

    @Test
    fun `an AUTO push with no merchant is not confirm-ready`() {
        val item = confirmReadyItemOf(
            classified(paymentMethod = PaymentMethod.PIX, merchant = null),
            NotificationEvidence(),
        )

        assertNull(item)
    }

    @Test
    fun `an AUTO push whose merchant is only digits is not confirm-ready`() {
        val item = confirmReadyItemOf(
            classified(paymentMethod = PaymentMethod.PIX, merchant = "29"),
            NotificationEvidence(),
        )

        assertNull(item)
    }

    @Test
    fun `a pending match with no merchant is still confirm-ready`() {
        val item = confirmReadyItemOf(
            classified(paymentMethod = PaymentMethod.PIX, merchant = null, pendingTransactionId = "tx-99"),
            NotificationEvidence(),
        )

        assertNotNull(item)
        assertEquals("tx-99", item!!.pendingTransactionId)
    }
}
