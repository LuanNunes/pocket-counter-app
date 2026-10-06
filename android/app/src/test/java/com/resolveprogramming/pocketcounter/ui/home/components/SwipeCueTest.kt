package com.resolveprogramming.pocketcounter.ui.home.components

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "0 no mês" and "não carregou ainda" are different facts, and the cue is the only place the month's
 * entry count is spoken.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SwipeCueTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(count: Int, hasLoadedMonth: Boolean, onClick: () -> Unit = {}) {
        compose.setContent {
            PocketTheme {
                SwipeCue(count = count, hasLoadedMonth = hasLoadedMonth, onClick = onClick)
            }
        }
    }

    @Test
    fun `shows an em dash instead of a count while the month is unknown`() {
        render(count = 0, hasLoadedMonth = false)

        compose.onNodeWithText(UNKNOWN_FIGURE, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("0 no mês", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `invites the swipe when a loaded month is empty`() {
        render(count = 0, hasLoadedMonth = true)

        compose.onNodeWithText("deslize para ver", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `counts the month once loaded`() {
        render(count = 12, hasLoadedMonth = true)

        compose.onNodeWithText("12 no mês", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `the merged node keeps the labelled click action`() {
        var clicks = 0
        render(count = 12, hasLoadedMonth = true, onClick = { clicks++ })

        val cue = compose.onNodeWithContentDescription("Lançamentos, 12 no mês")
        cue.assertHasClickAction().performClick()

        assertEquals("Abrir lançamentos", cue.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        assertEquals(1, clicks)
    }
}
