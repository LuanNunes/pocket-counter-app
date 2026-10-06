package com.resolveprogramming.pocketcounter.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/** The pure cents-pad conversion behind [MoneyTextField]. */
class MoneyTextFieldFormatTest {

    @Test
    fun `amountToDigits drops the decimal point and leading zeros`() {
        assertEquals("1234", amountToDigits(BigDecimal("12.34")))
        assertEquals("5", amountToDigits(BigDecimal("0.05")))
        assertEquals("123456", amountToDigits(BigDecimal("1234.56")))
        assertEquals("5000", amountToDigits(BigDecimal("50.00")))
    }

    @Test
    fun `amountToDigits is empty for null or zero`() {
        assertEquals("", amountToDigits(null))
        assertEquals("", amountToDigits(BigDecimal.ZERO))
        assertEquals("", amountToDigits(BigDecimal("0.00")))
    }

    @Test
    fun `amountToDigits rounds a stray third decimal to cents`() {
        assertEquals("1235", amountToDigits(BigDecimal("12.345")))
    }

    @Test
    fun `digitsToAmount reads digits as cents`() {
        assertEquals(BigDecimal("12.34"), digitsToAmount("1234"))
        assertEquals(BigDecimal("0.05"), digitsToAmount("5"))
        assertEquals(BigDecimal("1234.56"), digitsToAmount("123456"))
    }

    @Test
    fun `digitsToAmount is null for empty or all-zero`() {
        assertNull(digitsToAmount(""))
        assertNull(digitsToAmount("0"))
        assertNull(digitsToAmount("00"))
    }

    @Test
    fun `formatCentsDigits renders pt-BR grouping`() {
        assertEquals("12,34", formatCentsDigits("1234"))
        assertEquals("0,05", formatCentsDigits("5"))
        assertEquals("1.234,56", formatCentsDigits("123456"))
        assertEquals("1.000.000,00", formatCentsDigits("100000000"))
    }

    @Test
    fun `formatCentsDigits is empty for empty or all-zero`() {
        assertEquals("", formatCentsDigits(""))
        assertEquals("", formatCentsDigits("00"))
    }

    @Test
    fun `round-trips a typed amount`() {
        // Typing "1","2","3","4" then reading back must reproduce the same display + amount.
        val digits = "1234"
        assertEquals("12,34", formatCentsDigits(digits))
        val amount = digitsToAmount(digits)
        assertEquals(BigDecimal("12.34"), amount)
        assertEquals(digits, amountToDigits(amount))
    }
}
