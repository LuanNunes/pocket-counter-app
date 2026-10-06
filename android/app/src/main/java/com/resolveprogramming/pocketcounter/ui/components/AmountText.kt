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
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    autoSize: Boolean = false,
) {
    val resolvedColor = color ?: when (type) {
        TransactionType.INCOME -> PocketTheme.colors.income
        TransactionType.EXPENSE -> PocketTheme.colors.expense
        null -> PocketTheme.colors.text
    }

    val formatter = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    val formatted = formatter.format(amount.abs())
    val prefix = run {
        if (showSign && amount < BigDecimal.ZERO) return@run "−  "
        if (showSign && amount > BigDecimal.ZERO) return@run "+  "
        ""
    }
    val text = "$prefix$formatted"

    // Shrink-to-fit headline so large amounts never clip; delegates to the shared primitive.
    if (autoSize) {
        AutoSizeText(text = text, style = style, color = resolvedColor, modifier = modifier)
        return
    }

    if (autoSize) {
        AutoSizeText(
            text = "$prefix$formatted",
            style = style,
            color = resolvedColor,
            modifier = modifier,
        )
        return
    }

    Text(
        text = text,
        style = style,
        color = resolvedColor,
        maxLines = maxLines,
        softWrap = softWrap,
        modifier = modifier,
    )
}
