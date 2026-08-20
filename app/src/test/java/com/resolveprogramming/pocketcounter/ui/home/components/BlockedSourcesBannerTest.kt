package com.resolveprogramming.pocketcounter.ui.home.components

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class BlockedSourcesBannerTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(count: Int, onManage: () -> Unit = {}) {
        compose.setContent {
            PocketTheme {
                BlockedSourcesBanner(count = count, onManage = onManage)
            }
        }
    }

    @Test
    fun `a single blocked source reads in the singular`() {
        render(count = 1)

        compose.onNodeWithText("Captura pausada para 1 app. Toque para rever.").assertIsDisplayed()
        compose.onNodeWithText("gerenciar", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `several blocked sources read in the plural and name the count`() {
        render(count = 2)

        compose.onNodeWithText("Captura pausada para 2 apps. Toque para rever.").assertIsDisplayed()
    }

    @Test
    fun `tapping the banner asks to manage the blocks`() {
        var managed = 0
        render(count = 1, onManage = { managed++ })

        compose
            .onNodeWithText("Captura pausada para 1 app. Toque para rever.")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, managed)
    }
}
