package com.resolveprogramming.pocketcounter.ui.wizard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.resolveprogramming.pocketcounter.domain.model.IgnoreScope
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Taps go through the OnClick semantics action: Robolectric does not deliver injected pointer
 * events to the dialog window.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class IgnoreConfirmDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(option: IgnoreScope?, defaultLearn: Boolean, sourceTransactionCount: Int = 0) {
        compose.setContent {
            PocketTheme {
                var learn by remember { mutableStateOf(defaultLearn) }
                IgnoreConfirmDialog(
                    option = option,
                    sourceTransactionCount = sourceTransactionCount,
                    learnSelected = learn,
                    onSelect = { learn = it },
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
    }

    private fun tap(text: String) =
        compose.onNodeWithText(text, substring = true).performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun `a pattern option names the pattern on both lines and starts selected`() {
        render(IgnoreScope.Pattern("Google"), defaultLearn = true)

        compose.onNodeWithText("Ignorar sempre \"Google\"", substring = true).assertIsSelected()
        compose
            .onNodeWithText("Novas notificações com \"Google\" entram já ignoradas.", useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Só esta notificação", substring = true).assertIsNotSelected()
    }

    @Test
    fun `a source option spells out the blast radius in words and starts unselected`() {
        render(IgnoreScope.Source("Google"), defaultLearn = false)

        compose.onNodeWithText("Nunca capturar notificações do Google", substring = true).assertIsNotSelected()
        compose
            .onNodeWithText(
                "Nada vindo do Google será capturado — inclusive compras, se houver.",
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
        compose.onNodeWithText("Só esta notificação", substring = true).assertIsSelected()
    }

    @Test
    fun `a source that already produced one transaction says so and still starts unselected`() {
        render(IgnoreScope.Source("Google"), defaultLearn = false, sourceTransactionCount = 1)

        compose
            .onNodeWithText(
                "Este app já registrou 1 transação sua. Nada vindo dele será capturado.",
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
        compose.onNodeWithText("Nunca capturar notificações do Google", substring = true).assertIsNotSelected()
    }

    @Test
    fun `a source that already produced several transactions names the count`() {
        render(IgnoreScope.Source("Google"), defaultLearn = false, sourceTransactionCount = 7)

        compose
            .onNodeWithText(
                "Este app já registrou 7 transações suas. Nada vindo dele será capturado.",
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
        compose.onNodeWithText("Nunca capturar notificações do Google", substring = true).assertIsNotSelected()
    }

    @Test
    fun `picking the learn option moves the selection off this-only`() {
        render(IgnoreScope.Source("Google"), defaultLearn = false)

        tap("Nunca capturar notificações do Google")

        compose.onNodeWithText("Nunca capturar notificações do Google", substring = true).assertIsSelected()
        compose.onNodeWithText("Só esta notificação", substring = true).assertIsNotSelected()
    }

    @Test
    fun `no derivable option renders a caption instead of a one-item radio group`() {
        render(option = null, defaultLearn = false)

        compose.onAllNodes(isSelectable()).assertCountEquals(0)
        compose
            .onNodeWithText("Não foi possível identificar um padrão nesta mensagem.", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `both actions stay reachable in every variant`() {
        render(option = null, defaultLearn = false)

        compose.onNodeWithText("Ignorar").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").assertIsDisplayed()
    }

    @Test
    fun `an unselected learn option resolves to this-only`() {
        assertEquals(IgnoreScope.ThisOnly, ignoreScopeOf(IgnoreScope.Source("Google"), learnSelected = false))
        assertEquals(IgnoreScope.ThisOnly, ignoreScopeOf(IgnoreScope.Pattern("UBER"), learnSelected = false))
    }

    @Test
    fun `a selected learn option resolves to the offered scope`() {
        assertEquals(
            IgnoreScope.Pattern("UBER"),
            ignoreScopeOf(IgnoreScope.Pattern("UBER"), learnSelected = true),
        )
        assertEquals(
            IgnoreScope.Source("Google"),
            ignoreScopeOf(IgnoreScope.Source("Google"), learnSelected = true),
        )
    }

    @Test
    fun `a missing option can never resolve to a learn scope`() {
        assertEquals(IgnoreScope.ThisOnly, ignoreScopeOf(option = null, learnSelected = true))
    }
}
