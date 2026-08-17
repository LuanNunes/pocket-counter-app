package com.resolveprogramming.pocketcounter.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.resolveprogramming.pocketcounter.domain.model.HomeKpis
import com.resolveprogramming.pocketcounter.domain.model.TransactionTotals
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
 * The headline figure is the pending total, not the saldo — that swap is the point of the card, so
 * it is asserted here rather than left to a screenshot. Expected money strings go through the same
 * formatter the card uses: pt-BR puts a non-breaking space after "R$", which a hand-typed literal
 * would get wrong.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BalanceHeroTest {

    @get:Rule
    val compose = createComposeRule()

    private val brl: NumberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    private fun kpis(
        pendingTotal: String = "7912.92",
        pendingCount: Int = 4,
    ) = HomeKpis(
        totals = TransactionTotals(
            income = BigDecimal("22975.05"),
            expense = BigDecimal("47606.36"),
            balance = BigDecimal("-24631.31"),
        ),
        expenseCount = 47,
        incomeCount = 5,
        pendingTotal = BigDecimal(pendingTotal),
        pendingCount = pendingCount,
    )

    private fun setHero(kpis: HomeKpis, balance: String) {
        compose.setContent {
            PocketTheme {
                BalanceHero(monthLabel = "Agosto 2026", kpis = kpis, balance = BigDecimal(balance))
            }
        }
    }

    @Test
    fun `leads with the pending total under a PENDENTE header`() {
        setHero(kpis(), balance = "-24631.31")

        compose.onNodeWithText("PENDENTE · AGOSTO 2026").assertIsDisplayed()
        compose.onNodeWithText(brl.format(BigDecimal("7912.92"))).assertIsDisplayed()
    }

    @Test
    fun `keeps the saldo in the KPI stack instead of dropping it`() {
        setHero(kpis(), balance = "-24631.31")

        compose.onNodeWithText("Saldo do mês").assertIsDisplayed()
        compose.onNodeWithText(brl.format(BigDecimal("-24631.31"))).assertIsDisplayed()
    }

    @Test
    fun `counts the saldo row by every entry in the month`() {
        setHero(kpis(), balance = "-24631.31")

        // 47 expenses + 5 incomes — the whole month, not the pending slice the row used to count.
        compose.onNodeWithText("52 lançs.").assertIsDisplayed()
    }

    @Test
    fun `still renders the pending headline when nothing is owed`() {
        setHero(kpis(pendingTotal = "0", pendingCount = 0), balance = "1200.00")

        compose.onNodeWithText(brl.format(BigDecimal.ZERO)).assertIsDisplayed()
        compose.onNodeWithText("Saldo do mês").assertIsDisplayed()
    }
}
