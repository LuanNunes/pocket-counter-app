package com.resolveprogramming.pocketcounter.ui.components

import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagPickerLogicTest {

    private val ctxAlim = TagContext("c1", "Alimentação", 0xFF_AA_00_00L)
    private val ctxTransp = TagContext("c2", "Transporte", 0xFF_00_AA_00L)
    private val contexts = listOf(ctxAlim, ctxTransp)

    // Expense tags
    private val tMercado = Tag("t-mercado", "Mercado", TransactionType.EXPENSE, idContext = "c1")
    private val tRestaurante = Tag("t-restaurante", "Restaurante", TransactionType.EXPENSE, idContext = "c1")
    private val tUber = Tag("t-uber", "Uber", TransactionType.EXPENSE, idContext = "c2")
    private val tOrfao = Tag("t-orfao", "Órfão", TransactionType.EXPENSE, idContext = null)
    private val tUnknownCtx = Tag("t-unknown", "Unknown", TransactionType.EXPENSE, idContext = "c-deleted")

    // Income tags
    private val tSalario = Tag("t-salario", "Salário", TransactionType.INCOME, idContext = null)

    private val allTags = listOf(tMercado, tRestaurante, tUber, tOrfao, tUnknownCtx, tSalario)

    // -- tagUniverse --

    @Test
    fun `tagUniverse filters tags to the given kind`() {
        val universe = tagUniverse(allTags, TransactionType.EXPENSE)

        assertTrue(universe.none { it.kind == TransactionType.INCOME })
        assertEquals(5, universe.size)
    }

    @Test
    fun `tagUniverse for INCOME returns only income tags`() {
        val universe = tagUniverse(allTags, TransactionType.INCOME)

        assertEquals(listOf(tSalario), universe)
    }

    // -- searchMatches --

    @Test
    fun `searchMatches blank query returns empty list`() {
        val universe = tagUniverse(allTags, TransactionType.EXPENSE)

        assertEquals(emptyList<Tag>(), searchMatches(universe, ""))
    }

    @Test
    fun `searchMatches blank query with whitespace returns empty list`() {
        val universe = tagUniverse(allTags, TransactionType.EXPENSE)

        assertEquals(emptyList<Tag>(), searchMatches(universe, "   "))
    }

    @Test
    fun `searchMatches is case-insensitive substring`() {
        val universe = tagUniverse(allTags, TransactionType.EXPENSE)

        val results = searchMatches(universe, "merCADO")

        assertEquals(listOf(tMercado), results)
    }

    @Test
    fun `searchMatches returns all matching tags across contexts`() {
        val universe = tagUniverse(allTags, TransactionType.EXPENSE)

        // "rante" appears only in "Restaurante"
        val results = searchMatches(universe, "rante")

        assertEquals(listOf(tRestaurante), results)
    }

    // -- categoriesFor --

    @Test
    fun `categoriesFor includes one category per context that has tags`() {
        val universe = tagUniverse(allTags, TransactionType.EXPENSE)

        val categories = categoriesFor(universe, contexts, emptySet())

        val contextIds = categories.map { it.id }
        assertTrue(contextIds.contains("c1"))
        assertTrue(contextIds.contains("c2"))
    }

    @Test
    fun `categoriesFor includes orphan category for tags with null or unknown context`() {
        val universe = tagUniverse(allTags, TransactionType.EXPENSE)

        val categories = categoriesFor(universe, contexts, emptySet())

        val orphan = categories.firstOrNull { it.id == ORPHAN_CONTEXT_ID }
        assertTrue("orphan category must be present", orphan != null)
        // tOrfao (null context) + tUnknownCtx (deleted context) = 2 orphan tags
        assertEquals(2, orphan!!.tagCount)
    }

    @Test
    fun `categoriesFor selectedCount reflects selected tags in each category`() {
        val universe = tagUniverse(allTags, TransactionType.EXPENSE)

        val categories = categoriesFor(universe, contexts, selectedTagIds = setOf("t-mercado", "t-restaurante"))

        val alim = categories.first { it.id == "c1" }
        assertEquals(2, alim.selectedCount)

        val transp = categories.first { it.id == "c2" }
        assertEquals(0, transp.selectedCount)
    }

    @Test
    fun `categoriesFor orphan selectedCount counts selected orphan tags`() {
        val universe = tagUniverse(allTags, TransactionType.EXPENSE)

        val categories = categoriesFor(universe, contexts, selectedTagIds = setOf("t-orfao"))

        val orphan = categories.first { it.id == ORPHAN_CONTEXT_ID }
        assertEquals(1, orphan.selectedCount)
    }

    @Test
    fun `categoriesFor excludes context with no tags in universe`() {
        // Universe contains only Alimentação tags — Transporte context should not appear
        val universe = listOf(tMercado, tRestaurante)

        val categories = categoriesFor(universe, contexts, emptySet())

        assertTrue(categories.none { it.id == "c2" })
    }

    // -- drillTags --

    @Test
    fun `drillTags returns tags for a known context id`() {
        val universe = tagUniverse(allTags, TransactionType.EXPENSE)

        val tags = drillTags(universe, "c1", contexts)

        assertEquals(listOf(tMercado, tRestaurante), tags)
    }

    @Test
    fun `drillTags returns orphan tags when context id is ORPHAN_CONTEXT_ID`() {
        val universe = tagUniverse(allTags, TransactionType.EXPENSE)

        val tags = drillTags(universe, ORPHAN_CONTEXT_ID, contexts)

        assertTrue(tags.contains(tOrfao))
        assertTrue(tags.contains(tUnknownCtx))
        assertEquals(2, tags.size)
    }

    @Test
    fun `drillTags returns empty list for null context id`() {
        val universe = tagUniverse(allTags, TransactionType.EXPENSE)

        val tags = drillTags(universe, null, contexts)

        assertEquals(emptyList<Tag>(), tags)
    }
}
