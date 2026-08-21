package com.resolveprogramming.pocketcounter.ui.home.components

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * The fatura tile stays tappable while it loads — the destination has its own load state — so the
 * only thing that changes is what it announces.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeQuickTilesTest {

    @get:Rule
    val compose = createComposeRule()

    private val brl: NumberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    private fun render(openBillsLoading: Boolean) {
        compose.setContent {
            PocketTheme {
                HomeQuickTiles(
                    openBillsTotal = BigDecimal("1234.56"),
                    openBillsCount = 2,
                    openBillsLoading = openBillsLoading,
                    onResumo = {},
                    onFaturas = {},
                )
            }
        }
    }

    @Test
    fun `announces the fatura tile as loading without a total`() {
        render(openBillsLoading = true)

        compose.onNodeWithContentDescription("Faturas, carregando").assertHasClickAction()
    }

    @Test
    fun `announces the total and the card count once loaded`() {
        render(openBillsLoading = false)

        compose
            .onNodeWithContentDescription("Faturas, ${brl.format(BigDecimal("1234.56"))}, 2 cartões")
            .assertHasClickAction()
    }

    @Test
    fun `names the resumo tile`() {
        render(openBillsLoading = false)

        compose.onNodeWithContentDescription("Resumo do mês, Para onde foi").assertHasClickAction()
    }

    @Test
    fun `the tiles announce themselves as buttons`() {
        render(openBillsLoading = false)

        compose
            .onNodeWithContentDescription("Resumo do mês, Para onde foi")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }
}
