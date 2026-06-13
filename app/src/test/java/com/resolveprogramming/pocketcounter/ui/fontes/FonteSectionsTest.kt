package com.resolveprogramming.pocketcounter.ui.fontes

import com.resolveprogramming.pocketcounter.domain.model.PaymentSource
import com.resolveprogramming.pocketcounter.domain.model.PaymentSourceKind
import com.resolveprogramming.pocketcounter.domain.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FonteSectionsTest {

    private fun pay(id: String) = PaymentSource(
        id = id, name = "M-$id", sub = "", kind = PaymentSourceKind.CHECKING, color = 0L,
    )

    private fun src(id: String, payId: String) = Source(
        id = id, name = "S-$id", idPaymentSource = payId, allowsExpense = true, allowsIncome = false,
    )

    @Test
    fun `every payment gets a section, even with zero sources, in payment order`() {
        val payments = listOf(pay("a"), pay("b"))
        val sources = listOf(src("s1", "a")) // none for "b"

        val sections = buildFonteSections(sources, payments)

        assertEquals(listOf("M-a", "M-b"), sections.map { it.title })
        assertEquals(1, sections[0].sources.size)
        assertTrue("empty meio still has a section", sections[1].sources.isEmpty())
    }

    @Test
    fun `sources whose meio is missing land in a trailing Sem meio bucket`() {
        val payments = listOf(pay("a"))
        val sources = listOf(src("s1", "a"), src("s2", "ghost"))

        val sections = buildFonteSections(sources, payments)

        assertEquals(2, sections.size)
        assertEquals("Sem meio", sections.last().title)
        assertNull(sections.last().paymentSource)
        assertEquals(listOf("S-s2"), sections.last().sources.map { it.name })
    }

    @Test
    fun `no Sem meio bucket when every source has a known meio`() {
        val sections = buildFonteSections(listOf(src("s1", "a")), listOf(pay("a")))
        assertTrue(sections.none { it.title == "Sem meio" })
    }
}
