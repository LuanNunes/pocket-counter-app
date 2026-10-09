package com.resolveprogramming.pocketcounter.ui.transacoes

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethodPreferences
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The degradation path's landing: a sentence quick-add could not read arrives in Descrição, capped,
 * because it is a seed to edit and not a row title to save verbatim.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class TransacaoFormSheetSeedTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(defaultName: String?) {
        compose.setContent {
            PocketTheme {
                TransacaoFormSheet(
                    mode = FormMode.Add(defaultName = defaultName),
                    initialItem = null,
                    initialType = null,
                    cards = emptyList(),
                    tags = emptyList(),
                    contexts = emptyList(),
                    enabledMethods = PaymentMethodPreferences.default,
                    onSave = {},
                    onDismiss = {},
                    defaultName = defaultName,
                )
            }
        }
    }

    @Test
    fun `the sentence lands in Descricao`() {
        render("paguei 250 numa consulta do cachorro")

        compose.onNodeWithText("paguei 250 numa consulta do cachorro").assertExists()
    }

    @Test
    fun `a sentence longer than the cap is truncated`() {
        val long = "a".repeat(200)
        render(long)

        compose.onNodeWithText("a".repeat(120)).assertExists()
    }

    @Test
    fun `no sentence leaves the placeholder showing`() {
        render(null)

        compose.onNodeWithText("Ex.: iFood, Aluguel…").assertExists()
    }
}
