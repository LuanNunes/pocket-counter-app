package com.resolveprogramming.pocketcounter.ui.configuracoes

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.resolveprogramming.pocketcounter.domain.model.BlockedSource
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class CaptureSectionTest {

    @get:Rule
    val compose = createComposeRule()

    private fun blocked(label: String, date: LocalDate) = BlockedSource(
        label = label,
        blockedAt = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant(),
    )

    private fun render(vararg sources: BlockedSource, onUnblock: (String) -> Unit = {}) {
        compose.setContent {
            PocketTheme {
                Column {
                    CaptureSection(blockedSources = sources.toList(), onUnblock = onUnblock)
                }
            }
        }
    }

    @Test
    fun `the section explains itself even with nothing blocked`() {
        render()

        compose.onNodeWithText("Captura de notificações").assertIsDisplayed()
        compose.onNodeWithText("Apps bloqueados não geram nada para revisar.").assertIsDisplayed()
        compose.onNodeWithText("Nenhum app bloqueado.").assertIsDisplayed()
    }

    @Test
    fun `a single block is counted in the sub-line`() {
        render(blocked("Google", LocalDate.of(2026, 6, 3)))

        compose
            .onNodeWithText("1 app bloqueado. Apps bloqueados não geram nada para revisar.")
            .assertIsDisplayed()
    }

    @Test
    fun `several blocks are counted in the sub-line`() {
        render(
            blocked("Google", LocalDate.of(2026, 6, 3)),
            blocked("Promo Bank", LocalDate.of(2026, 5, 20)),
        )

        compose
            .onNodeWithText("2 apps bloqueados. Apps bloqueados não geram nada para revisar.")
            .assertIsDisplayed()
    }

    @Test
    fun `a blocked source shows its label and the day it was blocked`() {
        render(blocked("Google", LocalDate.of(2026, 6, 3)))

        compose.onNodeWithText("Google", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Bloqueado em 03/06/2026", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Nenhum app bloqueado.").assertDoesNotExist()
    }

    @Test
    fun `reactivating names the app it affects and reports the key`() {
        var unblocked: String? = null
        render(blocked("Google", LocalDate.of(2026, 6, 3)), onUnblock = { unblocked = it })

        compose
            .onNodeWithContentDescription("Reativar captura de Google")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("google", unblocked)
    }

    @Test
    fun `every blocked source gets its own row`() {
        render(
            blocked("Google", LocalDate.of(2026, 6, 3)),
            blocked("Promo Bank", LocalDate.of(2026, 5, 20)),
        )

        compose.onNodeWithContentDescription("Reativar captura de Google").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reativar captura de Promo Bank").assertIsDisplayed()
        compose.onNodeWithText("Bloqueado em 20/05/2026", useUnmergedTree = true).assertIsDisplayed()
    }
}
