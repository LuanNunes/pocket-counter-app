package com.resolveprogramming.pocketcounter.ui.home.components

import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.ConfirmReadyItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.ui.components.LedgerLookups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ConfirmReadyPresentationTest {

    private val today = LocalDate.of(2026, 6, 20)

    private val tagMercado = Tag("t-mercado", "Mercado", TransactionType.EXPENSE, idContext = "c1")
    // No cards and no contexts: these tests never exercise card or context-colour resolution.
    private val lookups = LedgerLookups(
        cards = emptyMap(),
        tags = mapOf(tagMercado.id to tagMercado),
        contexts = emptyMap(),
    )

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

    private fun presentationOf(item: ConfirmReadyItem) = confirmReadyPresentation(item, lookups, today)

    // -- signedAmount --

    @Test
    fun `signedAmount is negative for an EXPENSE`() {
        val presentation = presentationOf(
            item(draft(type = TransactionType.EXPENSE, amount = BigDecimal("129.90"))),
        )

        assertEquals(BigDecimal("-129.90"), presentation.signedAmount)
    }

    @Test
    fun `signedAmount is positive for an INCOME`() {
        val presentation = presentationOf(
            item(draft(type = TransactionType.INCOME, amount = BigDecimal("50.00"))),
        )

        assertEquals(BigDecimal("50.00"), presentation.signedAmount)
    }

    @Test
    fun `signedAmount is null when the draft has no amount`() {
        val presentation = presentationOf(item(draft(amount = null)))

        assertNull(presentation.signedAmount)
    }

    // -- title --

    @Test
    fun `title prefers the draft name`() {
        val presentation = presentationOf(item(draft(name = "Mercado Central"), merchantRaw = "Other Store"))

        assertEquals("Mercado Central", presentation.title)
    }

    @Test
    fun `title falls back to the parsed merchant when draft name is null`() {
        val presentation = presentationOf(item(draft(name = null), merchantRaw = "Uber Trip"))

        assertEquals("Uber Trip", presentation.title)
    }

    @Test
    fun `title falls back to the parsed merchant when draft name is blank`() {
        val presentation = presentationOf(item(draft(name = "   "), merchantRaw = "Uber Trip"))

        assertEquals("Uber Trip", presentation.title)
    }

    @Test
    fun `title rejects a stored merchant with no letters and falls back to the first tag name`() {
        val presentation = presentationOf(
            item(draft(name = null, tagIds = listOf(tagMercado.id)), merchantRaw = "29"),
        )

        assertEquals(tagMercado.name, presentation.title)
    }

    @Test
    fun `title falls back to Lancamento when nothing usable is available`() {
        val presentation = presentationOf(item(draft(name = null, tagIds = emptyList()), merchantRaw = "29"))

        assertEquals("Lançamento", presentation.title)
    }

    // -- confirmLabel --

    @Test
    fun `confirmLabel is Confirmar when there is no pending transaction id`() {
        val presentation = presentationOf(item(pendingTransactionId = null))

        assertEquals("Confirmar", presentation.confirmLabel)
    }

    @Test
    fun `confirmLabel is Confirmar pagamento when a pending transaction id is present`() {
        val presentation = presentationOf(item(pendingTransactionId = "tx-99"))

        assertEquals("Confirmar pagamento", presentation.confirmLabel)
    }

    // -- installmentsLabel --

    @Test
    fun `installmentsLabel is 3x when draft installments is 3`() {
        val presentation = presentationOf(item(draft(installments = 3)))

        assertEquals("3×", presentation.installmentsLabel)
    }

    @Test
    fun `installmentsLabel is null when the draft has no installments`() {
        val presentation = presentationOf(item(draft(installments = null)))

        assertNull(presentation.installmentsLabel)
    }

    // -- contentDescription --

    @Test
    fun `contentDescription for an EXPENSE contains despesa`() {
        val presentation = presentationOf(item(draft(type = TransactionType.EXPENSE)))

        assertTrue(presentation.contentDescription.contains("despesa"))
    }

    @Test
    fun `contentDescription for an INCOME contains receita`() {
        val presentation = presentationOf(item(draft(type = TransactionType.INCOME)))

        assertTrue(presentation.contentDescription.contains("receita"))
    }

    @Test
    fun `contentDescription carries no minus sign of either codepoint`() {
        // Both codepoints: AmountText prints U+2212, NumberFormat the ASCII hyphen.
        val presentation = presentationOf(
            item(draft(type = TransactionType.EXPENSE, amount = BigDecimal("129.90"))),
        )

        assertFalse(presentation.contentDescription.contains('\u2212'))
        assertFalse(presentation.contentDescription.contains('-'))
        assertTrue(presentation.contentDescription.contains("despesa de"))
    }

    @Test
    fun `contentDescription carries no minus sign when the incoming amount is already negative`() {
        // isConfirmReady short-circuits to true for a pending-transaction match without calling
        // isStep2Valid, so the amount is not guaranteed unsigned on the path this card serves.
        val presentation = presentationOf(
            item(draft(type = TransactionType.EXPENSE, amount = BigDecimal("-129.90"))),
        )

        assertFalse(presentation.contentDescription.contains('-'))
    }

    @Test
    fun `signedAmount is negative for an EXPENSE whose incoming amount is already negative`() {
        // Negating a value that already carries its sign would flip it back to positive and render
        // "+ R$ 129,90" in expense red — the mirror of the bug signedAmount exists to fix.
        val presentation = presentationOf(
            item(draft(type = TransactionType.EXPENSE, amount = BigDecimal("-129.90"))),
        )

        assertEquals(BigDecimal("-129.90"), presentation.signedAmount)
    }

    @Test
    fun `installmentsLabel is null for a single installment`() {
        // The gate exists for exactly this value: "1×" is noise on a line that must not wrap.
        val presentation = presentationOf(item(draft(installments = 1)))

        assertNull(presentation.installmentsLabel)
        assertFalse(presentation.contentDescription.contains("vezes"))
    }

    @Test
    fun `contentDescription uses hoje for today's date`() {
        val presentation = presentationOf(item(draft(date = today)))

        assertTrue(presentation.contentDescription.contains("hoje"))
    }

    @Test
    fun `contentDescription uses ontem for yesterday's date`() {
        val presentation = presentationOf(item(draft(date = today.minusDays(1))))

        assertTrue(presentation.contentDescription.contains("ontem"))
    }

    @Test
    fun `contentDescription uses a spoken day-and-month for other dates`() {
        val presentation = presentationOf(item(draft(date = LocalDate.of(2026, 6, 14))))

        assertTrue(presentation.contentDescription.contains("14 de junho"))
    }
}
