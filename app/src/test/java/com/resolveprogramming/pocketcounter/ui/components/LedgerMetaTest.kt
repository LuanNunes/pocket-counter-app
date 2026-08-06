package com.resolveprogramming.pocketcounter.ui.components

import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class LedgerMetaTest {

    private val ctxAlim = TagContext("c1", "Alimentação", 0xFF_AA_00_00L)

    private val tMercado = Tag("t-mercado", "Mercado", TransactionType.EXPENSE, idContext = "c1", color = 0xFF_11_22_33L)
    private val tSemCor = Tag("t-sem-cor", "Sem Cor", TransactionType.EXPENSE, idContext = "c1", color = null)
    private val tOrfao = Tag("t-orfao", "Órfão", TransactionType.EXPENSE, idContext = null, color = null)

    private val cardNubank = CreditCard(
        id = "card-1",
        name = "Nubank",
        brand = "mastercard",
        last4 = "1234",
        gradientStart = 0xFF_82_0A_D1L,
        gradientEnd = 0xFF_69_0C_A3L,
        limit = BigDecimal("5000"),
        billDay = 10,
    )

    private val lookups = LedgerLookups(
        cards = mapOf(cardNubank.id to cardNubank),
        tags = mapOf(
            tMercado.id to tMercado,
            tSemCor.id to tSemCor,
            tOrfao.id to tOrfao,
        ),
        contexts = mapOf(ctxAlim.id to ctxAlim),
    )

    // -- formatLedgerDate --

    @Test
    fun `formatLedgerDate returns Hoje for today`() {
        val today = LocalDate.of(2026, 6, 20)

        assertEquals("Hoje", formatLedgerDate(today, today))
    }

    @Test
    fun `formatLedgerDate returns Ontem for yesterday`() {
        val today = LocalDate.of(2026, 6, 20)

        assertEquals("Ontem", formatLedgerDate(LocalDate.of(2026, 6, 19), today))
    }

    @Test
    fun `formatLedgerDate returns Ontem for yesterday across a month boundary`() {
        val today = LocalDate.of(2026, 7, 1)

        assertEquals("Ontem", formatLedgerDate(LocalDate.of(2026, 6, 30), today))
    }

    @Test
    fun `formatLedgerDate returns Ontem for yesterday across a year boundary`() {
        val today = LocalDate.of(2026, 1, 1)

        assertEquals("Ontem", formatLedgerDate(LocalDate.of(2025, 12, 31), today))
    }

    @Test
    fun `formatLedgerDate formats other dates as lowercase day and short pt-BR month with no trailing dot`() {
        val today = LocalDate.of(2026, 6, 20)

        assertEquals("14 jun", formatLedgerDate(LocalDate.of(2026, 6, 14), today))
    }

    @Test
    fun `formatLedgerDate formats a date in a different year without treating it as Ontem`() {
        val today = LocalDate.of(2026, 1, 15)

        assertEquals("31 dez", formatLedgerDate(LocalDate.of(2025, 12, 31), today))
    }

    // -- ledgerMeta --

    private val today = LocalDate.of(2026, 6, 20)

    private fun metaOf(
        tagIds: List<String> = emptyList(),
        paymentMethod: PaymentMethod? = null,
        cardId: String? = null,
        date: LocalDate = today,
    ) = ledgerMeta(
        row = LedgerRow(date = date, tagIds = tagIds, paymentMethod = paymentMethod, cardId = cardId),
        lookups = lookups,
        today = today,
    )

    @Test
    fun `ledgerMeta date is formatted via formatLedgerDate`() {
        val meta = metaOf()

        assertEquals("Hoje", meta.date)
    }

    @Test
    fun `ledgerMeta tag color prefers the tag's own color`() {
        val meta = metaOf(tagIds = listOf(tMercado.id))

        assertEquals(tMercado.color, meta.tagColor)
    }

    @Test
    fun `ledgerMeta tag color falls back to the tag's context color when tag color is null`() {
        val meta = metaOf(tagIds = listOf(tSemCor.id))

        assertEquals(ctxAlim.color, meta.tagColor)
    }

    @Test
    fun `ledgerMeta tag color is null when both tag and context color are missing`() {
        val meta = metaOf(tagIds = listOf(tOrfao.id))

        assertNull(meta.tagColor)
    }

    @Test
    fun `ledgerMeta with a single tag reports the tag name and zero extra`() {
        val meta = metaOf(tagIds = listOf(tMercado.id))

        assertEquals(tMercado.name, meta.tagName)
        assertEquals(0, meta.extraTags)
    }

    @Test
    fun `ledgerMeta with three tags reports the first tag name and two extra`() {
        val meta = metaOf(tagIds = listOf(tMercado.id, tSemCor.id, tOrfao.id))

        assertEquals(tMercado.name, meta.tagName)
        assertEquals(2, meta.extraTags)
    }

    @Test
    fun `ledgerMeta with no tags reports null tag name and zero extra`() {
        val meta = metaOf()

        assertNull(meta.tagName)
        assertEquals(0, meta.extraTags)
    }

    @Test
    fun `ledgerMeta payment for CREDIT with a known card is Credito plus the card name`() {
        val meta = metaOf(paymentMethod = PaymentMethod.CREDIT, cardId = cardNubank.id)

        assertEquals("Crédito Nubank", meta.payment)
    }

    @Test
    fun `ledgerMeta payment for CREDIT with an unknown card id keeps the method word`() {
        val meta = metaOf(paymentMethod = PaymentMethod.CREDIT, cardId = "card-deleted")

        // A deleted card must not silently erase the fact that this was a credit charge.
        assertEquals("Crédito", meta.payment)
    }

    @Test
    fun `ledgerMeta payment for CREDIT with no card id keeps the method word`() {
        val meta = metaOf(paymentMethod = PaymentMethod.CREDIT)

        assertEquals("Crédito", meta.payment)
    }

    @Test
    fun `ledgerMeta payment for a non-credit method is the method word alone`() {
        val meta = metaOf(paymentMethod = PaymentMethod.PIX)

        // Only the card name is CREDIT-specific; Pix/Débito/Dinheiro keep their label, as before
        // this helper was shared out of TransacoesScreen.
        assertEquals("Pix", meta.payment)
    }

    @Test
    fun `ledgerMeta payment for a null payment method is empty`() {
        val meta = metaOf()

        assertEquals("", meta.payment)
    }
}
