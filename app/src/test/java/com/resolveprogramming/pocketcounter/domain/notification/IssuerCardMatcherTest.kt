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
    fun `resolutionKey is null when neither the app label nor the leading token names a card on file`() {
        // The generic delivery app names no card and the push body doesn't either — there is no
        // signal safe to key a learned mapping on. Teaching the raw app label here would poison
        // every other issuer that also arrives through the same shared app.
        val cards = listOf(card("card-1", "Roxinho"))

        val key = IssuerCardMatcher.resolutionKey(
            app = "Mensagens",
            text = "Compra aprovada no valor de R$ 30,00",
            cards = cards,
        )

        assertNull(key)
    }

    @Test
    fun `resolutionKey teaches the app label even when it names two cards ambiguously, leaving disambiguation to the manual pick`() {
        // The app "Nubank" genuinely IS the issuer here — it just doesn't decide WHICH of the
        // user's two Nubank cards, which is exactly what the manual pick resolves. That is a real
        // signal, unlike the SMS-aggregator case where the app names no card at all.
        val cards = listOf(card("card-1", "Nubank"), card("card-2", "Nubank"))

        val key = IssuerCardMatcher.resolutionKey(
            app = "Nubank",
            text = "Recebemos seu pagamento no valor de R$ 8.866,19.",
            cards = cards,
        )

        assertEquals("nubank", key)
    }

    @Test
    fun `resolve reads a learned mapping keyed by the leading token, taught for an SMS aggregator delivery`() {
        // resolutionKey teaches the leading-token key here (app "Mensagens" names no card); resolve
        // must read that same key back, not just learned[normalizeIssuer(app)].
        val teachKey = IssuerCardMatcher.resolutionKey(
            app = "Mensagens",
            text = "Nubank: Recebemos seu pagamento no valor de R$ 8.866,19.",
            cards = listOf(card("card-1", "Nubank")),
        )

        val result = IssuerCardMatcher.resolve(
            app = "Mensagens",
            text = "Nubank: Confirmamos o pagamento da sua fatura.",
            cards = emptyList(),
            learned = mapOf(requireNotNull(teachKey) to "card-2"),
        )

        assertEquals("card-2", result)
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

    // Real BR-name collisions produced by containment on a whitespace-deleted string: "ame" ⊂
    // "pagamento", "inter" ⊂ "intermedica", "pan" ⊂ "companhia", "uni" ⊂ "unimed".

    @Test
    fun `resolve a card nicknamed Ame does not match the leading token of a Pagamento recebido push`() {
        val cards = listOf(card("card-1", "Ame"))

        val result = IssuerCardMatcher.resolve(
            app = "com.package.wrapper",
            text = "Pagamento recebido no valor de R$ 50,00.",
            cards = cards,
            learned = emptyMap(),
        )

        assertNull(result)
    }

    @Test
    fun `resolve a card nicknamed Inter does not match Notredame Intermedica`() {
        val cards = listOf(card("card-1", "Inter"))

        val result = IssuerCardMatcher.resolve(
            app = "Notredame Intermédica",
            text = "Recebemos o pagamento da sua fatura.",
            cards = cards,
            learned = emptyMap(),
        )

        assertNull(result)
    }

    @Test
    fun `resolve a card nicknamed Pan does not match Companhia`() {
        val cards = listOf(card("card-1", "Pan"))

        val result = IssuerCardMatcher.resolve(
            app = "Companhia de Saneamento",
            text = "Recebemos o pagamento da sua fatura.",
            cards = cards,
            learned = emptyMap(),
        )

        assertNull(result)
    }

    @Test
    fun `resolve a card nicknamed Uni does not match Unimed`() {
        val cards = listOf(card("card-1", "Uni"))

        val result = IssuerCardMatcher.resolve(
            app = "Unimed",
            text = "Recebemos o pagamento da sua fatura.",
            cards = cards,
            learned = emptyMap(),
        )

        assertNull(result)
    }
}
