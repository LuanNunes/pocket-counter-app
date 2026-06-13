package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AmountText(
    amount: BigDecimal,
    modifier: Modifier = Modifier,
    type: TransactionType? = null,
    showSign: Boolean = false,
    style: TextStyle = PocketTheme.typography.monoBody,
    color: Color? = null,
) {
    val resolvedColor = color ?: when (type) {
        TransactionType.INCOME -> PocketTheme.colors.income
        TransactionType.EXPENSE -> PocketTheme.colors.expense
        null -> PocketTheme.colors.text
    }

    val formatter = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    val formatted = formatter.format(amount.abs())
    val prefix = when {
        showSign && amount < BigDecimal.ZERO -> "−  "
        showSign && amount > BigDecimal.ZERO -> "+  "
        else -> ""
    }

    Text(
        text = "$prefix$formatted",
        style = style,
        color = resolvedColor,
        modifier = modifier,
    )
}
