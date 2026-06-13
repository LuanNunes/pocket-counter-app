package com.resolveprogramming.pocketcounter.domain

import com.resolveprogramming.pocketcounter.domain.model.effectiveTagIds
import org.junit.Assert.assertEquals
import org.junit.Test

class TagResolutionTest {

    private val sourceDefaults = listOf("t1", "t2")

    @Test
    fun `null own tags inherit the source defaults`() {
        assertEquals(sourceDefaults, effectiveTagIds(null, sourceDefaults))
    }

    @Test
    fun `empty own tags are an explicit empty override, not inheritance`() {
        assertEquals(emptyList<String>(), effectiveTagIds(emptyList(), sourceDefaults))
    }

    @Test
    fun `non-empty own tags override the source defaults`() {
        assertEquals(listOf("x"), effectiveTagIds(listOf("x"), sourceDefaults))
    }

    @Test
    fun `inheriting from a source with no defaults yields empty`() {
        assertEquals(emptyList<String>(), effectiveTagIds(null, emptyList()))
    }
}
