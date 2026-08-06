package com.resolveprogramming.pocketcounter.ui.home.components

import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.ConfirmReadyItem
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ConfirmReadyPresentationTest {

    private val today = LocalDate.of(2026, 6, 20)

    private val cards = emptyMap<String, CreditCard>()
    private val tagMercado = Tag("t-mercado", "Mercado", TransactionType.EXPENSE, idContext = "c1")
    private val tags = mapOf(tagMercado.id to tagMercado)
    private val contexts = emptyMap<String, TagContext>()

    private fun draft(
        type: TransactionType = TransactionType.EXPENSE,
        amount: BigDecimal? = BigDecimal("129.90"),
        date: LocalDate = LocalDate.of(2026, 6, 14),
        name: String? = "Mercado Central",
        tagIds: List<String> = listOf(tagMercado.id),
        installments: Int? = null,
    ) = WizardDraft(
        type = type,
        amount = amount,
        date = date,
        name = name,
        tagIds = tagIds,
        installments = installments,
    )

    private fun notification(merchantRaw: String? = "Mercado Central") = NotificationItem(
        id = "n1",
        app = "App",
        channel = NotificationChannel.PUSH,
        time = "agora",
        received = "2026-06-14T13:25:00Z",
        text = "Compra aprovada",
        status = NotificationStatus.AUTO,
        parsed = ParsedNotification(
            type = TransactionType.EXPENSE,
            amount = BigDecimal("129.90"),
            date = LocalDate.of(2026, 6, 14),
            merchantRaw = merchantRaw,
            paymentHint = null,
        ),
        suggestions = ClassificationSuggestion(tagIds = listOf(tagMercado.id)),
        tokens = emptyList(),
    )

    private fun item(
        draft: WizardDraft = draft(),
        pendingTransactionId: String? = null,
        merchantRaw: String? = "Mercado Central",
    ) = ConfirmReadyItem(
        notificationId = "n1",
        draft = draft,
        pendingTransactionId = pendingTransactionId,
        notification = notification(merchantRaw = merchantRaw),
    )

    // -- signedAmount --

    @Test
    fun `signedAmount is negative for an EXPENSE`() {
        val presentation = confirmReadyPresentation(
            item(draft(type = TransactionType.EXPENSE, amount = BigDecimal("129.90"))),
            cards, tags, contexts, today,
        )

        assertEquals(BigDecimal("-129.90"), presentation.signedAmount)
    }

    @Test
    fun `signedAmount is positive for an INCOME`() {
        val presentation = confirmReadyPresentation(
            item(draft(type = TransactionType.INCOME, amount = BigDecimal("50.00"))),
            cards, tags, contexts, today,
        )

        assertEquals(BigDecimal("50.00"), presentation.signedAmount)
    }

    @Test
    fun `signedAmount is null when the draft has no amount`() {
        val presentation = confirmReadyPresentation(
            item(draft(amount = null)),
            cards, tags, contexts, today,
        )

        assertNull(presentation.signedAmount)
    }

    // -- title --

    @Test
    fun `title prefers the draft name`() {
        val presentation = confirmReadyPresentation(
            item(draft(name = "Mercado Central"), merchantRaw = "Other Store"),
            cards, tags, contexts, today,
        )

        assertEquals("Mercado Central", presentation.title)
    }

    @Test
    fun `title falls back to the parsed merchant when draft name is null`() {
        val presentation = confirmReadyPresentation(
            item(draft(name = null), merchantRaw = "Uber Trip"),
            cards, tags, contexts, today,
        )

        assertEquals("Uber Trip", presentation.title)
    }

    @Test
    fun `title falls back to the parsed merchant when draft name is blank`() {
        val presentation = confirmReadyPresentation(
            item(draft(name = "   "), merchantRaw = "Uber Trip"),
            cards, tags, contexts, today,
        )

        assertEquals("Uber Trip", presentation.title)
    }

    @Test
    fun `title rejects a stored merchant with no letters and falls back to the first tag name`() {
        val presentation = confirmReadyPresentation(
            item(draft(name = null, tagIds = listOf(tagMercado.id)), merchantRaw = "29"),
            cards, tags, contexts, today,
        )

        assertEquals(tagMercado.name, presentation.title)
    }

    @Test
    fun `title falls back to Lancamento when nothing usable is available`() {
        val presentation = confirmReadyPresentation(
            item(draft(name = null, tagIds = emptyList()), merchantRaw = "29"),
            cards, tags, contexts, today,
        )

        assertEquals("Lançamento", presentation.title)
    }

    // -- confirmLabel --

    @Test
    fun `confirmLabel is Confirmar when there is no pending transaction id`() {
        val presentation = confirmReadyPresentation(
            item(pendingTransactionId = null),
            cards, tags, contexts, today,
        )

        assertEquals("Confirmar", presentation.confirmLabel)
    }

    @Test
    fun `confirmLabel is Confirmar pagamento when a pending transaction id is present`() {
        val presentation = confirmReadyPresentation(
            item(pendingTransactionId = "tx-99"),
            cards, tags, contexts, today,
        )

        assertEquals("Confirmar pagamento", presentation.confirmLabel)
    }

    // -- installmentsLabel --

    @Test
    fun `installmentsLabel is 3x when draft installments is 3`() {
        val presentation = confirmReadyPresentation(
            item(draft(installments = 3)),
            cards, tags, contexts, today,
        )

        assertEquals("3×", presentation.installmentsLabel)
    }

    @Test
    fun `installmentsLabel is null when the draft has no installments`() {
        val presentation = confirmReadyPresentation(
            item(draft(installments = null)),
            cards, tags, contexts, today,
        )

        assertNull(presentation.installmentsLabel)
    }

    // -- contentDescription --

    @Test
    fun `contentDescription for an EXPENSE contains despesa`() {
        val presentation = confirmReadyPresentation(
            item(draft(type = TransactionType.EXPENSE)),
            cards, tags, contexts, today,
        )

        assertTrue(presentation.contentDescription.contains("despesa"))
    }

    @Test
    fun `contentDescription for an INCOME contains receita`() {
        val presentation = confirmReadyPresentation(
            item(draft(type = TransactionType.INCOME)),
            cards, tags, contexts, today,
        )

        assertTrue(presentation.contentDescription.contains("receita"))
    }

    @Test
    fun `contentDescription never contains the AmountText minus glyph`() {
        val presentation = confirmReadyPresentation(
            item(draft(type = TransactionType.EXPENSE, amount = BigDecimal("129.90"))),
            cards, tags, contexts, today,
        )

        assertFalse(presentation.contentDescription.contains('−'))
    }

    @Test
    fun `contentDescription uses hoje for today's date`() {
        val presentation = confirmReadyPresentation(
            item(draft(date = today)),
            cards, tags, contexts, today,
        )

        assertTrue(presentation.contentDescription.contains("hoje"))
    }

    @Test
    fun `contentDescription uses ontem for yesterday's date`() {
        val presentation = confirmReadyPresentation(
            item(draft(date = today.minusDays(1))),
            cards, tags, contexts, today,
        )

        assertTrue(presentation.contentDescription.contains("ontem"))
    }

    @Test
    fun `contentDescription uses a spoken day-and-month for other dates`() {
        val presentation = confirmReadyPresentation(
            item(draft(date = LocalDate.of(2026, 6, 14))),
            cards, tags, contexts, today,
        )

        assertTrue(presentation.contentDescription.contains("14 de junho"))
    }
}
