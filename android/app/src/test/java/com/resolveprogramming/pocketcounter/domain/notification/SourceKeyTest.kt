package com.resolveprogramming.pocketcounter.domain.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceKeyTest {

    @Test
    fun `normalize lowercases strips accents and non-alphanumerics`() {
        assertEquals("itaucard", SourceKey.normalize("Itaú Card!"))
    }

    @Test
    fun `normalize strips whitespace between words`() {
        assertEquals("googlepay", SourceKey.normalize("Google Pay"))
    }

    @Test
    fun `normalize of an emoji-only label is blank`() {
        assertTrue(SourceKey.normalize("📉📈").isBlank())
    }
}
