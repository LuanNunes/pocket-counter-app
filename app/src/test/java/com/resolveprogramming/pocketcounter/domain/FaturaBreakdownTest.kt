package com.resolveprogramming.pocketcounter.domain

import com.resolveprogramming.pocketcounter.domain.model.InvoiceItem
import com.resolveprogramming.pocketcounter.domain.model.SummaryGroup
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.UNCATEGORIZED_COLOR
import com.resolveprogramming.pocketcounter.domain.model.UNCATEGORIZED_CONTEXT_ID
import com.resolveprogramming.pocketcounter.domain.model.buildFaturaBreakdown
import com.resolveprogramming.pocketcounter.domain.model.faturaDonutSlices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class FaturaBreakdownTest {

    // ── test-data builders ────────────────────────────────────────────────────

    private fun item(
        id: String = "item-1",
        amount: BigDecimal,
        tags: List<Tag> = emptyList(),
    ) = InvoiceItem(
        transactionId = "tx-$id",
        invoiceId = "inv-1",
        itemId = id,
        name = id,
        date = LocalDate.of(2026, 6, 4),
        amount = amount,
        tags = tags,
        installmentLabel = null,
    )

    private fun tag(id: String) = Tag(
        id = id,
        name = "Tag $id",
        kind = TransactionType.EXPENSE,
        idContext = null,
    )

    private fun context(id: String, name: String, color: Long = 0xFF112233L) =
        TagContext(id = id, name = name, color = color)

    private fun summaryGroup(
        id: String,
        pct: Float,
        total: BigDecimal,
        color: Long = 0xFF000000L,
    ) = SummaryGroup(
        id = id,
        name = id,
        color = color,
        total = total,
        pct = pct,
        delta = null,
        prevTotal = null,
        tags = emptyList(),
    )

    // ── buildFaturaBreakdown ──────────────────────────────────────────────────

    @Test
    fun `buildFaturaBreakdown returns empty when faturaTotal is zero`() {
        val result = buildFaturaBreakdown(
            items = listOf(item(amount = BigDecimal("50.00"))),
            faturaTotal = BigDecimal.ZERO,
            tagToContext = emptyMap(),
            contextById = emptyMap(),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `buildFaturaBreakdown returns empty when faturaTotal is negative`() {
        val result = buildFaturaBreakdown(
            items = emptyList(),
            faturaTotal = BigDecimal("-1.00"),
            tagToContext = emptyMap(),
            contextById = emptyMap(),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `buildFaturaBreakdown returns single uncategorized group equal to faturaTotal when items are empty`() {
        val result = buildFaturaBreakdown(
            items = emptyList(),
            faturaTotal = BigDecimal("100.00"),
            tagToContext = emptyMap(),
            contextById = emptyMap(),
        )

        assertEquals(1, result.size)
        val group = result.single()
        assertEquals(UNCATEGORIZED_CONTEXT_ID, group.id)
        assertEquals("Sem categoria", group.name)
        assertEquals(UNCATEGORIZED_COLOR, group.color)
        assertEquals(0, group.total.compareTo(BigDecimal("100.00")))
        assertEquals(1.0f, group.pct, 0.001f)
    }

    @Test
    fun `buildFaturaBreakdown buckets item under its first tag context resolved via tagToContext`() {
        val ctx = context("ctx-food", "Alimentação", 0xFF123456L)
        val t = tag("tag-burger")
        val inv = item("i1", BigDecimal("30.00"), tags = listOf(t))

        val result = buildFaturaBreakdown(
            items = listOf(inv),
            faturaTotal = BigDecimal("30.00"),
            tagToContext = mapOf("tag-burger" to "ctx-food"),
            contextById = mapOf("ctx-food" to ctx),
        )

        val foodGroup = result.first { it.id == "ctx-food" }
        assertEquals("Alimentação", foodGroup.name)
        assertEquals(0xFF123456L, foodGroup.color)
        assertEquals(0, foodGroup.total.compareTo(BigDecimal("30.00")))
    }

    @Test
    fun `buildFaturaBreakdown uses context id name and color not the tag own idContext field`() {
        // The tag's own idContext points elsewhere; the lookup goes through tagToContext exclusively.
        val ctxColor = 0xFFAABBCCL
        val ctx = context("ctx-x", "Lazer", ctxColor)
        val t = Tag(
            id = "tag-x",
            name = "Movies",
            kind = TransactionType.EXPENSE,
            idContext = "something-else", // NOT what the bucket lookup should use
            color = 0xFF000001L,
        )
        val inv = item("i1", BigDecimal("50.00"), tags = listOf(t))

        val result = buildFaturaBreakdown(
            items = listOf(inv),
            faturaTotal = BigDecimal("50.00"),
            tagToContext = mapOf("tag-x" to "ctx-x"),
            contextById = mapOf("ctx-x" to ctx),
        )

        val group = result.first { it.id == "ctx-x" }
        assertEquals("ctx-x", group.id)
        assertEquals("Lazer", group.name)
        assertEquals(ctxColor, group.color)
    }

    @Test
    fun `buildFaturaBreakdown sends item with no tags to uncategorized`() {
        val inv = item("i1", BigDecimal("40.00"), tags = emptyList())

        val result = buildFaturaBreakdown(
            items = listOf(inv),
            faturaTotal = BigDecimal("40.00"),
            tagToContext = emptyMap(),
            contextById = emptyMap(),
        )

        val sem = result.single { it.id == UNCATEGORIZED_CONTEXT_ID }
        assertEquals(0, sem.total.compareTo(BigDecimal("40.00")))
    }

    @Test
    fun `buildFaturaBreakdown sends item whose tag maps to null context id to uncategorized`() {
        val t = tag("tag-no-ctx")
        val inv = item("i1", BigDecimal("25.00"), tags = listOf(t))

        val result = buildFaturaBreakdown(
            items = listOf(inv),
            faturaTotal = BigDecimal("25.00"),
            tagToContext = mapOf("tag-no-ctx" to null),
            contextById = emptyMap(),
        )

        assertTrue(result.any { it.id == UNCATEGORIZED_CONTEXT_ID })
    }

    @Test
    fun `buildFaturaBreakdown sends item whose tag maps to blank context id to uncategorized`() {
        val t = tag("tag-blank")
        val inv = item("i1", BigDecimal("20.00"), tags = listOf(t))

        val result = buildFaturaBreakdown(
            items = listOf(inv),
            faturaTotal = BigDecimal("20.00"),
            tagToContext = mapOf("tag-blank" to ""),
            contextById = emptyMap(),
        )

        assertTrue(result.any { it.id == UNCATEGORIZED_CONTEXT_ID })
    }

    @Test
    fun `buildFaturaBreakdown sends item whose context id is absent from contextById to uncategorized`() {
        val t = tag("tag-ghost")
        val inv = item("i1", BigDecimal("15.00"), tags = listOf(t))

        val result = buildFaturaBreakdown(
            items = listOf(inv),
            faturaTotal = BigDecimal("15.00"),
            tagToContext = mapOf("tag-ghost" to "ctx-ghost"),
            contextById = emptyMap(), // ctx-ghost not present
        )

        assertTrue(result.any { it.id == UNCATEGORIZED_CONTEXT_ID })
    }

    @Test
    fun `buildFaturaBreakdown reconciles partial itemization remainder into uncategorized`() {
        // container=100, one item 30 under c1 → c1=30, Sem categoria=70, sum=100
        val ctx = context("c1", "C1")
        val t = tag("t1")
        val inv = item("i1", BigDecimal("30.00"), tags = listOf(t))

        val result = buildFaturaBreakdown(
            items = listOf(inv),
            faturaTotal = BigDecimal("100.00"),
            tagToContext = mapOf("t1" to "c1"),
            contextById = mapOf("c1" to ctx),
        )

        val c1Group = result.first { it.id == "c1" }
        val semGroup = result.first { it.id == UNCATEGORIZED_CONTEXT_ID }
        assertEquals(0, c1Group.total.compareTo(BigDecimal("30.00")))
        assertEquals(0, semGroup.total.compareTo(BigDecimal("70.00")))
        val total = result.fold(BigDecimal.ZERO) { acc, g -> acc + g.total }
        assertEquals(0, total.compareTo(BigDecimal("100.00")))
    }

    @Test
    fun `buildFaturaBreakdown produces no uncategorized group when fully itemized`() {
        // container=150, items 100(c1)+50(c2) → groups sum 150, no Sem categoria added
        val ctx1 = context("c1", "C1")
        val ctx2 = context("c2", "C2")
        val t1 = tag("t1")
        val t2 = tag("t2")
        val items = listOf(
            item("i1", BigDecimal("100.00"), tags = listOf(t1)),
            item("i2", BigDecimal("50.00"), tags = listOf(t2)),
        )

        val result = buildFaturaBreakdown(
            items = items,
            faturaTotal = BigDecimal("150.00"),
            tagToContext = mapOf("t1" to "c1", "t2" to "c2"),
            contextById = mapOf("c1" to ctx1, "c2" to ctx2),
        )

        assertTrue(result.none { it.id == UNCATEGORIZED_CONTEXT_ID })
        assertEquals(2, result.size)
        val total = result.fold(BigDecimal.ZERO) { acc, g -> acc + g.total }
        assertEquals(0, total.compareTo(BigDecimal("150.00")))
    }

    @Test
    fun `buildFaturaBreakdown absorbs negative remainder into uncategorized when over-itemized`() {
        // container=100, item 120(c1) → Sem categoria=-20, total still reconciles to 100
        val ctx = context("c1", "C1")
        val t = tag("t1")
        val inv = item("i1", BigDecimal("120.00"), tags = listOf(t))

        val result = buildFaturaBreakdown(
            items = listOf(inv),
            faturaTotal = BigDecimal("100.00"),
            tagToContext = mapOf("t1" to "c1"),
            contextById = mapOf("c1" to ctx),
        )

        val semGroup = result.first { it.id == UNCATEGORIZED_CONTEXT_ID }
        assertEquals(0, semGroup.total.compareTo(BigDecimal("-20.00")))
        val total = result.fold(BigDecimal.ZERO) { acc, g -> acc + g.total }
        assertEquals(0, total.compareTo(BigDecimal("100.00")))
    }

    @Test
    fun `buildFaturaBreakdown pct equals group total divided by faturaTotal`() {
        val ctx = context("c1", "C1")
        val t = tag("t1")
        val inv = item("i1", BigDecimal("60.00"), tags = listOf(t))

        val result = buildFaturaBreakdown(
            items = listOf(inv),
            faturaTotal = BigDecimal("120.00"),
            tagToContext = mapOf("t1" to "c1"),
            contextById = mapOf("c1" to ctx),
        )

        val c1Group = result.first { it.id == "c1" }
        // 60 / 120 = 0.5
        assertEquals(0.5f, c1Group.pct, 0.001f)
    }

    @Test
    fun `buildFaturaBreakdown sorts uncategorized last and other groups by total descending`() {
        // i1=20(t1→c1), i2=50(t2→c2), i3=10(tU→null, uncategorized)
        // faturaTotal=100 → remainder=20 folded into Sem categoria (total=30)
        // Expected sort: c2(50), c1(20), Sem categoria(30) — sem categoria forced last
        val ctx1 = context("c1", "C1")
        val ctx2 = context("c2", "C2")
        val t1 = tag("t1")
        val t2 = tag("t2")
        val tU = tag("tU")
        val items = listOf(
            item("i1", BigDecimal("20.00"), tags = listOf(t1)),
            item("i2", BigDecimal("50.00"), tags = listOf(t2)),
            item("i3", BigDecimal("10.00"), tags = listOf(tU)),
        )

        val result = buildFaturaBreakdown(
            items = items,
            faturaTotal = BigDecimal("100.00"),
            tagToContext = mapOf("t1" to "c1", "t2" to "c2", "tU" to null),
            contextById = mapOf("c1" to ctx1, "c2" to ctx2),
        )

        val ids = result.map { it.id }
        val semIdx = ids.indexOf(UNCATEGORIZED_CONTEXT_ID)
        assertEquals("Sem categoria must be last", ids.size - 1, semIdx)
        assertTrue("c2 (larger) must precede c1 (smaller)", ids.indexOf("c2") < ids.indexOf("c1"))
    }

    @Test
    fun `buildFaturaBreakdown uses UNCATEGORIZED_COLOR for the sem categoria bucket`() {
        val result = buildFaturaBreakdown(
            items = emptyList(),
            faturaTotal = BigDecimal("50.00"),
            tagToContext = emptyMap(),
            contextById = emptyMap(),
        )

        val sem = result.single { it.id == UNCATEGORIZED_CONTEXT_ID }
        assertEquals(UNCATEGORIZED_COLOR, sem.color)
    }

    // ── faturaDonutSlices ─────────────────────────────────────────────────────

    @Test
    fun `faturaDonutSlices drops slices with zero pct`() {
        val groups = listOf(
            summaryGroup("c1", pct = 0.6f, total = BigDecimal("60.00")),
            summaryGroup("c2", pct = 0.0f, total = BigDecimal.ZERO),
        )

        val result = faturaDonutSlices(groups, maxSlices = 7)

        assertEquals(1, result.size)
        assertEquals("c1", result.single().id)
    }

    @Test
    fun `faturaDonutSlices drops slices with negative pct`() {
        val groups = listOf(
            summaryGroup("c1", pct = 0.7f, total = BigDecimal("70.00")),
            summaryGroup("c2", pct = -0.1f, total = BigDecimal("-10.00")),
        )

        val result = faturaDonutSlices(groups, maxSlices = 7)

        assertEquals(1, result.size)
        assertEquals("c1", result.single().id)
    }

    @Test
    fun `faturaDonutSlices returns slices unchanged when positive count is within maxSlices`() {
        val groups = listOf(
            summaryGroup("c1", pct = 0.5f, total = BigDecimal("50.00")),
            summaryGroup("c2", pct = 0.3f, total = BigDecimal("30.00")),
            summaryGroup("c3", pct = 0.2f, total = BigDecimal("20.00")),
        )

        val result = faturaDonutSlices(groups, maxSlices = 7)

        assertEquals(3, result.size)
        assertEquals(listOf("c1", "c2", "c3"), result.map { it.id })
    }

    @Test
    fun `faturaDonutSlices folds tail into outros when real categories exceed maxSlices`() {
        // 5 positive real categories, maxSlices=3 → top 2 kept, c3+c4+c5 → Outros (total=40)
        val groups = listOf(
            summaryGroup("c1", pct = 0.35f, total = BigDecimal("35.00")),
            summaryGroup("c2", pct = 0.25f, total = BigDecimal("25.00")),
            summaryGroup("c3", pct = 0.20f, total = BigDecimal("20.00")),
            summaryGroup("c4", pct = 0.12f, total = BigDecimal("12.00")),
            summaryGroup("c5", pct = 0.08f, total = BigDecimal("8.00")),
        )

        val result = faturaDonutSlices(groups, maxSlices = 3)

        val outros = result.firstOrNull { it.id == "outros" }
        assertTrue("Expected 'outros' group in result", outros != null)
        assertEquals(0, outros!!.total.compareTo(BigDecimal("40.00")))
        // Top categories survive
        assertTrue(result.any { it.id == "c1" })
        assertTrue(result.any { it.id == "c2" })
        // Tail is folded, not present as own slices
        assertTrue(result.none { it.id == "c3" })
        assertTrue(result.none { it.id == "c4" })
        assertTrue(result.none { it.id == "c5" })
        // Result is bounded (fewer entries than original)
        assertTrue(result.size < groups.size)
    }

    @Test
    fun `faturaDonutSlices keeps sem categoria as own slice and does not fold it into outros`() {
        // 4 real categories + sem categoria, maxSlices=3; sem categoria must remain distinct
        val semGroup = summaryGroup(UNCATEGORIZED_CONTEXT_ID, pct = 0.10f, total = BigDecimal("10.00"))
        val groups = listOf(
            summaryGroup("c1", pct = 0.35f, total = BigDecimal("35.00")),
            summaryGroup("c2", pct = 0.25f, total = BigDecimal("25.00")),
            summaryGroup("c3", pct = 0.20f, total = BigDecimal("20.00")),
            summaryGroup("c4", pct = 0.10f, total = BigDecimal("10.00")),
            semGroup,
        )

        val result = faturaDonutSlices(groups, maxSlices = 3)

        // sem categoria is its own slice in the output
        assertTrue("sem categoria must survive as own slice", result.any { it.id == UNCATEGORIZED_CONTEXT_ID })
        // outros is present (real tail was folded)
        assertTrue("Outros must be present", result.any { it.id == "outros" })
        // sem categoria total is unchanged — it was not absorbed into outros
        val semSlice = result.first { it.id == UNCATEGORIZED_CONTEXT_ID }
        assertEquals(0, semSlice.total.compareTo(BigDecimal("10.00")))
    }

    @Test
    fun `faturaDonutSlices outros total equals sum of folded tail totals`() {
        // 4 real categories, maxSlices=3 → keep top 2; fold c3(12)+c4(8) → Outros(20)
        val groups = listOf(
            summaryGroup("c1", pct = 0.50f, total = BigDecimal("50.00")),
            summaryGroup("c2", pct = 0.30f, total = BigDecimal("30.00")),
            summaryGroup("c3", pct = 0.12f, total = BigDecimal("12.00")),
            summaryGroup("c4", pct = 0.08f, total = BigDecimal("8.00")),
        )

        val result = faturaDonutSlices(groups, maxSlices = 3)

        val outros = result.first { it.id == "outros" }
        assertEquals(0, outros.total.compareTo(BigDecimal("20.00")))
    }
}
