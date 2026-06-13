package com.resolveprogramming.pocketcounter.ui.transacoes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReorderWithinGroupTest {

    @Test
    fun `moves a row within a non-contiguous group, leaving other rows in place`() {
        // group [A, C] occupy global slots 0 and 2; move A to group-index 1 → C, then A.
        val result = reorderWithinGroup(
            globalIds = listOf("A", "B", "C", "D"),
            groupIds = listOf("A", "C"),
            itemId = "A",
            targetIndex = 1,
        )
        assertEquals(listOf("C", "B", "A", "D"), result)
    }

    @Test
    fun `moves a middle row to the front of its group`() {
        val result = reorderWithinGroup(
            globalIds = listOf("A", "B", "C", "D"),
            groupIds = listOf("A", "B", "C", "D"),
            itemId = "C",
            targetIndex = 0,
        )
        assertEquals(listOf("C", "A", "B", "D"), result)
    }

    @Test
    fun `moves a row to the end of its group`() {
        val result = reorderWithinGroup(
            globalIds = listOf("A", "C", "E"),
            groupIds = listOf("A", "C", "E"),
            itemId = "A",
            targetIndex = 2,
        )
        assertEquals(listOf("C", "E", "A"), result)
    }

    @Test
    fun `no-op when target equals origin`() {
        assertNull(reorderWithinGroup(listOf("A", "B"), listOf("A", "B"), "A", 0))
    }

    @Test
    fun `null on out-of-range target or unknown item`() {
        assertNull(reorderWithinGroup(listOf("A", "B"), listOf("A", "B"), "A", 5))
        assertNull(reorderWithinGroup(listOf("A", "B"), listOf("A", "B"), "Z", 1))
    }
}
