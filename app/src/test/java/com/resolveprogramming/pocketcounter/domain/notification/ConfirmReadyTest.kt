package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.ClassifiedNotification
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
        cardId: String? = null,
        suggestedType: TransactionType? = null,
        tagIds: List<String> = listOf("tag-1"),
    ) = NotificationItem(
        id = "n1",
        app = "App",
        channel = NotificationChannel.PUSH,
        time = "agora",
        received = "2026-06-30T13:25:00Z",
        text = "Compra aprovada DL*UberRides",
        status = status,
        parsed = ParsedNotification(
            type = type,
            amount = amount,
            date = LocalDate.of(2026, 6, 29),
            merchantRaw = "DL*UberRides",
            paymentHint = null,
        ),
        suggestions = ClassificationSuggestion(
            tagIds = tagIds,
            paymentMethod = paymentMethod,
            cardId = cardId,
            transactionType = suggestedType,
        ),
        tokens = emptyList(),
    )

    @Test
    fun `AUTO notification with a saveable draft is confirm-ready`() {
        val item = confirmReadyItemOf(
            ClassifiedNotification(notification(NotificationStatus.AUTO), pendingTransactionId = null),
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
                notification(NotificationStatus.AUTO, paymentMethod = PaymentMethod.CREDIT, cardId = null),
                pendingTransactionId = null,
            ),
        )

        assertNull(item)
    }

    @Test
    fun `AUTO with CREDIT method and a card is confirm-ready`() {
        val item = confirmReadyItemOf(
            ClassifiedNotification(
                notification(NotificationStatus.AUTO, paymentMethod = PaymentMethod.CREDIT, cardId = "card-1"),
                pendingTransactionId = null,
            ),
        )

        assertNotNull(item)
        assertEquals("card-1", item!!.draft.cardId)
    }

    @Test
    fun `NEEDS_TAGS notification is not confirm-ready`() {
        val item = confirmReadyItemOf(
            ClassifiedNotification(notification(NotificationStatus.NEEDS_TAGS), pendingTransactionId = null),
        )

        assertNull(item)
    }

    @Test
    fun `NEEDS_REVIEW notification is not confirm-ready`() {
        val item = confirmReadyItemOf(
            ClassifiedNotification(notification(NotificationStatus.NEEDS_REVIEW), pendingTransactionId = null),
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
        )

        assertNotNull(item)
        assertEquals("tx-99", item!!.pendingTransactionId)
    }

    @Test
    fun `AUTO with no type and no suggested type is not confirm-ready`() {
        val item = confirmReadyItemOf(
            ClassifiedNotification(
                notification(NotificationStatus.AUTO, type = null, suggestedType = null),
                pendingTransactionId = null,
            ),
        )

        assertNull(item)
    }

    @Test
    fun `AUTO with no parsed type but a suggested type is confirm-ready`() {
        val item = confirmReadyItemOf(
            ClassifiedNotification(
                notification(NotificationStatus.AUTO, type = null, suggestedType = TransactionType.EXPENSE),
                pendingTransactionId = null,
            ),
        )

        assertNotNull(item)
        assertEquals(TransactionType.EXPENSE, item!!.draft.type)
    }
}
