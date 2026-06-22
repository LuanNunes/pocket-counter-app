package com.resolveprogramming.pocketcounter.ui.contextos

import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextSectionsTest {

    private fun ctx(id: String) = TagContext(id = id, name = "C-$id", color = 0L)

    /** An EXPENSE tag belonging to the given context id. */
    private fun expenseTag(id: String, ctxId: String?) =
        Tag(id = id, name = "T-$id", kind = TransactionType.EXPENSE, idContext = ctxId)

    /** An INCOME tag — no context, carries its own color. */
    private fun incomeTag(id: String, color: Long = 0xFF_AABBCCL) =
        Tag(id = id, name = "T-$id", kind = TransactionType.INCOME, idContext = null, color = color)

    // ── buildContextSections: expense grouping (existing behaviour, updated helpers) ─────────

    @Test
    fun `tags group under their context in context order`() {
        val contexts = listOf(ctx("a"), ctx("b"))
        val tags = listOf(expenseTag("t1", "a"), expenseTag("t2", "a"), expenseTag("t3", "b"))

        val sections = buildContextSections(contexts, tags)

        assertEquals(listOf("C-a", "C-b"), sections.map { it.title })
        assertEquals(2, sections[0].tags.size)
        assertEquals(1, sections[1].tags.size)
    }

    @Test
    fun `context with no tags still gets a section`() {
        val sections = buildContextSections(
            listOf(ctx("a"), ctx("b")),
            listOf(expenseTag("t1", "a")),
        )
        assertTrue(sections[1].tags.isEmpty())
    }

    @Test
    fun `expense tag with null context lands in Sem contexto bucket`() {
        val contexts = listOf(ctx("a"))
        val tags = listOf(expenseTag("t1", "a"), expenseTag("t2", null))

        val sections = buildContextSections(contexts, tags)

        assertEquals("Sem contexto", sections.last().title)
        assertNull(sections.last().context)
        assertEquals(setOf("T-t2"), sections.last().tags.map { it.name }.toSet())
    }

    @Test
    fun `tags with blank or unknown context land in a trailing Sem contexto bucket`() {
        val contexts = listOf(ctx("a"))
        val tags = listOf(expenseTag("t1", "a"), expenseTag("t2", ""), expenseTag("t3", "ghost"))

        val sections = buildContextSections(contexts, tags)

        assertEquals("Sem contexto", sections.last().title)
        assertNull(sections.last().context)
        assertEquals(setOf("T-t2", "T-t3"), sections.last().tags.map { it.name }.toSet())
    }

    @Test
    fun `no Sem contexto bucket when every tag has a known context`() {
        val sections = buildContextSections(listOf(ctx("a")), listOf(expenseTag("t1", "a")))
        assertTrue(sections.none { it.title == "Sem contexto" })
    }

    @Test
    fun `a context literally named Sem contexto coexists with the orphan bucket (distinct identities)`() {
        // The orphan bucket title collides with this context's name; the screen keys on
        // context id, not title, so both sections are valid and distinguishable here.
        val named = TagContext(id = "real", name = "Sem contexto", color = 0L)
        val sections = buildContextSections(
            listOf(named),
            listOf(expenseTag("t1", "real"), expenseTag("t2", null)),
        )

        assertEquals(2, sections.size)
        assertEquals("real", sections[0].context?.id)
        assertNull(sections[1].context) // the orphan bucket
        assertEquals(2, sections.count { it.title == "Sem contexto" })
    }

    // ── buildContextSections: income tags must NEVER appear ──────────────────────────────────

    @Test
    fun `income tags never appear in expense context sections`() {
        val contexts = listOf(ctx("a"), ctx("b"))
        val expense1 = expenseTag("e1", "a")
        val expense2 = expenseTag("e2", "b")
        // income tags — one could even "look like" it has a matching context id, must still be excluded
        val income1 = incomeTag("i1")
        val income2 = incomeTag("i2")
        val incomeWithMatchingCtx = Tag(
            id = "i3",
            name = "T-i3",
            kind = TransactionType.INCOME,
            idContext = null,   // income tags have no context by contract
            color = 0xFFL,
        )

        val sections = buildContextSections(contexts, listOf(expense1, expense2, income1, income2, incomeWithMatchingCtx))

        val allTagsInSections = sections.flatMap { it.tags }
        assertTrue(
            "No income tag should appear in any ContextSection",
            allTagsInSections.none { it.kind == TransactionType.INCOME },
        )
        // The "Sem contexto" orphan bucket must not exist because incomes are filtered out
        // and the two expense tags each have a known context.
        assertTrue(sections.none { it.title == "Sem contexto" })
    }

    @Test
    fun `income tags do not land in Sem contexto bucket even when expense orphans are present`() {
        val contexts = listOf(ctx("a"))
        val expenseOrphan = expenseTag("e1", null)
        val income = incomeTag("i1")

        val sections = buildContextSections(contexts, listOf(expenseOrphan, income))

        val semCtx = sections.last()
        assertEquals("Sem contexto", semCtx.title)
        assertEquals(1, semCtx.tags.size)
        assertEquals("e1", semCtx.tags.single().id)
    }

    // ── incomeCategories ────────────────────────────────────────────────────────────────────

    @Test
    fun `incomeCategories returns only INCOME kind tags`() {
        val mixed = listOf(
            expenseTag("e1", "ctx1"),
            incomeTag("i1"),
            expenseTag("e2", null),
            incomeTag("i2"),
        )

        val result = incomeCategories(mixed)

        assertEquals(listOf("i1", "i2"), result.map { it.id })
    }

    @Test
    fun `incomeCategories returns empty list when there are no income tags`() {
        val onlyExpenses = listOf(expenseTag("e1", "ctx1"), expenseTag("e2", "ctx2"))

        val result = incomeCategories(onlyExpenses)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `incomeCategories returns empty list for empty input`() {
        assertTrue(incomeCategories(emptyList()).isEmpty())
    }

    @Test
    fun `incomeCategories preserves the original order of income tags`() {
        val tags = listOf(incomeTag("i3"), incomeTag("i1"), incomeTag("i2"))

        val result = incomeCategories(tags)

        assertEquals(listOf("i3", "i1", "i2"), result.map { it.id })
    }
}
