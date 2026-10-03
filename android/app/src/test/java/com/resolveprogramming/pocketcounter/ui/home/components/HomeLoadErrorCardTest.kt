package com.resolveprogramming.pocketcounter.ui.home.components

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeLoadErrorCardTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(onRetry: () -> Unit = {}) {
        compose.setContent {
            PocketTheme { HomeLoadErrorCard(onRetry = onRetry) }
        }
    }

    @Test
    fun `names the failure and offers a retry`() {
        render()

        compose.onNodeWithText("Não foi possível carregar o mês.", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Tentar de novo", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `retrying calls back`() {
        var retries = 0
        render(onRetry = { retries++ })

        compose.onNode(hasClickAction()).performClick()

        assertEquals(1, retries)
    }

    @Test
    fun `the retry button keeps a 48dp touch target`() {
        render()

        compose.onNode(hasClickAction()).assertHeightIsAtLeast(48.dp)
    }
}
