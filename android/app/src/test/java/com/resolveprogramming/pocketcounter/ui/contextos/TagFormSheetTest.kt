package com.resolveprogramming.pocketcounter.ui.contextos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.resolveprogramming.pocketcounter.data.repository.TagInput
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The visible half of the double-tap guard: a save in flight has to show as such and refuse a
 * second tap. Assertions stay on existence and enabled state — Robolectric has no font metrics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class TagFormSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val ctxFood = TagContext("ctx-food", "Alimentação", 0xFF_AA_00_00L)

    private fun render(
        mode: TagFormMode = TagFormMode.Add("ctx-food"),
        initialName: String = "",
        isSaving: Boolean = false,
        errorMessage: String? = null,
        onSave: (TagInput) -> Unit = {},
    ) {
        compose.setContent {
            PocketTheme {
                TagFormSheet(
                    mode = mode,
                    editing = null,
                    contexts = listOf(ctxFood),
                    palette = CuratedPalette.argb,
                    onSave = onSave,
                    onDelete = {},
                    onDismiss = {},
                    initialName = initialName,
                    isSaving = isSaving,
                    errorMessage = errorMessage,
                )
            }
        }
    }

    @Test
    fun `initialName seeds the name field`() {
        render(initialName = "Mercado")

        compose.onNodeWithText("Mercado").assertIsDisplayed()
    }

    @Test
    fun `a seeded name under a context enables Salvar`() {
        render(initialName = "Mercado")

        compose.onNodeWithText("Salvar").assertIsEnabled()
    }

    @Test
    fun `an empty name keeps Salvar disabled`() {
        render()

        compose.onNodeWithText("Salvar").assertIsNotEnabled()
    }

    @Test
    fun `a save in flight swaps the label and refuses a second tap`() {
        render(initialName = "Mercado", isSaving = true)

        compose.onNodeWithText("Salvando…").assertIsNotEnabled()
    }

    @Test
    fun `errorMessage renders inline`() {
        render(initialName = "Mercado", errorMessage = "Não foi possível criar a tag: HTTP 409 Conflict")

        compose.onNodeWithText("Não foi possível criar a tag: HTTP 409 Conflict").assertIsDisplayed()
    }

    @Test
    fun `the income form needs no context to enable Salvar`() {
        render(mode = TagFormMode.AddIncome, initialName = "Salário")

        compose.onNodeWithText("Nova categoria").assertIsDisplayed()
        compose.onNodeWithText("Salvar").assertIsEnabled()
    }
}
