package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentMethodResolverTest {

    // -------------------------------------------------------------------------
    // normalizeKey
    // -------------------------------------------------------------------------

    @Test
    fun `normalizeKey_trailingComma_strippedAndLowercased`() {
        assertEquals("débito", PaymentMethodResolver.normalizeKey("Débito,"))
    }

    @Test
    fun `normalizeKey_allUppercase_lowercased`() {
        assertEquals("débito", PaymentMethodResolver.normalizeKey("DÉBITO"))
    }

    @Test
    fun `normalizeKey_alreadyClean_unchanged`() {
        assertEquals("débito", PaymentMethodResolver.normalizeKey("débito"))
    }

    @Test
    fun `normalizeKey_leadingAndTrailingPunctuation_stripped`() {
        assertEquals("pix", PaymentMethodResolver.normalizeKey(".pix;"))
    }

    @Test
    fun `normalizeKey_whitespaceOnlyPunctuation_trims`() {
        assertEquals("pix", PaymentMethodResolver.normalizeKey("  pix  "))
    }

    // -------------------------------------------------------------------------
    // resolve — builtin fallback
    // -------------------------------------------------------------------------

    @Test
    fun `resolve_emptyMap_builtinKnownWord_returnsBuiltinMethod`() {
        val result = PaymentMethodResolver.resolve("compra no débito aprovada", emptyMap())

        assertEquals(PaymentMethod.DEBIT, result)
    }

    @Test
    fun `resolve_emptyMap_unknownWord_returnsNull`() {
        val result = PaymentMethodResolver.resolve("compra eletronico aprovada", emptyMap())

        assertNull(result)
    }

    // -------------------------------------------------------------------------
    // resolve — learned dictionary wins
    // -------------------------------------------------------------------------

    @Test
    fun `resolve_learnedKeyInMap_returnsLearnedMethod`() {
        val learned = mapOf("eletronico" to PaymentMethod.CREDIT)

        val result = PaymentMethodResolver.resolve("eletronico", learned)

        assertEquals(PaymentMethod.CREDIT, result)
    }

    @Test
    fun `resolve_learnedWinsOverBuiltin_differentMethod`() {
        // "débito" is known by the builtin as DEBIT; the learned map says PIX.
        val learned = mapOf("débito" to PaymentMethod.PIX)

        val result = PaymentMethodResolver.resolve("compra no débito", learned)

        assertEquals(PaymentMethod.PIX, result)
    }

    @Test
    fun `resolve_learnedKeyMatchedMidText`() {
        val learned = mapOf("eletronico" to PaymentMethod.CREDIT)

        val result = PaymentMethodResolver.resolve("compra eletronico aprovada R\$ 50,00", learned)

        assertEquals(PaymentMethod.CREDIT, result)
    }

    @Test
    fun `resolve_normalizationCollapsesVariant_matchesLearnedKey`() {
        // Stored key is "débito" (clean); token in text is "Débito," — normalization must unify them.
        val learned = mapOf("débito" to PaymentMethod.CREDIT)

        val result = PaymentMethodResolver.resolve("pagamento Débito, aprovado", learned)

        assertEquals(PaymentMethod.CREDIT, result)
    }
}
