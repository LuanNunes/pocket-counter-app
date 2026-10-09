package com.resolveprogramming.pocketcounter.ui.quickadd

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** One focusable node, one action, one announcement — the mic contradicts none of it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickAddFieldTest {

    @get:Rule
    val compose = createComposeRule()

    private val label = "Lançamento rápido. O que você gastou ou recebeu?"

    private var clicks = 0

    private fun render() {
        compose.setContent {
            PocketTheme { QuickAddField(onClick = { clicks += 1 }) }
        }
    }

    @Test
    fun `the field is one enabled button naming what to write there`() {
        render()

        compose.onNodeWithContentDescription(label).assertHasClickAction().assertIsEnabled()
    }

    @Test
    fun `tapping it opens the sheet`() {
        render()

        compose.onNodeWithContentDescription(label).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `the mic is decoration, not a second node`() {
        render()

        assertEquals(0, compose.onAllNodesWithContentDescription("Ditar lançamento").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithContentDescription("Em breve").fetchSemanticsNodes().size)
    }
}
