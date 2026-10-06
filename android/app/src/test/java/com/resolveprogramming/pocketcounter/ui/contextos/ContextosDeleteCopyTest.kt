package com.resolveprogramming.pocketcounter.ui.contextos

import org.junit.Assert.assertEquals
import org.junit.Test

/** The count is the whole warning, so the sentence has to be absent at zero and agree in number. */
class ContextosDeleteCopyTest {

    private fun target(ruleCount: Int) = TagDeleteTarget("tag-1", "Mercado", ruleCount)

    @Test
    fun `no rules means no second sentence`() {
        assertEquals(
            "“Mercado” será removida das transações que a usam.",
            tagDeleteMessage(target(0)),
        )
    }

    @Test
    fun `one rule is singular`() {
        assertEquals(
            "“Mercado” será removida das transações que a usam. " +
                "1 regra aprendida que usa esta tag também será excluída.",
            tagDeleteMessage(target(1)),
        )
    }

    @Test
    fun `several rules are plural`() {
        assertEquals(
            "“Mercado” será removida das transações que a usam. " +
                "4 regras aprendidas que usam esta tag também serão excluídas.",
            tagDeleteMessage(target(4)),
        )
    }

    @Test
    fun `the context warning names the count`() {
        assertEquals(
            "3 regra(s) aprendida(s) dessas tags também serão excluídas.",
            contextRulesWarning(3),
        )
    }
}
