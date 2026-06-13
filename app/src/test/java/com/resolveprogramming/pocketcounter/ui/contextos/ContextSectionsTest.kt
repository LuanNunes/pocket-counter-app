package com.resolveprogramming.pocketcounter.ui.contextos

import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextSectionsTest {

    private fun ctx(id: String) = TagContext(id = id, name = "C-$id", color = 0L)
    private fun tag(id: String, ctxId: String) = Tag(id = id, name = "T-$id", idContext = ctxId)

    @Test
    fun `tags group under their context in context order`() {
        val contexts = listOf(ctx("a"), ctx("b"))
        val tags = listOf(tag("t1", "a"), tag("t2", "a"), tag("t3", "b"))

        val sections = buildContextSections(contexts, tags)

        assertEquals(listOf("C-a", "C-b"), sections.map { it.title })
        assertEquals(2, sections[0].tags.size)
        assertEquals(1, sections[1].tags.size)
    }

    @Test
    fun `context with no tags still gets a section`() {
        val sections = buildContextSections(listOf(ctx("a"), ctx("b")), listOf(tag("t1", "a")))
        assertTrue(sections[1].tags.isEmpty())
    }

    @Test
    fun `tags with blank or unknown context land in a trailing Sem contexto bucket`() {
        val contexts = listOf(ctx("a"))
        val tags = listOf(tag("t1", "a"), tag("t2", ""), tag("t3", "ghost"))

        val sections = buildContextSections(contexts, tags)

        assertEquals("Sem contexto", sections.last().title)
        assertNull(sections.last().context)
        assertEquals(setOf("T-t2", "T-t3"), sections.last().tags.map { it.name }.toSet())
    }

    @Test
    fun `no Sem contexto bucket when every tag has a known context`() {
        val sections = buildContextSections(listOf(ctx("a")), listOf(tag("t1", "a")))
        assertTrue(sections.none { it.title == "Sem contexto" })
    }

    @Test
    fun `a context literally named Sem contexto coexists with the orphan bucket (distinct identities)`() {
        // The orphan bucket title collides with this context's name; the screen keys on
        // context id, not title, so both sections are valid and distinguishable here.
        val named = TagContext(id = "real", name = "Sem contexto", color = 0L)
        val sections = buildContextSections(listOf(named), listOf(tag("t1", "real"), tag("t2", "")))

        assertEquals(2, sections.size)
        assertEquals("real", sections[0].context?.id)
        assertNull(sections[1].context) // the orphan bucket
        assertEquals(2, sections.count { it.title == "Sem contexto" })
    }
}
