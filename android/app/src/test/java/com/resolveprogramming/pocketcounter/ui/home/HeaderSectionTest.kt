package com.resolveprogramming.pocketcounter.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The avatar initial must come from the same string the greeting shows — never "?". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HeaderSectionTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(userName: String) {
        compose.setContent {
            PocketTheme {
                HeaderSection(userName = userName, isRefreshing = false, onRefresh = {})
            }
        }
    }

    @Test
    fun `a blank name greets and initials the fallback`() {
        render("   ")

        compose.onNodeWithText("Bem-vindo", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("B", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("?", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a padded name initials its first letter, not the whitespace`() {
        render("  ana ")

        compose.onNodeWithText("ana", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("A", useUnmergedTree = true).assertIsDisplayed()
    }
}
