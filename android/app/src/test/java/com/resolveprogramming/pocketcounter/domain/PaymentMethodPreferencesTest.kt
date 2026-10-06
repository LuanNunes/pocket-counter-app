package com.resolveprogramming.pocketcounter.domain

import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethodPreferences
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentMethodPreferencesTest {

    @Test
    fun `default is all entries minus CRYPTO`() {
        val expected = PaymentMethod.entries.toSet() - PaymentMethod.CRYPTO
        assertEquals(expected, PaymentMethodPreferences.default)
    }

    @Test
    fun `resolve null returns default`() {
        assertEquals(PaymentMethodPreferences.default, PaymentMethodPreferences.resolve(null))
    }

    @Test
    fun `resolve emptySet returns default`() {
        assertEquals(PaymentMethodPreferences.default, PaymentMethodPreferences.resolve(emptySet()))
    }

    @Test
    fun `resolve with only unknown names returns default`() {
        assertEquals(PaymentMethodPreferences.default, PaymentMethodPreferences.resolve(setOf("UNKNOWN", "BOGUS")))
    }

    @Test
    fun `resolve ignores unknown names and keeps valid ones`() {
        val result = PaymentMethodPreferences.resolve(setOf("PIX", "UNKNOWN"))
        assertEquals(setOf(PaymentMethod.PIX), result)
    }

    @Test
    fun `resolve valid names maps them correctly`() {
        val result = PaymentMethodPreferences.resolve(setOf("PIX", "CASH"))
        assertEquals(setOf(PaymentMethod.PIX, PaymentMethod.CASH), result)
    }

    @Test
    fun `canDisable is false when method is the only enabled one`() {
        val singleEnabled = setOf(PaymentMethod.PIX)
        assertFalse(PaymentMethodPreferences.canDisable(singleEnabled, PaymentMethod.PIX))
    }

    @Test
    fun `canDisable is false when method is not in the enabled set`() {
        val enabled = setOf(PaymentMethod.PIX, PaymentMethod.CASH)
        assertFalse(PaymentMethodPreferences.canDisable(enabled, PaymentMethod.CREDIT))
    }

    @Test
    fun `canDisable is true when multiple methods are enabled and method is in the set`() {
        val enabled = setOf(PaymentMethod.PIX, PaymentMethod.CASH)
        assertTrue(PaymentMethodPreferences.canDisable(enabled, PaymentMethod.PIX))
    }

    @Test
    fun `toggle off the only enabled method is a no-op`() {
        val singleEnabled = setOf(PaymentMethod.PIX)
        val result = PaymentMethodPreferences.toggle(singleEnabled, PaymentMethod.PIX, on = false)
        assertEquals(singleEnabled, result)
    }

    @Test
    fun `toggle on CRYPTO adds CRYPTO`() {
        val enabled = PaymentMethodPreferences.default
        val result = PaymentMethodPreferences.toggle(enabled, PaymentMethod.CRYPTO, on = true)
        assertTrue(result.contains(PaymentMethod.CRYPTO))
    }

    @Test
    fun `toggle on already-enabled method leaves set unchanged`() {
        val enabled = setOf(PaymentMethod.PIX, PaymentMethod.CASH)
        val result = PaymentMethodPreferences.toggle(enabled, PaymentMethod.PIX, on = true)
        assertEquals(enabled, result)
    }

    @Test
    fun `toggle off a method removes it from the set`() {
        val enabled = setOf(PaymentMethod.PIX, PaymentMethod.CASH)
        val result = PaymentMethodPreferences.toggle(enabled, PaymentMethod.PIX, on = false)
        assertEquals(setOf(PaymentMethod.CASH), result)
    }

    @Test
    fun `enable then disable round-trips back to original`() {
        val original = setOf(PaymentMethod.PIX, PaymentMethod.CASH)
        val withCrypto = PaymentMethodPreferences.toggle(original, PaymentMethod.CRYPTO, on = true)
        val backToOriginal = PaymentMethodPreferences.toggle(withCrypto, PaymentMethod.CRYPTO, on = false)
        assertEquals(original, backToOriginal)
    }

    @Test
    fun `selectable lists only enabled methods`() {
        val enabled = setOf(PaymentMethod.PIX, PaymentMethod.CASH)
        val result = PaymentMethodPreferences.selectable(enabled, selected = null, type = TransactionType.EXPENSE)
        assertEquals(listOf(PaymentMethod.PIX, PaymentMethod.CASH), result)
    }

    @Test
    fun `selectable keeps the currently-selected method even when it is disabled`() {
        // CRYPTO is globally off, but the item being edited already uses it — it must stay selectable.
        val enabled = setOf(PaymentMethod.PIX)
        val result = PaymentMethodPreferences.selectable(enabled, selected = PaymentMethod.CRYPTO, type = TransactionType.EXPENSE)
        assertTrue(PaymentMethod.CRYPTO in result)
        assertTrue(PaymentMethod.PIX in result)
    }

    @Test
    fun `selectable drops CREDIT on income`() {
        val enabled = setOf(PaymentMethod.CREDIT, PaymentMethod.PIX)
        val result = PaymentMethodPreferences.selectable(enabled, selected = null, type = TransactionType.INCOME)
        assertFalse(PaymentMethod.CREDIT in result)
        assertTrue(PaymentMethod.PIX in result)
    }

    @Test
    fun `selectable keeps CREDIT on expense`() {
        val enabled = setOf(PaymentMethod.CREDIT, PaymentMethod.PIX)
        val result = PaymentMethodPreferences.selectable(enabled, selected = null, type = TransactionType.EXPENSE)
        assertTrue(PaymentMethod.CREDIT in result)
    }
}
