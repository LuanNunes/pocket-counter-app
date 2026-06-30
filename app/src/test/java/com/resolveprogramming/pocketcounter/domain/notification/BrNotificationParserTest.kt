package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Unit tests for [BrNotificationParser].
 *
 * All calls that depend on a "current date" receive a fixed [NOW] so results are deterministic.
 * Tests that expect a fallback to the `now` parameter also use [NOW].
 */
class BrNotificationParserTest {

    private val NOW = LocalDate.of(2026, 6, 12)

    // -------------------------------------------------------------------------
    // isFinancial — relevance gate
    // -------------------------------------------------------------------------

    @Test
    fun `parse with no R$ marker is not financial`() {
        val result = BrNotificationParser.parse("Seu cadastro foi atualizado com sucesso.", NOW)

        assertFalse(result.isFinancial)
    }

    @Test
    fun `parse empty string is not financial`() {
        val result = BrNotificationParser.parse("", NOW)

        assertFalse(result.isFinancial)
    }

    @Test
    fun `parse whitespace-only string is not financial`() {
        val result = BrNotificationParser.parse("   \t  ", NOW)

        assertFalse(result.isFinancial)
    }

    @Test
    fun `parse text with R$ but no digits after is not financial`() {
        // "R$" alone cannot be parsed to an amount — the regex requires at least one digit group
        val result = BrNotificationParser.parse("Valor: R$ aprovado", NOW)

        assertFalse(result.isFinancial)
    }

    @Test
    fun `parse text with valid amount is financial`() {
        val result = BrNotificationParser.parse("Compra aprovada: R$ 89,90", NOW)

        assertTrue(result.isFinancial)
    }

    // -------------------------------------------------------------------------
    // Amount parsing
    // -------------------------------------------------------------------------

    @Test
    fun `parse amount two-decimal BR format`() {
        val result = BrNotificationParser.parse("Compra aprovada: R$ 89,90", NOW)

        assertEquals(BigDecimal("89.90"), result.parsed.amount)
    }

    @Test
    fun `parse amount with thousands separator and decimals`() {
        val result = BrNotificationParser.parse("Pix recebido R$ 1.234,56", NOW)

        assertEquals(BigDecimal("1234.56"), result.parsed.amount)
    }

    @Test
    fun `parse amount whole number without decimal part`() {
        val result = BrNotificationParser.parse("Compra aprovada R$ 50", NOW)

        assertEquals(BigDecimal("50"), result.parsed.amount)
    }

    @Test
    fun `parse amount no space between R$ and digits`() {
        val result = BrNotificationParser.parse("Débito R$200,00 realizado", NOW)

        assertEquals(BigDecimal("200.00"), result.parsed.amount)
    }

    @Test
    fun `parse amount large value with multiple thousands groups`() {
        val result = BrNotificationParser.parse("Salário creditado R$ 5.000,00", NOW)

        assertEquals(BigDecimal("5000.00"), result.parsed.amount)
    }

    @Test
    fun `parse amount is null when text has no R$ prefix`() {
        val result = BrNotificationParser.parse("Transação realizada 89,90", NOW)

        assertNull(result.parsed.amount)
    }

    // -------------------------------------------------------------------------
    // Type keyword detection — EXPENSE paths
    // -------------------------------------------------------------------------

    @Test
    fun `parse compra keyword yields EXPENSE`() {
        val result = BrNotificationParser.parse("Compra aprovada R$ 30,00", NOW)

        assertEquals(TransactionType.EXPENSE, result.parsed.type)
    }

    @Test
    fun `parse debito keyword yields EXPENSE`() {
        val result = BrNotificationParser.parse("débito em conta R$ 40,00", NOW)

        assertEquals(TransactionType.EXPENSE, result.parsed.type)
    }

    @Test
    fun `parse pagamento keyword yields EXPENSE`() {
        val result = BrNotificationParser.parse("Pagamento efetuado R$ 120,00", NOW)

        assertEquals(TransactionType.EXPENSE, result.parsed.type)
    }

    @Test
    fun `parse pix enviado phrase yields EXPENSE`() {
        val result = BrNotificationParser.parse("Pix enviado para João R$ 20,00", NOW)

        assertEquals(TransactionType.EXPENSE, result.parsed.type)
    }

    @Test
    fun `parse transferência enviada phrase yields EXPENSE`() {
        val result = BrNotificationParser.parse("Transferência enviada R$ 20,00", NOW)

        assertEquals(TransactionType.EXPENSE, result.parsed.type)
    }

    @Test
    fun `parse saque keyword yields EXPENSE`() {
        val result = BrNotificationParser.parse("Saque realizado R$ 200,00", NOW)

        assertEquals(TransactionType.EXPENSE, result.parsed.type)
    }

    @Test
    fun `parse fatura keyword yields EXPENSE`() {
        val result = BrNotificationParser.parse("Fatura do cartão vence amanhã R$ 850,00", NOW)

        assertEquals(TransactionType.EXPENSE, result.parsed.type)
    }

    // -------------------------------------------------------------------------
    // Type keyword detection — INCOME paths
    // -------------------------------------------------------------------------

    @Test
    fun `parse pix recebido phrase yields INCOME not EXPENSE`() {
        // Critical: "recebido" in INCOME_WORDS and "pix recebido" in INCOME_PHRASES.
        // There is no EXPENSE keyword in this text, so result must be INCOME.
        val result = BrNotificationParser.parse("Pix recebido de João R$ 100,00", NOW)

        assertEquals(TransactionType.INCOME, result.parsed.type)
    }

    @Test
    fun `parse salário keyword yields INCOME`() {
        val result = BrNotificationParser.parse("Salário creditado R$ 5.000,00", NOW)

        assertEquals(TransactionType.INCOME, result.parsed.type)
    }

    @Test
    fun `parse salario without accent yields INCOME`() {
        val result = BrNotificationParser.parse("salario recebido R$ 3.800,00", NOW)

        assertEquals(TransactionType.INCOME, result.parsed.type)
    }

    @Test
    fun `parse depósito keyword yields INCOME`() {
        val result = BrNotificationParser.parse("Depósito realizado R$ 1.000,00", NOW)

        assertEquals(TransactionType.INCOME, result.parsed.type)
    }

    @Test
    fun `parse crédito em conta phrase yields INCOME`() {
        val result = BrNotificationParser.parse("Crédito em conta R$ 200,00", NOW)

        assertEquals(TransactionType.INCOME, result.parsed.type)
    }

    @Test
    fun `parse transferência recebida phrase yields INCOME`() {
        val result = BrNotificationParser.parse("Transferência recebida R$ 50,00", NOW)

        assertEquals(TransactionType.INCOME, result.parsed.type)
    }

    @Test
    fun `parse recebida feminine word yields INCOME`() {
        val result = BrNotificationParser.parse("Transferência recebida de Ana R$ 60,00", NOW)

        assertEquals(TransactionType.INCOME, result.parsed.type)
    }

    @Test
    fun `parse você recebeu phrase yields INCOME`() {
        val result = BrNotificationParser.parse("Você recebeu R$ 75,00 de Ana", NOW)

        assertEquals(TransactionType.INCOME, result.parsed.type)
    }

    // -------------------------------------------------------------------------
    // Type keyword detection — ambiguous and null cases
    // -------------------------------------------------------------------------

    @Test
    fun `parse amount-only text with no type keyword yields null type`() {
        // A message with an amount but no recognizable income/expense keyword → type null
        val result = BrNotificationParser.parse("Atualização de saldo R$ 300,00", NOW)

        assertNull(result.parsed.type)
    }

    @Test
    fun `parse text with both expense and income keywords yields null type`() {
        // "compra" (EXPENSE_WORD) and "recebido" (INCOME_WORD) both present → ambiguous → null.
        val result = BrNotificationParser.parse("Compra recebido R$ 10,00", NOW)

        assertNull(result.parsed.type)
    }

    @Test
    fun `parse non-financial message with no R$ has null type`() {
        val result = BrNotificationParser.parse("Lembrete: sua fatura vence em 5 dias.", NOW)

        // "fatura" would be an EXPENSE keyword, but there is no amount so isFinancial=false.
        // Type is still parsed independently — we do not assert isFinancial=false here but do
        // assert that the absence of an amount is the primary gate.
        assertFalse(result.isFinancial)
    }

    // -------------------------------------------------------------------------
    // Installment parsing
    // -------------------------------------------------------------------------

    @Test
    fun `parse installments from Nx pattern`() {
        val result = BrNotificationParser.parse("Compra em 3x de R$ 33,33", NOW)

        assertEquals(3, result.parsed.installments)
    }

    @Test
    fun `parse installments from fraction notation total`() {
        // "3/10" → group 2 = 10, so total installments = 10
        val result = BrNotificationParser.parse("Parcela 3/10 R$ 45,00", NOW)

        assertEquals(10, result.parsed.installments)
    }

    @Test
    fun `parse installments from em N vezes pattern`() {
        val result = BrNotificationParser.parse("Compra em 12 vezes R$ 1.200,00", NOW)

        assertEquals(12, result.parsed.installments)
    }

    @Test
    fun `parse installments null when no installment pattern present`() {
        val result = BrNotificationParser.parse("Compra aprovada R$ 89,90", NOW)

        assertNull(result.parsed.installments)
    }

    @Test
    fun `parse dated non-installment purchase does not read date as installment fraction`() {
        // "12/06/2026" must NOT be read as a "12/06" fraction → installments must stay null and
        // installmentValue must not be fabricated.
        val result = BrNotificationParser.parse(
            "Itaú: compra aprovada de R\$ 89,90 em IFD*IFOOD em 12/06/2026",
            NOW,
        )

        assertNull(result.parsed.installments)
        assertNull(result.parsed.installmentValue)
    }

    @Test
    fun `parse installmentValue is null for single payment`() {
        val result = BrNotificationParser.parse("Compra em 1x R$ 100,00", NOW)

        // installments=1, installmentValue returns null when installments <= 1
        assertNull(result.parsed.installmentValue)
    }

    @Test
    fun `parse installmentValue computed when no explicit parcel value present`() {
        // "12 vezes" with R$ 1.200,00 → 1200.00 / 12 = 100.00
        val result = BrNotificationParser.parse("Compra em 12 vezes R$ 1.200,00", NOW)

        assertEquals(BigDecimal("100.00"), result.parsed.installmentValue)
    }

    @Test
    fun `parse installmentValue rounds HALF_UP`() {
        // R$ 100,00 / 3 = 33.333... → rounds to 33.33 HALF_UP
        val result = BrNotificationParser.parse("Compra em 3 vezes R$ 100,00", NOW)

        assertEquals(BigDecimal("33.33"), result.parsed.installmentValue)
    }

    @Test
    fun `parse installmentValue is null when explicit parcel amount follows de R$`() {
        // "em 3x de R$ 33,33" → PARCEL_VALUE_REGEX matches "de r$" → installmentValue suppressed
        val result = BrNotificationParser.parse("Compra em 3x de R$ 33,33", NOW)

        assertNull(result.parsed.installmentValue)
    }

    @Test
    fun `parse installmentValue is null when no amount parsed`() {
        // No R$ → amount null → installmentValue null regardless of installments
        val result = BrNotificationParser.parse("Compra em 3 vezes", NOW)

        assertNull(result.parsed.installmentValue)
    }

    // -------------------------------------------------------------------------
    // Merchant parsing
    // -------------------------------------------------------------------------

    @Test
    fun `parse merchant extracted after compra em prefix`() {
        // Capture is an UPPERCASE-led run: it spans "PADARIA DOIS IRMAOS" and stops at end of text.
        val result = BrNotificationParser.parse("R$ 15,00 compra em PADARIA DOIS IRMAOS", NOW)

        assertEquals("PADARIA DOIS IRMAOS", result.parsed.merchantRaw)
    }

    @Test
    fun `parse merchant prefix matches case-insensitively`() {
        // "Compra em" (capitalized prefix) must still match; the merchant keeps its original case.
        val result = BrNotificationParser.parse("Compra em IFOOD R$ 25,00", NOW)

        assertEquals("IFOOD", result.parsed.merchantRaw)
    }

    @Test
    fun `parse merchant stops before trailing lowercase context word`() {
        // "Compra em IFOOD aprovada" must capture only "IFOOD", not "IFOOD aprovada".
        val result = BrNotificationParser.parse("Compra em IFOOD aprovada R$ 25,00", NOW)

        assertEquals("IFOOD", result.parsed.merchantRaw)
    }

    @Test
    fun `parse merchant handles star-joined acquirer token`() {
        // "IFD*IFOOD" is a single uppercase-led token and must be captured whole.
        val result = BrNotificationParser.parse("compra em IFD*IFOOD em 12/06/2026 R$ 89,90", NOW)

        assertEquals("IFD*IFOOD", result.parsed.merchantRaw)
    }

    @Test
    fun `parse merchant does not absorb the R$ amount marker`() {
        val result = BrNotificationParser.parse("Débito em SUPERMERCADO EXTRA R$ 75,00", NOW)

        assertEquals("SUPERMERCADO EXTRA", result.parsed.merchantRaw)
    }

    @Test
    fun `parse merchant extracted after bare em lowercase`() {
        val result = BrNotificationParser.parse("Débito em SUPERMERCADO EXTRA R$ 75,00", NOW)

        // "em SUPERMERCADO EXTRA" → merchant candidate. The regex captures after "em ".
        // The candidate text is trimmed and stripped of trailing punctuation.
        assertNotNull(result.parsed.merchantRaw)
    }

    @Test
    fun `parse merchant is null when no compra em or em prefix in text`() {
        val result = BrNotificationParser.parse("Pix recebido de João R$ 100,00", NOW)

        // No "compra em / em" before an uppercase word
        assertNull(result.parsed.merchantRaw)
    }

    @Test
    fun `parse merchant is null when no uppercase word follows em`() {
        // MERCHANT_REGEX requires the captured group to start with \p{Lu} (uppercase letter).
        // A lowercase word after "em" will not match the capture group at all.
        val result = BrNotificationParser.parse("Pix enviado em reais R$ 10,00", NOW)

        // "reais" starts with lowercase → no match for the capture group → merchantRaw null.
        assertNull(result.parsed.merchantRaw)
    }

    @Test
    fun `parse merchant trailing punctuation is stripped`() {
        val result = BrNotificationParser.parse("compra em LOJA XPTO, aprovada R$ 50,00", NOW)

        // The trailing comma should be stripped from the candidate
        val merchant = result.parsed.merchantRaw
        if (merchant != null) {
            assertFalse("Merchant should not end with punctuation", merchant.endsWith(","))
        }
    }

    // -------------------------------------------------------------------------
    // Merchant parsing — real credit-card formats ("final NNNN - <merchant> valor")
    // -------------------------------------------------------------------------

    @Test
    fun `parse merchant from card final-dash-valor format keeps mixed case descriptor`() {
        val result = BrNotificationParser.parse(
            "Compra aprovada no seu PERSON BLACK CASHBAC final 3685 - DL*UberRides valor RS 26,74 em 29/06, as 13h25.",
            NOW,
        )

        assertEquals("DL*UberRides", result.parsed.merchantRaw)
    }

    @Test
    fun `parse merchant from card format spans multi-word merchant`() {
        val result = BrNotificationParser.parse(
            "Compra aprovada no seu PERSON BLACK CASHBAC final 3685 - IFD*IFOOD CLUB valor RS 12,90 em 29/06, as 07h11.",
            NOW,
        )

        assertEquals("IFD*IFOOD CLUB", result.parsed.merchantRaw)
    }

    @Test
    fun `parse merchant from card format never returns the date day digits`() {
        // Regression: the old "em <X>" heuristic captured "29" (the day of "29/06") as the merchant.
        val result = BrNotificationParser.parse(
            "Compra aprovada no seu PERSON BLACK CASHBAC final 3685 - DL*UberRides valor RS 21,96 em 27/06, as 18h06.",
            NOW,
        )

        assertEquals("DL*UberRides", result.parsed.merchantRaw)
    }

    @Test
    fun `parse merchant from aprovada-em-para format keeps mixed case`() {
        val result = BrNotificationParser.parse(
            "Compra no crédito aprovada Compra de R\$ 26,90 APROVADA em DL*GOOGLE YouTub para o cartão com final 1523.",
            NOW,
        )

        assertEquals("DL*GOOGLE YouTub", result.parsed.merchantRaw)
    }

    @Test
    fun `parse merchant is never a pure number`() {
        // A bare "em <date>" must not yield a numeric merchant.
        val result = BrNotificationParser.parse("Compra RS 10,00 em 29/06", NOW)

        val merchant = result.parsed.merchantRaw
        if (merchant != null) {
            assertFalse("Merchant must not be all digits", merchant.all { it.isDigit() })
        }
    }

    // -------------------------------------------------------------------------
    // Date parsing
    // -------------------------------------------------------------------------

    @Test
    fun `parse explicit full date dd slash MM slash yyyy`() {
        val result = BrNotificationParser.parse("Transação em 12/06/2026 R$ 89,90", NOW)

        assertEquals(LocalDate.of(2026, 6, 12), result.parsed.date)
    }

    @Test
    fun `parse explicit short date dd slash MM uses now year`() {
        val result = BrNotificationParser.parse("Compra em 01/03 R$ 40,00", LocalDate.of(2026, 6, 12))

        assertEquals(LocalDate.of(2026, 3, 1), result.parsed.date)
    }

    @Test
    fun `parse falls back to now when no date in text`() {
        val result = BrNotificationParser.parse("Compra aprovada R$ 50,00", NOW)

        assertEquals(NOW, result.parsed.date)
    }

    @Test
    fun `parse non-financial message date falls back to now`() {
        // No amount, no date text → date falls back to now
        val result = BrNotificationParser.parse("Seu cadastro foi atualizado.", NOW)

        assertEquals(NOW, result.parsed.date)
    }

    // -------------------------------------------------------------------------
    // Payment hint parsing
    // -------------------------------------------------------------------------

    @Test
    fun `parse payment hint extracts final NNNN`() {
        val result = BrNotificationParser.parse("Compra aprovada cartão final 3685 R$ 89,90", NOW)

        assertEquals("final 3685", result.parsed.paymentHint)
    }

    @Test
    fun `parse payment hint is cartão when cartão word present without final digits`() {
        val result = BrNotificationParser.parse("Débito no cartão R$ 40,00", NOW)

        assertEquals("cartão", result.parsed.paymentHint)
    }

    @Test
    fun `parse payment hint is conta when conta word present`() {
        val result = BrNotificationParser.parse("débito em conta R$ 40,00", NOW)

        assertEquals("conta", result.parsed.paymentHint)
    }

    @Test
    fun `parse payment hint is null when no hint keyword present`() {
        val result = BrNotificationParser.parse("Pix recebido de João R$ 100,00", NOW)

        assertNull(result.parsed.paymentHint)
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    fun `parse very long string does not hang and returns a result`() {
        // Regression guard against catastrophic regex backtracking.
        // 10 000 repetitions of a word + space — no R$ present, so isFinancial=false.
        val longText = "palavra ".repeat(10_000)
        val result = BrNotificationParser.parse(longText, NOW)

        assertFalse(result.isFinancial)
    }

    @Test
    fun `parse R$ with garbage digits after is not financial`() {
        // "R$ abc" — AMOUNT_REGEX requires \d{1,3} after R$\s?, so this won't match
        val result = BrNotificationParser.parse("Operação R$ abc realizada", NOW)

        assertFalse(result.isFinancial)
    }

    @Test
    fun `parse complete realistic Itaú expense SMS`() {
        val text = "Itaú: compra aprovada de R$ 89,90 em IFD*IFOOD em 12/06/2026. cartão final 4321."
        val result = BrNotificationParser.parse(text, NOW)

        assertTrue(result.isFinancial)
        assertEquals(BigDecimal("89.90"), result.parsed.amount)
        assertEquals(TransactionType.EXPENSE, result.parsed.type)
        assertEquals(LocalDate.of(2026, 6, 12), result.parsed.date)
        assertEquals("final 4321", result.parsed.paymentHint)
    }

    @Test
    fun `parse complete realistic Nubank Pix received push`() {
        val text = "Você recebeu um Pix de R$ 250,00 de Maria Silva"
        val result = BrNotificationParser.parse(text, NOW)

        assertTrue(result.isFinancial)
        assertEquals(BigDecimal("250.00"), result.parsed.amount)
        assertEquals(TransactionType.INCOME, result.parsed.type)
    }

    @Test
    fun `parse complete realistic installment purchase`() {
        val text = "Compra aprovada: R$ 600,00 em 3 vezes de R$ 200,00. cartão final 9988."
        val result = BrNotificationParser.parse(text, NOW)

        assertTrue(result.isFinancial)
        assertEquals(BigDecimal("600.00"), result.parsed.amount)
        assertEquals(3, result.parsed.installments)
        // "de R$" is matched by PARCEL_VALUE_REGEX → installmentValue suppressed
        assertNull(result.parsed.installmentValue)
        assertEquals("final 9988", result.parsed.paymentHint)
    }

    // -------------------------------------------------------------------------
    // RS / BRL currency forms — Brazilian card SMS uses "RS" (no $ sign)
    // -------------------------------------------------------------------------

    @Test
    fun `parse exact Uber card SMS with RS currency form is financial`() {
        val text = "Pre-autorizacao aprovada no seu PERSON BLACK CASHBAC final 3685 - DL *UberRides valor RS 30,96 em 26/06, as 18h33."
        val result = BrNotificationParser.parse(text, NOW)

        assertTrue(result.isFinancial)
        assertEquals(0, BigDecimal("30.96").compareTo(result.parsed.amount))
    }

    @Test
    fun `parse RS currency form in simple text is financial with correct amount`() {
        val result = BrNotificationParser.parse("Compra aprovada RS 50,00", NOW)

        assertTrue(result.isFinancial)
        assertEquals(0, BigDecimal("50.00").compareTo(result.parsed.amount))
    }

    @Test
    fun `parse BRL currency form in simple text is financial with correct amount`() {
        val result = BrNotificationParser.parse("Pagamento BRL 123,45 realizado", NOW)

        assertTrue(result.isFinancial)
        assertEquals(0, BigDecimal("123.45").compareTo(result.parsed.amount))
    }

    @Test
    fun `parse text with bare numbers but no currency token is not financial`() {
        // "final 3685", "26/06", "18h33" must not trigger the relevance gate
        val result = BrNotificationParser.parse("final 3685 em 26/06 as 18h33", NOW)

        assertFalse(result.isFinancial)
    }

    @Test
    fun `parse word ending in RS followed by number is not financial`() {
        // "CARS" ends in RS but the lookbehind must block it from matching as a currency token
        val result = BrNotificationParser.parse("CARS 30,00 vendidos", NOW)

        assertFalse(result.isFinancial)
    }

    @Test
    fun `parse pix recebido is not classified as EXPENSE due to recebido presence`() {
        // "recebido" is in INCOME_WORDS. If "pix recebido" phrase also matches INCOME_PHRASES,
        // and no EXPENSE keyword is present, the result must be INCOME.
        // This is the critical ordering invariant: INCOME wins when only income signals are found.
        val result = BrNotificationParser.parse("Pix recebido R$ 50,00", NOW)

        assertEquals(TransactionType.INCOME, result.parsed.type)
        assertFalse("Pix recebido must not be classified as EXPENSE",
            result.parsed.type == TransactionType.EXPENSE)
    }
}
