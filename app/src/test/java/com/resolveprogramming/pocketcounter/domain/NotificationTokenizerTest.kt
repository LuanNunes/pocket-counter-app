package com.resolveprogramming.pocketcounter.domain

import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.TokenRole
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.notification.NotificationTokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class NotificationTokenizerTest {

    // -------------------------------------------------------------------------
    // parseBrAmount
    // -------------------------------------------------------------------------

    @Test
    fun `parseBrAmount full BR format with thousands separator`() {
        val result = NotificationTokenizer.parseBrAmount("R$ 1.234,56")

        assertEquals(BigDecimal("1234.56"), result)
    }

    @Test
    fun `parseBrAmount decimal-only value without thousands separator`() {
        val result = NotificationTokenizer.parseBrAmount("49,90")

        assertEquals(BigDecimal("49.90"), result)
    }

    @Test
    fun `parseBrAmount plain integer string`() {
        val result = NotificationTokenizer.parseBrAmount("250")

        assertEquals(BigDecimal("250"), result)
    }

    @Test
    fun `parseBrAmount junk text returns null`() {
        val result = NotificationTokenizer.parseBrAmount("abc")

        assertNull(result)
    }

    @Test
    fun `parseBrAmount empty string returns null`() {
        val result = NotificationTokenizer.parseBrAmount("")

        assertNull(result)
    }

    @Test
    fun `parseBrAmount stray currency symbol alone returns null`() {
        val result = NotificationTokenizer.parseBrAmount("R$")

        assertNull(result)
    }

    @Test
    fun `parseBrAmount value with only whitespace returns null`() {
        val result = NotificationTokenizer.parseBrAmount("   ")

        assertNull(result)
    }

    @Test
    fun `parseBrAmount value with leading and trailing whitespace`() {
        val result = NotificationTokenizer.parseBrAmount("  99,00  ")

        assertEquals(BigDecimal("99.00"), result)
    }

    @Test
    fun `parseBrAmount currency symbol with surrounding whitespace`() {
        val result = NotificationTokenizer.parseBrAmount("R$ 10,00")

        assertEquals(BigDecimal("10.00"), result)
    }

    @Test
    fun `parseBrAmount RS prefix with decimal`() {
        val result = NotificationTokenizer.parseBrAmount("RS 30,96")

        assertEquals(0, BigDecimal("30.96").compareTo(result))
    }

    @Test
    fun `parseBrAmount BRL prefix with decimal`() {
        val result = NotificationTokenizer.parseBrAmount("BRL 123,45")

        assertEquals(0, BigDecimal("123.45").compareTo(result))
    }

    // -------------------------------------------------------------------------
    // tokenize — basic splitting
    // -------------------------------------------------------------------------

    @Test
    fun `tokenize splits text into one token per whitespace-delimited word`() {
        val parsed = noParsed()

        val tokens = NotificationTokenizer.tokenize("Compra aprovada hoje", parsed)

        assertEquals(listOf("Compra", "aprovada", "hoje"), tokens.map { it.text })
    }

    @Test
    fun `tokenize on empty string returns empty list`() {
        val parsed = noParsed()

        val tokens = NotificationTokenizer.tokenize("", parsed)

        assertEquals(emptyList<Any>(), tokens)
    }

    @Test
    fun `tokenize on text with only whitespace returns empty list`() {
        val parsed = noParsed()

        val tokens = NotificationTokenizer.tokenize("   \t  ", parsed)

        assertEquals(emptyList<Any>(), tokens)
    }

    @Test
    fun `tokenize preserves original token text`() {
        val parsed = noParsed()

        val tokens = NotificationTokenizer.tokenize("IFD*IFOOD R$ 49,90", parsed)

        assertEquals("IFD*IFOOD", tokens[0].text)
        assertEquals("R$", tokens[1].text)
        assertEquals("49,90", tokens[2].text)
    }

    // -------------------------------------------------------------------------
    // tokenize — AMOUNT role assignment
    // -------------------------------------------------------------------------

    @Test
    fun `tokenize assigns AMOUNT role to token matching parsed amount`() {
        val parsed = parsedWithAmount(BigDecimal("49.90"))

        val tokens = NotificationTokenizer.tokenize("Compra 49,90 aprovada", parsed)

        val amountToken = tokens.find { it.role == TokenRole.AMOUNT }
        assertEquals("49,90", amountToken?.text)
    }

    @Test
    fun `tokenize stores plain string value on AMOUNT token`() {
        val parsed = parsedWithAmount(BigDecimal("100.00"))

        val tokens = NotificationTokenizer.tokenize("Valor 100,00 debitado", parsed)

        val amountToken = tokens.find { it.role == TokenRole.AMOUNT }
        assertEquals("100.00", amountToken?.value)
    }

    @Test
    fun `tokenize assigns AMOUNT only once when amount appears twice in text`() {
        // "49,90" appears at positions 0 and 2 — only the first unassigned match gets AMOUNT
        val parsed = parsedWithAmount(BigDecimal("49.90"))

        val tokens = NotificationTokenizer.tokenize("49,90 IFD 49,90", parsed)

        val amountTokens = tokens.filter { it.role == TokenRole.AMOUNT }
        assertEquals(1, amountTokens.size)
    }

    @Test
    fun `tokenize does not assign AMOUNT when parsed amount is null`() {
        val parsed = noParsed()

        val tokens = NotificationTokenizer.tokenize("Compra 99,00 aprovada", parsed)

        val amountTokens = tokens.filter { it.role == TokenRole.AMOUNT }
        assertEquals(0, amountTokens.size)
    }

    @Test
    fun `tokenize does not assign AMOUNT when no token matches parsed amount`() {
        val parsed = parsedWithAmount(BigDecimal("999.00"))

        val tokens = NotificationTokenizer.tokenize("Compra 49,90 aprovada", parsed)

        val amountTokens = tokens.filter { it.role == TokenRole.AMOUNT }
        assertEquals(0, amountTokens.size)
    }

    // -------------------------------------------------------------------------
    // tokenize — MERCHANT role assignment
    // -------------------------------------------------------------------------

    @Test
    fun `tokenize assigns MERCHANT role to tokens matching single-word merchantRaw`() {
        val parsed = parsedWithMerchant("IFOOD")

        val tokens = NotificationTokenizer.tokenize("Compra IFOOD aprovada", parsed)

        val merchantToken = tokens.find { it.role == TokenRole.MERCHANT }
        assertEquals("IFOOD", merchantToken?.text)
    }

    @Test
    fun `tokenize assigns MERCHANT across a contiguous multi-word run`() {
        val parsed = parsedWithMerchant("IFD*A M GUILHERME CORR")

        val tokens = NotificationTokenizer.tokenize(
            "Compra IFD*A M GUILHERME CORR aprovada",
            parsed,
        )

        val merchantTokens = tokens.filter { it.role == TokenRole.MERCHANT }
        assertEquals(listOf("IFD*A", "M", "GUILHERME", "CORR"), merchantTokens.map { it.text })
    }

    @Test
    fun `tokenize stores merchantRaw as value on all merchant tokens`() {
        val merchant = "PADARIA PAO"
        val parsed = parsedWithMerchant(merchant)

        val tokens = NotificationTokenizer.tokenize("compra PADARIA PAO realizada", parsed)

        tokens.filter { it.role == TokenRole.MERCHANT }.forEach { token ->
            assertEquals(merchant, token.value)
        }
    }

    @Test
    fun `tokenize is case-insensitive when matching merchantRaw`() {
        val parsed = parsedWithMerchant("ifood")

        val tokens = NotificationTokenizer.tokenize("Compra IFOOD hoje", parsed)

        val merchantToken = tokens.find { it.role == TokenRole.MERCHANT }
        assertEquals("IFOOD", merchantToken?.text)
    }

    @Test
    fun `tokenize does not assign MERCHANT when merchantRaw is null`() {
        val parsed = noParsed()

        val tokens = NotificationTokenizer.tokenize("Compra IFOOD aprovada", parsed)

        val merchantTokens = tokens.filter { it.role == TokenRole.MERCHANT }
        assertEquals(0, merchantTokens.size)
    }

    @Test
    fun `tokenize does not assign MERCHANT when merchantRaw is blank`() {
        val parsed = parsedWithMerchant("   ")

        val tokens = NotificationTokenizer.tokenize("Compra IFOOD aprovada", parsed)

        val merchantTokens = tokens.filter { it.role == TokenRole.MERCHANT }
        assertEquals(0, merchantTokens.size)
    }

    @Test
    fun `tokenize does not assign MERCHANT when no contiguous run matches merchantRaw`() {
        val parsed = parsedWithMerchant("BURGER KING")

        // The words appear, but not as a contiguous run (IFOOD is between them)
        val tokens = NotificationTokenizer.tokenize("Compra BURGER IFOOD KING aprovada", parsed)

        val merchantTokens = tokens.filter { it.role == TokenRole.MERCHANT }
        assertEquals(0, merchantTokens.size)
    }

    // -------------------------------------------------------------------------
    // tokenize — mutual exclusivity (AMOUNT vs MERCHANT cannot share a token)
    // -------------------------------------------------------------------------

    @Test
    fun `tokenize AMOUNT token is not also assigned MERCHANT even when merchantRaw matches it`() {
        // "49,90" matches both amount AND merchantRaw — AMOUNT wins (assigned first),
        // so the merchant run is blocked because that token is already assigned.
        val parsed = ParsedNotification(
            type = null,
            amount = BigDecimal("49.90"),
            date = null,
            merchantRaw = "49,90",
            paymentHint = null,
        )

        val tokens = NotificationTokenizer.tokenize("Compra 49,90 realizada", parsed)

        val amountTokens = tokens.filter { it.role == TokenRole.AMOUNT }
        val merchantTokens = tokens.filter { it.role == TokenRole.MERCHANT }
        assertEquals(1, amountTokens.size)
        assertEquals(0, merchantTokens.size)
    }

    @Test
    fun `tokenize unmatched tokens carry null role`() {
        val parsed = parsedWithAmount(BigDecimal("50.00"))

        val tokens = NotificationTokenizer.tokenize("Compra 50,00 aprovada", parsed)

        val unassigned = tokens.filter { it.role == null }
        assertEquals(listOf("Compra", "aprovada"), unassigned.map { it.text })
    }

    @Test
    fun `tokenize preserves token order from original text`() {
        val parsed = parsedWithAmount(BigDecimal("120.00"))

        val tokens = NotificationTokenizer.tokenize("Debito 120,00 cartao", parsed)

        assertEquals("Debito", tokens[0].text)
        assertEquals("120,00", tokens[1].text)
        assertEquals("cartao", tokens[2].text)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun noParsed() = ParsedNotification(
        type = null,
        amount = null,
        date = null,
        merchantRaw = null,
        paymentHint = null,
    )

    private fun parsedWithAmount(amount: BigDecimal) = ParsedNotification(
        type = TransactionType.EXPENSE,
        amount = amount,
        date = LocalDate.of(2026, 6, 12),
        merchantRaw = null,
        paymentHint = null,
    )

    private fun parsedWithMerchant(merchant: String) = ParsedNotification(
        type = TransactionType.EXPENSE,
        amount = null,
        date = LocalDate.of(2026, 6, 12),
        merchantRaw = merchant,
        paymentHint = null,
    )
}
