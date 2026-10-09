package com.resolveprogramming.pocketcounter.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a tag needs before it can be created, and what the inline form still has to collect. */
class TagCreationTest {

    private val casa = TagContext("ctx", "Casa", 0xFF112233)

    @Test
    fun `no kind, nothing to create`() {
        assertFalse(canCreateTag(null, listOf(casa)))
    }

    @Test
    fun `an expense with no categories has nowhere to put a tag`() {
        assertFalse(canCreateTag(TransactionType.EXPENSE, emptyList()))
    }

    @Test
    fun `an existing category is enough on its own`() {
        assertTrue(canCreateTag(TransactionType.EXPENSE, listOf(casa)))
    }

    @Test
    fun `income needs no category`() {
        assertTrue(canCreateTag(TransactionType.INCOME, emptyList()))
    }

    @Test
    fun `a nameless tag is never complete`() {
        val request = NewTagRequest(name = "   ", color = 0, contextId = "ctx")

        assertFalse(request.isComplete(TransactionType.EXPENSE))
        assertFalse(request.copy(name = "").isComplete(TransactionType.INCOME))
    }

    @Test
    fun `an expense needs a category picked`() {
        val request = NewTagRequest(name = "Veterinário", color = 0)

        assertFalse(request.isComplete(TransactionType.EXPENSE))
        assertTrue(request.copy(contextId = "ctx").isComplete(TransactionType.EXPENSE))
    }

    @Test
    fun `an income category is complete on the name alone`() {
        val request = NewTagRequest(name = "Dividendos", color = 0xFF23A268)

        assertTrue(request.isComplete(TransactionType.INCOME))
    }
}
