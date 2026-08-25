package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class TagPickerTest {

    @get:Rule
    val compose = createComposeRule()

    private val ctxAlim = TagContext("c1", "Alimentação", 0xFF_AA_00_00L)
    private val tMercado = Tag("t-mercado", "Mercado", TransactionType.EXPENSE, idContext = "c1")
    private val tSalario = Tag("t-salario", "Salário", TransactionType.INCOME, idContext = null)

    private fun render(
        type: TransactionType,
        tags: List<Tag>,
        contexts: List<TagContext> = emptyList(),
        selectedTagIds: List<String> = emptyList(),
        onToggleTag: (String) -> Unit = {},
    ) {
        compose.setContent {
            PocketTheme {
                TagPicker(
                    type = type,
                    tags = tags,
                    contexts = contexts,
                    selectedTagIds = selectedTagIds,
                    onToggleTag = onToggleTag,
                )
            }
        }
    }

    @Test
    fun `empty expense universe shows the expense empty-state copy`() {
        render(type = TransactionType.EXPENSE, tags = emptyList())

        compose.onNodeWithText("Nenhuma tag ainda. Crie em Mais › Contextos & Tags.").assertIsDisplayed()
    }

    @Test
    fun `empty income universe shows the income empty-state copy`() {
        render(type = TransactionType.INCOME, tags = emptyList())

        compose.onNodeWithText("Nenhuma categoria de renda ainda. Crie em Mais › Contextos & Tags.").assertIsDisplayed()
    }

    @Test
    fun `toggling an income chip reports its id and reflects the checked state`() {
        var toggledId: String? = null
        render(
            type = TransactionType.INCOME,
            tags = listOf(tSalario),
            onToggleTag = { toggledId = it },
        )

        compose.onNodeWithText("Salário").assertIsOff()
        compose.onNodeWithText("Salário").performClick()

        assertEquals("t-salario", toggledId)
    }

    @Test
    fun `a selected chip reports on`() {
        render(
            type = TransactionType.INCOME,
            tags = listOf(tSalario),
            selectedTagIds = listOf("t-salario"),
        )

        // A pre-selected tag also renders as a pill above the chip flow, so the plain "Salário"
        // text is ambiguous; scope to the toggleable chip specifically.
        compose
            .onNode(isToggleable() and hasAnyDescendant(hasText("Salário")), useUnmergedTree = true)
            .assertIsOn()
    }

    @Test
    fun `CategoryRow exposes a combined a11y label with tag and selected counts`() {
        render(
            type = TransactionType.EXPENSE,
            tags = listOf(tMercado),
            contexts = listOf(ctxAlim),
            selectedTagIds = listOf("t-mercado"),
        )

        compose.onNodeWithContentDescription("Alimentação, 1 tag, 1 selecionada").assertIsDisplayed()
    }

    @Test
    fun `a selected pill exposes a single Remover content description`() {
        render(
            type = TransactionType.EXPENSE,
            tags = listOf(tMercado),
            contexts = listOf(ctxAlim),
            selectedTagIds = listOf("t-mercado"),
        )

        compose.onNodeWithContentDescription("Remover Mercado").assertIsDisplayed()
    }
}
