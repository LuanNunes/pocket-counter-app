package com.resolveprogramming.pocketcounter.domain

import com.resolveprogramming.pocketcounter.domain.model.automationPercent
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationStatTest {

    @Test
    fun `automationPercent returns 0 when monthTotal is 0`() {
        val result = automationPercent(autoDone = 0, monthTotal = 0)

        assertEquals(0, result)
    }

    @Test
    fun `automationPercent returns 0 when autoDone is 0 and monthTotal is positive`() {
        val result = automationPercent(autoDone = 0, monthTotal = 5)

        assertEquals(0, result)
    }

    @Test
    fun `automationPercent rounds down when fraction is below half`() {
        // 1/3 = 33.3… → rounds to 33
        val result = automationPercent(autoDone = 1, monthTotal = 3)

        assertEquals(33, result)
    }

    @Test
    fun `automationPercent rounds up when fraction is at or above half`() {
        // 2/3 = 66.6… → rounds to 67
        val result = automationPercent(autoDone = 2, monthTotal = 3)

        assertEquals(67, result)
    }

    @Test
    fun `automationPercent returns 75 for 3 out of 4`() {
        val result = automationPercent(autoDone = 3, monthTotal = 4)

        assertEquals(75, result)
    }

    @Test
    fun `automationPercent returns 100 when all transactions are automated`() {
        val result = automationPercent(autoDone = 4, monthTotal = 4)

        assertEquals(100, result)
    }

    @Test
    fun `automationPercent clamps to 100 when autoDone exceeds monthTotal`() {
        val result = automationPercent(autoDone = 6, monthTotal = 4)

        assertEquals(100, result)
    }

    @Test
    fun `automationPercent clamps to 0 when autoDone is negative`() {
        val result = automationPercent(autoDone = -1, monthTotal = 4)

        assertEquals(0, result)
    }
}
