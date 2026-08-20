package com.resolveprogramming.pocketcounter.domain.rules

import com.resolveprogramming.pocketcounter.domain.model.IgnoreScope
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IgnoreOptionsTest {

    @Test
    fun `resolve returns Pattern when a non-blank pattern is given`() {
        val option = IgnoreOptions.resolve(pattern = "IFOOD", app = "Google", channel = NotificationChannel.PUSH)

        assertEquals(IgnoreScope.Pattern("IFOOD"), option)
    }

    @Test
    fun `resolve falls back to Source when pattern is null and the source can be blocked`() {
        val option = IgnoreOptions.resolve(pattern = null, app = "Google", channel = NotificationChannel.PUSH)

        assertEquals(IgnoreScope.Source("Google"), option)
    }

    @Test
    fun `resolve falls back to Source when pattern is blank`() {
        val option = IgnoreOptions.resolve(pattern = "   ", app = "Google", channel = NotificationChannel.PUSH)

        assertEquals(IgnoreScope.Source("Google"), option)
    }

    @Test
    fun `resolve returns null when there is no pattern and the source cannot be blocked`() {
        val option = IgnoreOptions.resolve(pattern = null, app = "Google", channel = NotificationChannel.SMS)

        assertNull(option)
    }
}
