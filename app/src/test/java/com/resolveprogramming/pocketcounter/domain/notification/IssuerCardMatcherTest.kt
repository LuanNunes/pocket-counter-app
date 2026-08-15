package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * Unit tests for [IssuerCardMatcher].
 *
 * Ambiguity always resolves to null — mirrors [CardLast4Matcher.matchCardId]: two or more
 * matches means the caller falls through to a manual picker rather than guessing.
 */
class IssuerCardMatcherTest {

    private fun card(id: String, name: String) = CreditCard(
        id = id,
        name = name,
        brand = "Mastercard",
        last4 = "0000",
        gradientStart = 0L,
        gradientEnd = 0L,
        limit = BigDecimal("1000.00"),
        billDay = 10,
    )

    @Test
    fun `normalizeIssuer lowercases strips accents and non-alphanumerics`() {
        assertEquals("itaucard", IssuerCardMatcher.normalizeIssuer("Itaú Card!"))
    }

    @Test
    fun `resolve matches the app label against a card literally named after the issuer`() {
        val cards = listOf(card("card-1", "Nubank"))

        val result = IssuerCardMatcher.resolve(
            app = "Nubank",
            text = "Recebemos seu pagamento no valor de R$ 8.866,19.",
            cards = cards,
            learned = emptyMap(),
        )

        assertEquals("card-1", result)
    }

    @Test
    fun `resolve falls back to the leading text token when the app label misses`() {
        val cards = listOf(card("card-1", "Nubank"))

        val result = IssuerCardMatcher.resolve(
            app = "com.package.wrapper",
            text = "Nubank: Recebemos seu pagamento no valor de R$ 8.866,19.",
            cards = cards,
            learned = emptyMap(),
        )

        assertEquals("card-1", result)
    }

    @Test
    fun `resolve two cards named after the same issuer returns null`() {
        val cards = listOf(card("card-1", "Nubank"), card("card-2", "Nubank"))

        val result = IssuerCardMatcher.resolve(
            app = "Nubank",
            text = "Recebemos seu pagamento no valor de R$ 8.866,19.",
            cards = cards,
            learned = emptyMap(),
        )

        assertNull(result)
    }

    @Test
    fun `resolve learned mapping wins over a conflicting name match`() {
        val cards = listOf(card("card-1", "Nubank"))

        val result = IssuerCardMatcher.resolve(
            app = "Nubank",
            text = "Recebemos seu pagamento no valor de R$ 8.866,19.",
            cards = cards,
            learned = mapOf("nubank" to "card-2"),
        )

        assertEquals("card-2", result)
    }

    @Test
    fun `resolve matches a card name that contains the issuer label`() {
        val cards = listOf(card("card-1", "Nubank Ultravioleta"))

        val result = IssuerCardMatcher.resolve(
            app = "Nubank",
            text = "Recebemos seu pagamento no valor de R$ 8.866,19.",
            cards = cards,
            learned = emptyMap(),
        )

        assertEquals("card-1", result)
    }

    @Test
    fun `resolve matches an issuer label that contains a shorter card name`() {
        val cards = listOf(card("card-1", "Nubank"))

        val result = IssuerCardMatcher.resolve(
            app = "Nubank Ultravioleta",
            text = "Recebemos seu pagamento no valor de R$ 8.866,19.",
            cards = cards,
            learned = emptyMap(),
        )

        assertEquals("card-1", result)
    }

    @Test
    fun `resolve does not match a two-character card name against a longer issuer label`() {
        val cards = listOf(card("card-1", "Nu"))

        val result = IssuerCardMatcher.resolve(
            app = "Nubank",
            text = "Recebemos seu pagamento no valor de R$ 8.866,19.",
            cards = cards,
            learned = emptyMap(),
        )

        assertNull(result)
    }

    @Test
    fun `resolutionKey is the app label when it names a card on file`() {
        val cards = listOf(card("card-1", "Nubank"))

        val key = IssuerCardMatcher.resolutionKey(
            app = "Nubank",
            text = "Recebemos seu pagamento no valor de R$ 8.866,19.",
            cards = cards,
        )

        assertEquals("nubank", key)
    }

    @Test
    fun `resolutionKey falls back to the text's leading token when the app label names no card, as with an SMS aggregator`() {
        val cards = listOf(card("card-1", "Nubank"))

        val key = IssuerCardMatcher.resolutionKey(
            app = "Mensagens",
            text = "Nubank: Recebemos seu pagamento no valor de R$ 8.866,19.",
            cards = cards,
        )

        assertEquals("nubank", key)
    }

    @Test
    fun `resolve unknown issuer returns null`() {
        val cards = listOf(card("card-1", "Nu Roxinho"))

        val result = IssuerCardMatcher.resolve(
            app = "Itaú",
            text = "Recebemos o pagamento da sua fatura.",
            cards = cards,
            learned = emptyMap(),
        )

        assertNull(result)
    }

    @Test
    fun `resolve a utility issuer against Nubank and Itau cards returns null`() {
        val cards = listOf(card("card-1", "Nubank"), card("card-2", "Itaú"))

        val result = IssuerCardMatcher.resolve(
            app = "Vivo",
            text = "Vivo Recebemos o pagamento da sua fatura no valor de R$ 89,90. Obrigado!",
            cards = cards,
            learned = emptyMap(),
        )

        assertNull(result)
    }
}
