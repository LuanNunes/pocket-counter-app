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
    private val contexts = mapOf(ctxAlim.id to ctxAlim)

    private val tMercado = Tag("t-mercado", "Mercado", TransactionType.EXPENSE, idContext = "c1", color = 0xFF_11_22_33L)
    private val tSemCor = Tag("t-sem-cor", "Sem Cor", TransactionType.EXPENSE, idContext = "c1", color = null)
    private val tOrfao = Tag("t-orfao", "Órfão", TransactionType.EXPENSE, idContext = null, color = null)
    private val tags = mapOf(
        tMercado.id to tMercado,
        tSemCor.id to tSemCor,
        tOrfao.id to tOrfao,
    )

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
    private val cards = mapOf(cardNubank.id to cardNubank)

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

    @Test
    fun `ledgerMeta date is formatted via formatLedgerDate`() {
        val meta = ledgerMeta(
            date = today,
            tagIds = emptyList(),
            paymentMethod = null,
            cardId = null,
            cards = cards,
            tags = tags,
            contextsById = contexts,
            today = today,
        )

        assertEquals("Hoje", meta.date)
    }

    @Test
    fun `ledgerMeta tag color prefers the tag's own color`() {
        val meta = ledgerMeta(
            date = today,
            tagIds = listOf(tMercado.id),
            paymentMethod = null,
            cardId = null,
            cards = cards,
            tags = tags,
            contextsById = contexts,
            today = today,
        )

        assertEquals(tMercado.color, meta.tagColor)
    }

    @Test
    fun `ledgerMeta tag color falls back to the tag's context color when tag color is null`() {
        val meta = ledgerMeta(
            date = today,
            tagIds = listOf(tSemCor.id),
            paymentMethod = null,
            cardId = null,
            cards = cards,
            tags = tags,
            contextsById = contexts,
            today = today,
        )

        assertEquals(ctxAlim.color, meta.tagColor)
    }

    @Test
    fun `ledgerMeta tag color is null when both tag and context color are missing`() {
        val meta = ledgerMeta(
            date = today,
            tagIds = listOf(tOrfao.id),
            paymentMethod = null,
            cardId = null,
            cards = cards,
            tags = tags,
            contextsById = contexts,
            today = today,
        )

        assertNull(meta.tagColor)
    }

    @Test
    fun `ledgerMeta with a single tag reports the tag name and zero extra`() {
        val meta = ledgerMeta(
            date = today,
            tagIds = listOf(tMercado.id),
            paymentMethod = null,
            cardId = null,
            cards = cards,
            tags = tags,
            contextsById = contexts,
            today = today,
        )

        assertEquals(tMercado.name, meta.tagName)
        assertEquals(0, meta.extraTags)
    }

    @Test
    fun `ledgerMeta with three tags reports the first tag name and two extra`() {
        val meta = ledgerMeta(
            date = today,
            tagIds = listOf(tMercado.id, tSemCor.id, tOrfao.id),
            paymentMethod = null,
            cardId = null,
            cards = cards,
            tags = tags,
            contextsById = contexts,
            today = today,
        )

        assertEquals(tMercado.name, meta.tagName)
        assertEquals(2, meta.extraTags)
    }

    @Test
    fun `ledgerMeta with no tags reports null tag name and zero extra`() {
        val meta = ledgerMeta(
            date = today,
            tagIds = emptyList(),
            paymentMethod = null,
            cardId = null,
            cards = cards,
            tags = tags,
            contextsById = contexts,
            today = today,
        )

        assertNull(meta.tagName)
        assertEquals(0, meta.extraTags)
    }

    @Test
    fun `ledgerMeta payment for CREDIT with a known card is Credito plus the card name`() {
        val meta = ledgerMeta(
            date = today,
            tagIds = emptyList(),
            paymentMethod = PaymentMethod.CREDIT,
            cardId = cardNubank.id,
            cards = cards,
            tags = tags,
            contextsById = contexts,
            today = today,
        )

        assertEquals("Crédito Nubank", meta.payment)
    }

    @Test
    fun `ledgerMeta payment for CREDIT with an unknown card id is empty`() {
        val meta = ledgerMeta(
            date = today,
            tagIds = emptyList(),
            paymentMethod = PaymentMethod.CREDIT,
            cardId = "card-deleted",
            cards = cards,
            tags = tags,
            contextsById = contexts,
            today = today,
        )

        assertEquals("", meta.payment)
    }

    @Test
    fun `ledgerMeta payment for CREDIT with no card id is empty`() {
        val meta = ledgerMeta(
            date = today,
            tagIds = emptyList(),
            paymentMethod = PaymentMethod.CREDIT,
            cardId = null,
            cards = cards,
            tags = tags,
            contextsById = contexts,
            today = today,
        )

        assertEquals("", meta.payment)
    }

    @Test
    fun `ledgerMeta payment for a non-credit method is empty`() {
        val meta = ledgerMeta(
            date = today,
            tagIds = emptyList(),
            paymentMethod = PaymentMethod.PIX,
            cardId = null,
            cards = cards,
            tags = tags,
            contextsById = contexts,
            today = today,
        )

        assertEquals("", meta.payment)
    }

    @Test
    fun `ledgerMeta payment for a null payment method is empty`() {
        val meta = ledgerMeta(
            date = today,
            tagIds = emptyList(),
            paymentMethod = null,
            cardId = null,
            cards = cards,
            tags = tags,
            contextsById = contexts,
            today = today,
        )

        assertEquals("", meta.payment)
    }
}
